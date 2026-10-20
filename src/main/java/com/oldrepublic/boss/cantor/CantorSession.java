package com.oldrepublic.boss.cantor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.boss.raid.BossMusicPayload;
import com.oldrepublic.boss.raid.RaidBalance;
import com.oldrepublic.boss.raid.RaidRewards;
import com.oldrepublic.boss.raid.RaidRules;
import com.oldrepublic.boss.raid.RaidSession;
import com.oldrepublic.boss.raid.UndercroftLayout;
import com.oldrepublic.combat.Allegiances;
import com.oldrepublic.enemy.hymn.HymnDamage;
import com.oldrepublic.enemy.hymn.Hymnsworn;
import com.oldrepublic.enemy.hymn.Hymnsworns;
import com.oldrepublic.enemy.hymn.Knock;
import com.oldrepublic.enemy.hymn.StormCall;
import com.oldrepublic.force.ForceDamage;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.registry.ModSounds;
import com.oldrepublic.saber.SaberCombat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * One running Long Hymn (spec MILESTONE_5.md 12.3, 12.4): the keystone's countdown, then the shared
 * {@link RaidSession} (participants, bar, music, the wipe), the First Cantor rising at {@code CANTOR}
 * and his three phases. The Choir: four echoes shield him while any sings, the verse beam sweeps the
 * hall ({@link CantorBeam}), and the choir returns if he is not below 70% within
 * {@code choirReturnTicks} of the last echo falling. The Verses: the slam, the chain lightning, a
 * verse every {@code verseInterval} ({@link CantorVerses}) and the Hymnsworn shades. The Long Hymn:
 * the floor rings in turn and the silencing points with their double-damage windows
 * ({@link CantorEngine}), the slam and the chain going on. At his last floor he unravels into song,
 * every listed participant is paid ({@link RaidRewards}) and the engine falls silent; no participant
 * alive inside for {@code wipeResetTicks} stops the Hymn and resets it. Nothing here is saved.
 */
public final class CantorSession {
	/** He hovers this high over his spot. */
	static final double HOVER = 0.75;
	/** He rises from this deep in the engine. */
	private static final double RISE_DEPTH = 2.6;
	/** A refused hit's shield motes come at most this often. */
	private static final int SHIELD_HIT_TICKS = 10;
	/** While the choir sings, its motes drift to him this often. */
	private static final int SHIELD_SONG_TICKS = 80;
	/** The shades look for a participant to fight this often. */
	private static final int RETARGET_TICKS = 20;
	/** A brief pose (the chain, a call) lasts this long. */
	private static final int POSE_TICKS = 12;
	/** The undercroft's half-width used when an operator's fixture is drawn around a player (blocks). */
	public static final int FIXTURE_HALF = 14;
	/** W6's Stasis status effect, by id: a held Cantor makes no new move. */
	private static final Identifier STASIS = OldRepublicMod.id("stasis");

	private final ServerLevel level;
	private final UndercroftLayout layout;
	private final @Nullable UUID starter;
	private final CantorBeam beam = new CantorBeam(this);
	private final CantorVerses verses = new CantorVerses(this);
	private final CantorEngine engine = new CantorEngine(this);
	private final CantorChoir[] choir = new CantorChoir[4];
	private final List<Hymnsworn> shades = new ArrayList<>();
	private @Nullable RaidSession raid;
	private @Nullable FirstCantor cantor;
	private int countdown;
	private int phase;
	private int transition;
	private int rise;
	private int unravel = -1;
	private boolean over;
	private boolean cleared;
	private int age;
	private @Nullable Vec3 riseFrom;
	/** The raid's age when the last echo fell, or -1 while any stands. */
	private int choirDownAt = -1;
	private int slamTimer;
	private int slamTell = -1;
	private int chainTimer;
	private int shadeTimer;
	private int poseTicks;
	private int lastShieldHit = -SHIELD_HIT_TICKS;
	private List<UUID> rewarded = List.of();

	CantorSession(ServerLevel level, UndercroftLayout layout, @Nullable UUID starter) {
		this.level = level;
		this.layout = layout;
		this.starter = starter;
	}

	// ---------------------------------------------------------------- reading

	public ServerLevel level() {
		return level;
	}

	public UndercroftLayout layout() {
		return layout;
	}

	/** Who turned the keystone (null for an operator or a test). */
	public @Nullable UUID starter() {
		return starter;
	}

	/** The shared raid session once the Hymn has begun (null during the countdown). */
	public @Nullable RaidSession raid() {
		return raid;
	}

	public @Nullable FirstCantor cantor() {
		return cantor;
	}

	/** 0 during the countdown and the rise, then 1 to 3. */
	public int phase() {
		return phase;
	}

	public boolean isCountingDown() {
		return raid == null && !over;
	}

	/** Ticks left on the keystone's countdown. */
	public int countdown() {
		return countdown;
	}

	public boolean isRising() {
		return rise > 0;
	}

	public boolean isTransitioning() {
		return transition > 0;
	}

	public boolean isUnravelling() {
		return unravel >= 0;
	}

	/** He fights: the Hymn has begun, he has risen, and he has not unravelled; the session is not over. */
	public boolean isFighting() {
		return !over && raid != null && cantor != null && rise <= 0 && unravel < 0;
	}

	public boolean isOver() {
		return over;
	}

	/** Whether he unravelled (the clear) rather than the Hymn stopping. */
	public boolean isCleared() {
		return cleared;
	}

	/** The participants paid at the clear. */
	public List<UUID> rewarded() {
		return rewarded;
	}

	/** Ticks since the keystone turned. */
	public int age() {
		return age;
	}

	public CantorBeam beam() {
		return beam;
	}

	public CantorVerses verses() {
		return verses;
	}

	public CantorEngine engine() {
		return engine;
	}

	/** The standing choir echoes. */
	public List<CantorChoir> choir() {
		List<CantorChoir> out = new ArrayList<>();

		for (CantorChoir echo : choir) {
			if (echo != null && echo.isAlive() && !echo.isRemoved()) {
				out.add(echo);
			}
		}

		return out;
	}

	/** The Hymnsworn shades still standing. */
	public List<Hymnsworn> shades() {
		shades.removeIf(s -> !s.isAlive() || s.isRemoved());
		return List.copyOf(shades);
	}

	/** Whether the choir shields him now: the first phase, with any echo singing. */
	public boolean shielded() {
		if (phase != RaidRules.PHASE_CHOIR) {
			return false;
		}

		for (CantorChoir echo : choir) {
			if (echo != null && echo.sings()) {
				return true;
			}
		}

		return false;
	}

	/** Whether a silenced point's window is open (he takes double damage). */
	public boolean windowOpen() {
		return engine.windowOpen();
	}

	/** His scaled maximum health for the listed participants. */
	public double maxHealth() {
		return RaidRules.maxHealth(count(), RaidBalance.get());
	}

	/** The listed participants' count (1 before the Hymn begins). */
	public int count() {
		RaidSession session = raid;
		return session == null ? 1 : Math.max(1, session.count());
	}

	/** Whether {@code echo} is one of this raid's standing echoes. */
	boolean ownsChoir(CantorChoir echo) {
		for (CantorChoir c : choir) {
			if (c == echo) {
				return true;
			}
		}

		return false;
	}

	/** Whether {@code player} is a listed participant. */
	public boolean isParticipant(ServerPlayer player) {
		return raid != null && raid.isParticipant(player.getUUID());
	}

	/** Every player playing inside the undercroft (listed or not; creative and spectators aside): the Hymn's hazards touch them all. */
	public List<ServerPlayer> everyoneInside() {
		if (raid != null) {
			return raid.everyoneInside();
		}

		List<ServerPlayer> out = new ArrayList<>();

		for (ServerPlayer player : level.players()) {
			if (player.isAlive() && !StormCall.ignored(player) && layout.inside(player.position())) {
				out.add(player);
			}
		}

		return out;
	}

	/** The listed participants inside now, alive and playing. */
	public List<ServerPlayer> participantsInside() {
		return raid == null ? List.of() : raid.inside();
	}

	/**
	 * Everyone playing inside the undercroft now, alive and not spectating, creative players too (unlike
	 * {@link #everyoneInside}, the hazards' list): whom the Hymn lists when it begins ({@link RaidSession#admit}), so the
	 * countdown speaks to them and runs on while any of them stays.
	 */
	private List<ServerPlayer> playingInside() {
		List<ServerPlayer> out = new ArrayList<>();

		for (ServerPlayer player : level.players()) {
			if (player.isAlive() && !player.isSpectator() && layout.inside(player.position())) {
				out.add(player);
			}
		}

		return out;
	}

	// ---------------------------------------------------------------- the keystone and the rise

	/** The keystone turns: the countdown begins ({@code countdownTicks}; 0 starts the Hymn on the next tick). */
	void begin(int countdownTicks) {
		countdown = Math.max(0, countdownTicks);
		event(floorCentre(), new Vec3(countdown, 0, 0), -1, 0f, CantorEffects.PHASE);
		play(floorCentre(), ModSounds.BOSS_CANTOR_PHASE, 1.6f, 0.6f);

		for (ServerPlayer player : playingInside()) {
			player.sendSystemMessage(Component.translatable("boss.oldrepublic.cantor.keystone"));

			if (RaidSession.clearedThisWeek(player)) {
				// Already rewarded this raid week: they may help, for experience only.
				player.sendSystemMessage(Component.translatable("boss.oldrepublic.cantor.helping"));
			}
		}
	}

	private void tickCountdown() {
		List<ServerPlayer> inside = playingInside();

		if (inside.isEmpty()) {
			// Everyone left before the Hymn began: the engine settles again.
			stop(false);
			return;
		}

		if (countdown > 0 && countdown % 20 == 0) {
			Component left = Component.translatable("boss.oldrepublic.cantor.countdown", countdown / 20);

			for (ServerPlayer player : inside) {
				player.sendOverlayMessage(left);
			}
		}

		if (--countdown > 0) {
			return;
		}

		beginHymn();
	}

	/** The Hymn begins: everyone inside is listed, the First Cantor rises at {@code CANTOR} and the choir sings. */
	private void beginHymn() {
		RaidBalance b = RaidBalance.get();
		RaidSession session = new RaidSession(level, layout, Component.translatable("boss.oldrepublic.cantor.name"), BossEvent.BossBarColor.RED,
				BossEvent.BossBarOverlay.NOTCHED_12, BossMusicPayload.CANTOR);
		raid = session;
		session.admit();

		if (session.count() == 0) {
			stop(false);
			return;
		}

		FirstCantor spawned = CantorEntities.FIRST_CANTOR.create(level, EntitySpawnReason.EVENT);

		if (spawned == null) {
			stop(false);
			return;
		}

		Vec3 hover = hoverPoint();
		Vec3 from = hover.subtract(0, RISE_DEPTH, 0);
		riseFrom = from;
		spawned.snapTo(from.x, from.y, from.z, 180f, 0f);
		spawned.finalizeSpawn(level, level.getCurrentDifficultyAt(layout.cantor()), EntitySpawnReason.EVENT, null);
		spawned.bind(this, from);

		if (!level.addFreshEntity(spawned)) {
			stop(false);
			return;
		}

		cantor = spawned;
		phase = RaidRules.PHASE_CHOIR;
		rise = Math.max(1, b.riseTicks);
		spawned.setPhase(0);
		spawned.setAction(FirstCantor.Action.RISE);
		spawned.rescale(maxHealth());
		spawned.setHealthUnlocked(spawned.getMaxHealth());
		spawnChoir(b);
		event(hover, new Vec3(rise, 0, 0), spawned.getId(), RaidRules.PHASE_CHOIR, CantorEffects.PHASE);
		play(hover, ModSounds.BOSS_CANTOR_PHASE, 2f, 0.8f);
		session.setProgress(1f);

		for (ServerPlayer player : session.online()) {
			player.sendSystemMessage(Component.translatable("boss.oldrepublic.cantor.begins"));
		}
	}

	/** He rises out of the engine over {@code riseTicks}; then the first phase's moves begin. */
	private void tickRise(FirstCantor c, RaidBalance b) {
		Vec3 from = riseFrom != null ? riseFrom : hoverPoint();
		int total = Math.max(1, b.riseTicks);
		rise--;
		double done = 1.0 - (double) rise / total;
		c.setHover(from.add(0, RISE_DEPTH * done, 0));

		if (rise > 0) {
			return;
		}

		c.setHover(hoverPoint());
		c.setPhase(phase);
		c.setAction(FirstCantor.Action.NONE);
		beam.start(b);
	}

	/** Where he hovers: over his spot's centre. */
	Vec3 hoverPoint() {
		return Vec3.atBottomCenterOf(layout.cantor()).add(0, HOVER, 0);
	}

	/** His spot on the floor. */
	Vec3 floorCentre() {
		return Vec3.atBottomCenterOf(layout.cantor());
	}

	/** The undercroft floor's height (his spot's). */
	double floorY() {
		return layout.cantor().getY();
	}

	// ---------------------------------------------------------------- ticking

	/** One server tick. */
	void tick() {
		if (over) {
			return;
		}

		age++;

		if (level.getDifficulty() == Difficulty.PEACEFUL) {
			// Peaceful (12.3): the undercroft stays shut, and a Hymn already running stops.
			stop(false);
			return;
		}

		if (unravel >= 0) {
			tickUnravel();
			return;
		}

		RaidSession session = raid;

		if (session == null) {
			tickCountdown();
			return;
		}

		FirstCantor c = cantor;

		if (c == null || c.isRemoved()) {
			stop(false);
			return;
		}

		RaidSession.Tick step = session.tick();

		if (step.joined()) {
			rescale();
		}

		if (step.wiped()) {
			stop(false);
			return;
		}

		RaidBalance b = RaidBalance.get();
		session.setProgress(c.getHealth() / Math.max(1f, c.getMaxHealth()));

		if (rise > 0) {
			tickRise(c, b);
			return;
		}

		if (transition > 0 && --transition == 0) {
			restPose(c);
		}

		if (poseTicks > 0 && --poseTicks == 0 && !isTransitioning() && slamTell < 0 && !beam.isActive()) {
			restPose(c);
		}

		boolean shield = shielded();
		c.setShielded(shield);
		c.setWindowOpen(engine.windowOpen());
		tickShades();

		if (shield && age % SHIELD_SONG_TICKS == 0) {
			shieldMotes(c);
		}

		if (isTransitioning()) {
			return;
		}

		// Held in Stasis he starts nothing new; what he set going (a tell, a sweep, a verse) plays out.
		boolean held = frozen(c);

		switch (phase) {
			case RaidRules.PHASE_CHOIR -> {
				tickChoirReturn(b);
				beam.tick(c, b, held);
			}
			case RaidRules.PHASE_VERSES -> {
				verses.tick(c, b, held);
				tickSlam(c, b, held);
				tickChain(c, b, held);
				tickShadeWaves(c, b);
			}
			default -> {
				engine.tick(c, b);
				tickSlam(c, b, held);
				tickChain(c, b, held);
			}
		}
	}

	/** Held in Stasis (a quarter of its length on him, 9.2): he makes no new move, though what he set going goes on. */
	private static boolean frozen(FirstCantor c) {
		Optional<Holder.Reference<MobEffect>> stasis = BuiltInRegistries.MOB_EFFECT.get(STASIS);
		return stasis.isPresent() && c.hasEffect(stasis.get());
	}

	/** A participant joined in the window: his maximum (and the choir's) rises, each keeping its fraction. */
	private void rescale() {
		FirstCantor c = cantor;

		if (c != null) {
			c.rescale(maxHealth());
		}

		double echo = RaidRules.choirHealth(count(), RaidBalance.get());

		for (CantorChoir member : choir()) {
			setMaxHealth(member, echo);
		}
	}

	private static void setMaxHealth(LivingEntity entity, double max) {
		AttributeInstance instance = entity.getAttribute(Attributes.MAX_HEALTH);

		if (instance == null || Math.abs(instance.getBaseValue() - max) < 1.0e-3) {
			return;
		}

		float fraction = entity.getMaxHealth() <= 0f ? 1f : entity.getHealth() / entity.getMaxHealth();
		instance.setBaseValue(max);
		entity.setHealth((float) max * fraction);
	}

	// ---------------------------------------------------------------- the choir (phase 1)

	/** The four echoes stand (again) at {@code CHOIR_1..4}, whole, at the scaled health. */
	private void spawnChoir(RaidBalance b) {
		double health = RaidRules.choirHealth(count(), b);
		List<BlockPos> spots = layout.choir();
		Vec3 centre = floorCentre();

		for (int i = 0; i < choir.length && i < spots.size(); i++) {
			CantorChoir standing = choir[i];

			if (standing != null && standing.isAlive() && !standing.isRemoved()) {
				standing.setHealth(standing.getMaxHealth());
				continue;
			}

			CantorChoir echo = CantorEntities.CANTOR_CHOIR.create(level, EntitySpawnReason.EVENT);

			if (echo == null) {
				continue;
			}

			Vec3 at = Vec3.atBottomCenterOf(spots.get(i));
			Vec3 toward = centre.subtract(at);
			float yaw = (float) (Math.toDegrees(Math.atan2(toward.z, toward.x)) - 90.0);
			echo.snapTo(at.x, at.y, at.z, yaw, 0f);
			echo.setYHeadRot(yaw);
			echo.bind(this, i, at);
			setMaxHealth(echo, health);
			echo.setHealth(echo.getMaxHealth());

			if (level.addFreshEntity(echo)) {
				choir[i] = echo;
			}
		}

		choirDownAt = -1;
		play(centre, ModSounds.BOSS_CANTOR_CHOIR, 1.4f, 1f);
	}

	/** An echo fell: the song thins; with the last, his shield is down and the choir's return clock runs. */
	void onChoirFallen(CantorChoir echo) {
		for (int i = 0; i < choir.length; i++) {
			if (choir[i] == echo) {
				choir[i] = null;
			}
		}

		play(echo.position(), ModSounds.BOSS_CANTOR_CHOIR, 1.2f, 0.6f);

		if (choir().isEmpty() && raid != null && phase == RaidRules.PHASE_CHOIR) {
			choirDownAt = raid.age();
		}
	}

	/** If he is not below 70% within {@code choirReturnTicks} of the last echo falling, the choir returns (12.3). */
	private void tickChoirReturn(RaidBalance b) {
		RaidSession session = raid;

		if (session == null || !choir().isEmpty()) {
			return;
		}

		if (choirDownAt < 0) {
			choirDownAt = session.age();
			return;
		}

		if (session.age() - choirDownAt >= Math.max(1, b.choirReturnTicks)) {
			spawnChoir(b);
		}
	}

	/** A refused hit while the choir sings: pale motes drift from each singing echo to him (at most every half second). */
	private void onShieldedHit(FirstCantor c) {
		if (age - lastShieldHit < SHIELD_HIT_TICKS) {
			return;
		}

		lastShieldHit = age;
		shieldMotes(c);
	}

	private void shieldMotes(FirstCantor c) {
		Vec3 to = c.chest();

		for (CantorChoir echo : choir) {
			if (echo != null && echo.sings()) {
				Vec3 from = echo.position().add(0, echo.getBbHeight() * 0.7, 0);
				event(from, to.subtract(from), c.getId(), 1f, CantorEffects.SHIELD);
			}
		}
	}

	/** Only the participants' blows reach an echo, while he fights. */
	boolean acceptsChoirDamage(DamageSource source) {
		return isFighting() && !isTransitioning() && source.getEntity() instanceof ServerPlayer player && isParticipant(player);
	}

	// ---------------------------------------------------------------- damage and phases

	/** Whether {@code c} takes this hit: only while he fights, from a listed participant, with the choir silent. */
	boolean acceptsCantorDamage(FirstCantor c, DamageSource source) {
		if (c != cantor || !isFighting() || isTransitioning()) {
			return false;
		}

		if (!(source.getEntity() instanceof ServerPlayer player) || !isParticipant(player)) {
			return false;
		}

		if (shielded()) {
			onShieldedHit(c);
			return false;
		}

		return true;
	}

	/** What each hit on him is multiplied by: the health-cap scale, doubled while a silenced point's window is open. */
	float damageMultiplier() {
		RaidBalance b = RaidBalance.get();
		double max = cantor != null ? cantor.scaledMaxHealth() : maxHealth();
		return RaidRules.damageScale(max) * RaidRules.windowMultiplier(engine.windowOpen(), b);
	}

	/** The lowest his health may go now, for a maximum of {@code max}: the next phase's threshold, or 1 before he unravels. */
	float healthFloor(float max) {
		if (!isFighting()) {
			return unravel >= 0 ? 1f : 0f;
		}

		RaidBalance b = RaidBalance.get();
		return switch (phase) {
			case RaidRules.PHASE_CHOIR -> max * b.phase2At;
			case RaidRules.PHASE_VERSES -> max * b.phase3At;
			default -> 1f;
		};
	}

	/** His health reached the current floor: the next phase, or he unravels. */
	void onFloorReached(FirstCantor c) {
		if (c != cantor || !isFighting()) {
			return;
		}

		if (phase < RaidRules.PHASE_HYMN) {
			enterPhase(phase + 1);
		} else {
			unravel();
		}
	}

	/** He took damage: a phase he has passed begins. */
	void onCantorHurt(FirstCantor c) {
		if (c != cantor || !isFighting()) {
			return;
		}

		int next = RaidRules.phaseOf(c.getHealth() / Math.max(1f, c.getMaxHealth()), RaidBalance.get());

		if (next > phase) {
			enterPhase(next);
		}
	}

	private void enterPhase(int next) {
		FirstCantor c = cantor;

		if (c == null || next <= phase) {
			return;
		}

		RaidBalance b = RaidBalance.get();
		phase = next;
		transition = Math.max(1, b.transitionTicks);
		c.setPhase(next);
		c.setAction(next == RaidRules.PHASE_HYMN ? FirstCantor.Action.HYMN : FirstCantor.Action.VERSE);
		slamTell = -1;
		slamTimer = Math.max(1, b.slamInterval / 2);
		chainTimer = Math.max(1, b.chainInterval / 2);
		beam.stop();
		discardChoir();
		choirDownAt = -1;

		if (next == RaidRules.PHASE_VERSES) {
			verses.start(b);
			shadeTimer = Math.max(1, b.shadeFirstTicks);
		} else {
			verses.stop();
			engine.start(b);
		}

		event(c.position(), null, c.getId(), next, CantorEffects.PHASE);
		play(c.position(), ModSounds.BOSS_CANTOR_PHASE, 2f, next == RaidRules.PHASE_HYMN ? 0.8f : 1f);
	}

	/** An operator's jump to a phase (tests, the commands): his health at its threshold. */
	public boolean forcePhase(int target) {
		FirstCantor c = cantor;

		if (c == null || !isFighting() || target <= phase || target > RaidRules.PHASE_HYMN) {
			return false;
		}

		RaidBalance b = RaidBalance.get();
		transition = 0;
		c.setHealthUnlocked(c.getMaxHealth() * (target == RaidRules.PHASE_VERSES ? b.phase2At : b.phase3At));
		enterPhase(target);
		return true;
	}

	/** An operator's end of the transition (tests): his moves begin at once. */
	public void skipTransition() {
		transition = 0;

		if (cantor != null) {
			restPose(cantor);
		}
	}

	/** An operator's end of the rise (tests): he fights at once. */
	public void skipRise() {
		FirstCantor c = cantor;

		if (c != null && rise > 0) {
			rise = 1;
			tickRise(c, RaidBalance.get());
		}
	}

	// ---------------------------------------------------------------- the slam and the chain (phases 2 and 3)

	/** Every {@code slamInterval}, with someone close, he raises his fists ({@code slamTell}) and slams the floor around him. */
	private void tickSlam(FirstCantor c, RaidBalance b, boolean held) {
		if (slamTell >= 0) {
			if (--slamTell < 0) {
				slam(c, b);
			}

			return;
		}

		if (held || --slamTimer > 0) {
			return;
		}

		if (slamTargets(c, b.slamRadius + 1.5).isEmpty()) {
			slamTimer = RETARGET_TICKS;
			return;
		}

		slamTimer = Math.max(1, b.slamInterval);
		slamTell = Math.max(1, b.slamTell);
		c.setAction(FirstCantor.Action.SLAM);
		event(floorCentre(), null, c.getId(), b.slamRadius, CantorEffects.SLAM_TELL);
		play(floorCentre(), ModSounds.BOSS_CANTOR_SLAM_TELL, 1.5f, 1f);
	}

	private void slam(FirstCantor c, RaidBalance b) {
		Vec3 at = floorCentre();
		DamageSource source = level.damageSources().mobAttack(c);

		for (LivingEntity target : slamTargets(c, b.slamRadius)) {
			if (target.hurtServer(level, source, b.slamDamage)) {
				Knock.away(target, at, b.slamKnockback, 0.45);
			}
		}

		event(at, null, c.getId(), b.slamRadius, CantorEffects.SLAM);
		play(at, ModSounds.BOSS_CANTOR_SLAM, 2f, 1f);
		poseTicks = POSE_TICKS;
	}

	/** Everything his hazards touch within {@code radius} of his spot (across the floor, up to 3 blocks over it). */
	private List<LivingEntity> slamTargets(FirstCantor c, double radius) {
		Vec3 at = floorCentre();
		AABB box = new AABB(at.x - radius, at.y - 1, at.z - radius, at.x + radius, at.y + 3, at.z + radius);
		List<LivingEntity> out = new ArrayList<>();

		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box, e -> hits(c, e))) {
			double dx = target.getX() - at.x;
			double dz = target.getZ() - at.z;

			if (dx * dx + dz * dz <= radius * radius) {
				out.add(target);
			}
		}

		return out;
	}

	/**
	 * Every {@code chainInterval} lightning leaps from him to the nearest participant in {@code chainRange}
	 * and on from each struck to the next, {@code chainTargets} in all, {@code chainDamage} each (a lit
	 * saber's guard takes it, the M3 rule). Someone channelling a silencing point is struck only when no
	 * one else is in reach.
	 */
	private void tickChain(FirstCantor c, RaidBalance b, boolean held) {
		if (held || --chainTimer > 0) {
			return;
		}

		chainTimer = Math.max(1, b.chainInterval);
		Vec3 from = c.chest();
		Set<UUID> struck = new HashSet<>();
		List<ServerPlayer> candidates = everyoneInside();

		for (int link = 0; link < Math.max(0, b.chainTargets); link++) {
			ServerPlayer next = nextLink(candidates, struck, from, b.chainRange);

			if (next == null) {
				break;
			}

			struck.add(next.getUUID());
			Vec3 chest = next.position().add(0, next.getBbHeight() * 0.6, 0);
			DamageSource source = ForceDamage.source(level, ForceDamage.LIGHTNING, c);
			SaberCombat.strike(next, level, source, b.chainDamage, SaberCombat.Strike.NPC_LIGHT);
			event(chest, chest.subtract(from), c.getId(), link, CantorEffects.CHAIN);
			from = chest;
		}

		if (!struck.isEmpty()) {
			c.setAction(FirstCantor.Action.CHAIN);
			poseTicks = POSE_TICKS;
			play(c.chest(), ModSounds.BOSS_CANTOR_CHAIN, 1.6f, 0.9f + c.getRandom().nextFloat() * 0.2f);
		}
	}

	private @Nullable ServerPlayer nextLink(List<ServerPlayer> candidates, Set<UUID> struck, Vec3 from, double range) {
		double r = range * range;
		Comparator<ServerPlayer> nearest = Comparator.comparingDouble(p -> p.position().distanceToSqr(from));
		ServerPlayer free = null;
		ServerPlayer any = null;

		for (ServerPlayer player : candidates.stream().sorted(nearest).toList()) {
			if (struck.contains(player.getUUID()) || !player.isAlive() || player.position().distanceToSqr(from) > r) {
				continue;
			}

			if (any == null) {
				any = player;
			}

			if (free == null && !engine.isChannelling(player.getUUID())) {
				free = player;
			}
		}

		return free != null ? free : any;
	}

	// ---------------------------------------------------------------- the shades (phase 2)

	/** Every {@code shadeInterval} (the first wave {@code shadeFirstTicks} in) Hymnsworn shades join from the choir's spots. */
	private void tickShadeWaves(FirstCantor c, RaidBalance b) {
		if (--shadeTimer > 0) {
			return;
		}

		shadeTimer = Math.max(1, b.shadeInterval);
		int count = RaidRules.shades(count(), b);

		if (count <= 0 || shades().size() >= count * 2) {
			return;
		}

		spawnShades(count, c);
	}

	/** {@code count} shades (Hymnsworn blades, drawn translucent, never saved) at the choir's spots, each set on a participant. */
	public List<Hymnsworn> spawnShades(int count, FirstCantor c) {
		List<BlockPos> spots = layout.choir();
		List<ServerPlayer> targets = participantsInside();
		List<Hymnsworn> spawned = new ArrayList<>();
		int perSpot = Math.max(1, (count + spots.size() - 1) / Math.max(1, spots.size()));

		for (int i = 0; i < count && !spots.isEmpty(); i++) {
			BlockPos spot = spots.get(i % spots.size());
			Hymnsworn shade = Hymnsworns.summon(level, spot, i / spots.size(), perSpot, Hymnsworn.Variant.BLADE, true, null);

			if (shade == null) {
				continue;
			}

			if (!targets.isEmpty()) {
				shade.setTarget(targets.get(i % targets.size()));
			}

			shades.add(shade);
			spawned.add(shade);
			play(shade.position(), ModSounds.HYMNSWORN_CHANT, 1f, 0.8f);
		}

		if (!spawned.isEmpty()) {
			c.setAction(FirstCantor.Action.VERSE);
			poseTicks = POSE_TICKS * 2;
		}

		return spawned;
	}

	/** The shades keep to the listed participants inside. */
	private void tickShades() {
		if (age % RETARGET_TICKS != 0) {
			return;
		}

		List<ServerPlayer> inside = participantsInside();

		for (Hymnsworn shade : shades()) {
			LivingEntity current = shade.getTarget();

			if (inside.isEmpty()) {
				break;
			}

			if (current == null || !current.isAlive() || !(current instanceof ServerPlayer p) || !inside.contains(p)) {
				shade.setTarget(inside.stream().min(Comparator.comparingDouble(p -> p.distanceToSqr(shade))).orElse(null));
			}
		}
	}

	// ---------------------------------------------------------------- the end

	/**
	 * He unravels into song (the clear): his moves stop, the shades and echoes fade, every participant
	 * listed now is paid (once a raid week; {@link RaidRewards}), and the bar and the music go. He comes
	 * apart over {@code unravelTicks}.
	 */
	private void unravel() {
		FirstCantor c = cantor;
		RaidSession session = raid;

		if (c == null || session == null || unravel >= 0) {
			return;
		}

		RaidBalance b = RaidBalance.get();
		unravel = Math.max(1, b.unravelTicks);
		cleared = true;
		c.setAction(FirstCantor.Action.UNRAVEL);
		c.setShielded(false);
		c.setWindowOpen(false);
		event(c.chest(), new Vec3(unravel, 0, 0), c.getId(), 1f, CantorEffects.UNRAVEL);
		play(c.position(), ModSounds.BOSS_CANTOR_UNRAVEL, 2.5f, 1f);
		clearMoves();
		long week = RaidSession.week(level.getServer());
		List<UUID> paid = new ArrayList<>();

		for (UUID id : session.participants()) {
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);

			if (player == null) {
				OldRepublicMod.LOGGER.info("Long Hymn cleared with participant {} offline: no reward", id);
				continue;
			}

			player.sendSystemMessage(Component.translatable("boss.oldrepublic.cantor.cleared"));
			RaidRewards.pay(player, week);
			paid.add(id);
		}

		rewarded = List.copyOf(paid);
		session.end();
	}

	/** An operator's clear (tests, the commands): he unravels now, as at his last floor. */
	public boolean forceClear() {
		FirstCantor c = cantor;

		if (c == null || !isFighting()) {
			return false;
		}

		phase = RaidRules.PHASE_HYMN;
		transition = 0;
		c.setHealthUnlocked(1f);
		unravel();
		return true;
	}

	private void tickUnravel() {
		if (--unravel > 0) {
			return;
		}

		FirstCantor c = cantor;

		if (c != null && !c.isRemoved()) {
			c.discard();
		}

		over = true;
	}

	/** He died some other way (an operator's {@code /kill}): the Hymn stops as a wipe. */
	void onCantorLost(FirstCantor c) {
		if (c == cantor && !over && unravel < 0) {
			stop(false);
		}
	}

	/**
	 * The Hymn stops without a clear (a wipe, everyone gone in the countdown, Peaceful, an operator):
	 * everything goes, the engine falls quiet and the next keystone starts afresh. Idempotent.
	 *
	 * @param quietly no message (the server stopping)
	 */
	public void stop(boolean quietly) {
		if (over) {
			return;
		}

		RaidSession session = raid;

		if (!quietly) {
			event(floorCentre(), null, cantor != null ? cantor.getId() : -1, 4f, CantorEffects.PHASE);

			if (session != null) {
				for (ServerPlayer player : session.online()) {
					player.sendSystemMessage(Component.translatable("boss.oldrepublic.cantor.wipe"));
				}
			}
		}

		clearMoves();

		if (session != null) {
			session.end();
		}

		FirstCantor c = cantor;

		if (c != null && !c.isRemoved()) {
			c.discard();
		}

		cantor = null;
		over = true;
	}

	/** Every move stops and every echo and shade goes. */
	private void clearMoves() {
		beam.stop();
		verses.stop();
		engine.stop();
		slamTell = -1;
		discardChoir();

		for (Hymnsworn shade : shades) {
			if (shade.isAlive()) {
				shade.discard();
			}
		}

		shades.clear();
	}

	private void discardChoir() {
		for (int i = 0; i < choir.length; i++) {
			if (choir[i] != null) {
				choir[i].discard();
				choir[i] = null;
			}
		}
	}

	// ---------------------------------------------------------------- shared helpers

	/** Whether his hazards touch {@code e}: alive and playing, not his own, not the Hymn's. */
	boolean hits(FirstCantor c, LivingEntity e) {
		return e.isAlive() && !StormCall.ignored(e) && !(e instanceof FirstCantor) && !(e instanceof CantorChoir) && !Allegiances.friendly(c, e);
	}

	/** The Hymn's own damage (the beam, the fog, the rings), his. */
	DamageSource hymnDamage(FirstCantor c) {
		return level.damageSources().source(HymnDamage.HYMN, c);
	}

	RandomSource random() {
		return level.getRandom();
	}

	/** A random spot on the undercroft floor within {@code radius} of his spot, inside the box. */
	Vec3 randomFloorPoint(double radius) {
		Vec3 centre = floorCentre();
		AABB box = layout.box();

		for (int tries = 0; tries < 8; tries++) {
			double angle = random().nextDouble() * Math.PI * 2;
			double distance = Math.sqrt(random().nextDouble()) * radius;
			Vec3 at = new Vec3(centre.x + Math.cos(angle) * distance, centre.y, centre.z + Math.sin(angle) * distance);

			if (box.contains(at.add(0, 0.5, 0))) {
				return at;
			}
		}

		return centre;
	}

	void event(Vec3 pos, @Nullable Vec3 dir, int entityId, float intensity, int variant) {
		EffectEvents.around(level, CantorEffects.EVENT, pos, dir, entityId, intensity, variant);
	}

	void play(Vec3 at, SoundEvent sound, float volume, float pitch) {
		level.playSound(null, at.x, at.y, at.z, sound, SoundSource.HOSTILE, volume, pitch);
	}

	/** A pose that lasts {@code POSE_TICKS} twice over (a verse's call). */
	void pose(FirstCantor c, FirstCantor.Action action) {
		c.setAction(action);
		poseTicks = POSE_TICKS * 2;
	}

	/** Back to his resting pose: arms spread for the engine's hymn in the last phase, else still. */
	void restPose(FirstCantor c) {
		c.setAction(phase == RaidRules.PHASE_HYMN ? FirstCantor.Action.HYMN : FirstCantor.Action.NONE);
	}
}
