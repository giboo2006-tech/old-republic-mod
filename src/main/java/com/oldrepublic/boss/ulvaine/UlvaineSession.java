package com.oldrepublic.boss.ulvaine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.boss.CollectorHooks;
import com.oldrepublic.boss.raid.BossMusicPayload;
import com.oldrepublic.combat.Allegiances;
import com.oldrepublic.enemy.hymn.HymnDamage;
import com.oldrepublic.enemy.hymn.Hymnsworn;
import com.oldrepublic.enemy.hymn.Hymnsworns;
import com.oldrepublic.enemy.hymn.StormCall;
import com.oldrepublic.force.ForcePush;
import com.oldrepublic.force.barrier.ForceBarrier;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.planet.Clocks;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.BossOutcome;
import com.oldrepublic.registry.ModSounds;
import com.oldrepublic.saber.SaberCombat;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * One running Darth Ulvaine fight in the Heart of the Hymnal, or her daily echo (spec MILESTONE_5.md
 * 12.2): the owner, the listed participants (the M3 rules: the owner and up to three helpers who are
 * in the Heart at the start or come in during the first phase; a dead participant stays listed, one
 * away alive, in another dimension or offline for {@code participantLeaveTicks} leaves), the health
 * scaling with the M4 cap, the PURPLE boss bar and the music, the three phases (the duel; the storm's
 * rods and pylon chanters; the hymn's pulses and the Crescendo's resonators), the yield, the reset,
 * the resolution's scene and the echo's end. Nothing here is saved: after a restart the owner's next
 * visit starts a fresh fight, or a kneeling Ulvaine when the yield is still open.
 *
 * <p>The story moments (a phase, the yield, each participant's defeat) go to the session's
 * {@link CollectorHooks.Sink}: {@link CollectorHooks#QUEST} in play, a recorder in the game tests, so a
 * test's fight never touches a player's story and never swaps anything global.
 */
public final class UlvaineSession {
	/** The ticks the spared and bound scenes take before she is gone. */
	private static final int SCENE_TICKS = 60;
	/** The echo gathers out of the Hymn for this long before she fights. */
	private static final int FORM_TICKS = 40;
	/** The chanters look for a participant to fight this often. */
	private static final int RETARGET_TICKS = 20;
	/** The boss bar's list is checked against the participants once a second (the combat digest). */
	private static final int BAR_SYNC_TICKS = 20;
	/** A Push cast this close to facing her counts as cast into a pulse. */
	private static final double PUSH_FACING = 0.2;

	private final UUID owner;
	private final ServerLevel level;
	private final HeartLayout layout;
	private final boolean echo;
	/** Where the phase, yield and defeat hooks go. */
	private final CollectorHooks.Sink hooks;
	/** Participant id to the ticks it has been away. */
	private final Map<UUID, Integer> participants = new LinkedHashMap<>();
	private final List<Hymnsworn> chanters = new ArrayList<>();
	/** The standing resonators by spot (null where one is broken or none is lit). */
	private final HymnResonator[] resonators = new HymnResonator[4];
	private @Nullable DarthUlvaine ulvaine;
	private @Nullable ServerBossEvent bar;
	private int phase = UlvaineRules.PHASE_DUEL;
	private int transition;
	private int forming;
	private int emptyTicks;
	private boolean yielded;
	private boolean yieldDelivered;
	private int sceneTicks = -1;
	private boolean over;
	private int age;
	// The storm (phase 2).
	private int rodTimer;
	private int rodIndex;
	private int rodTell;
	private @Nullable Vec3 rodPatch;
	private @Nullable Vec3 rodFrom;
	// The hymn (phase 3).
	private int pulseTimer;
	private int pulseAge = -1;
	private @Nullable Vec3 pulseOrigin;
	private final Set<UUID> pulseResolved = new HashSet<>();
	private final Set<UUID> pulseImmune = new HashSet<>();
	private final Map<UUID, Integer> pushCooldowns = new HashMap<>();
	private boolean crescendoBegun;
	private boolean crescendoOver;
	private int crescendoTimer;
	private int blasts;

	UlvaineSession(UUID owner, ServerLevel level, HeartLayout layout, boolean echo, CollectorHooks.Sink hooks) {
		this.owner = owner;
		this.level = level;
		this.layout = layout;
		this.echo = echo;
		this.hooks = hooks;
	}

	// ---------------------------------------------------------------- reading

	public UUID owner() {
		return owner;
	}

	public ServerLevel level() {
		return level;
	}

	public HeartLayout layout() {
		return layout;
	}

	public boolean isEcho() {
		return echo;
	}

	public int phase() {
		return phase;
	}

	public boolean isTransitioning() {
		return transition > 0;
	}

	/** The echo is still gathering out of the Hymn. */
	public boolean isForming() {
		return forming > 0;
	}

	public boolean isYielded() {
		return yielded;
	}

	public boolean isOver() {
		return over;
	}

	/** The fight runs: not yielded, not in a scene, not over. */
	public boolean isFighting() {
		return !over && !yielded && sceneTicks < 0;
	}

	public @Nullable DarthUlvaine ulvaine() {
		return ulvaine;
	}

	public boolean isParticipant(UUID id) {
		return participants.containsKey(id);
	}

	/** The listed participants' ids, in joining order. */
	public List<UUID> participants() {
		return List.copyOf(participants.keySet());
	}

	/** Her scaled maximum health for the listed participants. */
	public double maxHealth() {
		return UlvaineRules.maxHealth(participants.size(), UlvaineBalance.get());
	}

	/** The pylon chanters still standing. */
	public List<Hymnsworn> chanters() {
		chanters.removeIf(c -> !c.isAlive());
		return List.copyOf(chanters);
	}

	/** The Crescendo's standing resonators. */
	public List<HymnResonator> resonators() {
		List<HymnResonator> out = new ArrayList<>();

		for (HymnResonator r : resonators) {
			if (r != null && r.isAlive()) {
				out.add(r);
			}
		}

		return out;
	}

	public boolean crescendoBegun() {
		return crescendoBegun;
	}

	/** Whether the Crescendo is over for good (all four broken, or never begun). */
	public boolean crescendoOver() {
		return crescendoOver;
	}

	/** How many Crescendo blasts have gone off. */
	public int blasts() {
		return blasts;
	}

	/** Whether a Hymn pulse is rolling out now. */
	public boolean pulseActive() {
		return pulseAge >= 0;
	}

	/** Whether a rod's strike is being called now (its tell runs). */
	public boolean rodCharging() {
		return rodPatch != null;
	}

	/** Whether {@code resonator} is one of this fight's standing spires. */
	boolean ownsResonator(HymnResonator resonator) {
		for (HymnResonator r : resonators) {
			if (r == resonator) {
				return true;
			}
		}

		return false;
	}

	/** The participants inside the Heart now, alive and playing. */
	public List<ServerPlayer> inHeart() {
		List<ServerPlayer> out = new ArrayList<>();

		for (UUID id : participants.keySet()) {
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);

			if (player != null && fighting(player)) {
				out.add(player);
			}
		}

		return out;
	}

	/** Every player inside the Heart whom the Hymn can touch (not creative or spectating), listed or not. */
	private List<ServerPlayer> everyoneInHeart() {
		List<ServerPlayer> out = new ArrayList<>();

		for (ServerPlayer player : level.players()) {
			if (fighting(player) && !StormCall.ignored(player)) {
				out.add(player);
			}
		}

		return out;
	}

	private boolean fighting(ServerPlayer player) {
		return player.level() == level && player.isAlive() && !player.isSpectator() && layout.inside(player.position());
	}

	// ---------------------------------------------------------------- start

	/** Spawns her at the Heart's centre; {@code knelt} spawns her already yielded (a restart, or the choice still open). */
	boolean start(boolean knelt) {
		DarthUlvaine spawned = UlvaineEntities.DARTH_ULVAINE.create(level, EntitySpawnReason.EVENT);

		if (spawned == null) {
			return false;
		}

		BlockPos at = layout.heart();
		spawned.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 180f, 0f);
		spawned.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
		spawned.bind(this, echo);

		if (!level.addFreshEntity(spawned)) {
			return false;
		}

		ulvaine = spawned;
		addParticipants();
		spawned.rescale(maxHealth());
		spawned.setHealthUnlocked(spawned.getMaxHealth());

		if (knelt) {
			yielded = true;
			spawned.setYielded(true);
			deliverYield();
			return true;
		}

		bar = new ServerBossEvent(UUID.randomUUID(), barName(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);

		for (ServerPlayer player : inHeart()) {
			join(player);
		}

		if (echo) {
			forming = FORM_TICKS;
			spawned.setForming(true);
		}

		EffectEvents.around(level, UlvaineEffects.EVENT, spawned.position(), null, spawned.getId(), echo ? 4f : 1f, UlvaineEffects.PHASE);

		if (!echo && level.getDifficulty() == Difficulty.PEACEFUL) {
			// Peaceful (6.9): she talks, then kneels; the choice opens as after a fight.
			yieldNow();
		}

		return true;
	}

	private Component barName() {
		return Component.translatable(echo ? "boss.oldrepublic.ulvaine.echo" : "boss.oldrepublic.ulvaine.name");
	}

	// ---------------------------------------------------------------- ticking

	/** One server tick. */
	void tick() {
		if (over) {
			return;
		}

		if (sceneTicks >= 0) {
			tickScene();
			return;
		}

		DarthUlvaine u = ulvaine;

		if (u == null || u.isRemoved()) {
			reset();
			return;
		}

		if (yielded) {
			deliverYield();
			return;
		}

		age++;
		UlvaineBalance b = UlvaineBalance.get();
		addParticipants();
		tickParticipants(b);

		if (forming > 0 && --forming == 0) {
			u.setForming(false);
		}

		if (transition > 0 && --transition == 0 && (u.action() == DarthUlvaine.Action.STORM || u.action() == DarthUlvaine.Action.SING)) {
			u.setAction(DarthUlvaine.Action.NONE);
		}

		ServerPlayer target = target();
		u.setTarget(target);
		tickChanters(target);

		if (bar != null) {
			bar.setProgress(Math.clamp(u.getHealth() / Math.max(1f, u.getMaxHealth()), 0f, 1f));

			if (age % BAR_SYNC_TICKS == 0) {
				syncBar();
			}
		}

		if (!isTransitioning() && !isForming()) {
			if (phase == UlvaineRules.PHASE_STORM) {
				tickRods(u, b);
			}

			if (phase >= UlvaineRules.PHASE_HYMN) {
				tickPulse(u, b);
				tickCrescendo(u, b);
			}
		}

		emptyTicks = inHeart().isEmpty() ? emptyTicks + 1 : 0;

		if (UlvaineRules.resetDue(emptyTicks, b)) {
			reset();
		}
	}

	/** The participant she fights: the nearest one in the Heart. */
	public @Nullable ServerPlayer target() {
		ServerPlayer best = null;
		double bestDistance = Double.MAX_VALUE;
		Vec3 from = ulvaine != null ? ulvaine.position() : layout.centre();

		for (ServerPlayer player : inHeart()) {
			double distance = player.position().distanceToSqr(from);

			if (distance < bestDistance) {
				best = player;
				bestDistance = distance;
			}
		}

		return best;
	}

	/** In the first phase, anyone playing inside the Heart joins the list (the owner first; at most {@code scalingCap}). */
	private void addParticipants() {
		if (phase != UlvaineRules.PHASE_DUEL || yielded || over) {
			return;
		}

		int cap = Math.max(1, UlvaineBalance.get().scalingCap);
		ServerPlayer ownerPlayer = level.getServer().getPlayerList().getPlayer(owner);

		if (ownerPlayer != null && !participants.containsKey(owner) && fighting(ownerPlayer)) {
			add(ownerPlayer);
		}

		for (ServerPlayer player : level.players()) {
			// The owner's place is kept: helpers fill at most cap - 1 places.
			int room = cap - participants.size() - (participants.containsKey(owner) ? 0 : 1);

			if (room <= 0) {
				return;
			}

			if (!participants.containsKey(player.getUUID()) && fighting(player)) {
				add(player);
			}
		}
	}

	private void add(ServerPlayer player) {
		participants.put(player.getUUID(), 0);

		if (bar != null) {
			join(player);
		}

		if (ulvaine != null) {
			ulvaine.rescale(maxHealth());
		}
	}

	/**
	 * Away for {@code participantLeaveTicks}: off the list, the bar and the music. A dead participant
	 * waits on the death screen without counting; once back up, the clock runs until they return.
	 */
	private void tickParticipants(UlvaineBalance b) {
		List<UUID> gone = new ArrayList<>();

		for (Map.Entry<UUID, Integer> entry : participants.entrySet()) {
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
			boolean stays = player != null && player.level() == level && (!player.isAlive() || fighting(player));
			int away = stays ? 0 : entry.getValue() + 1;
			entry.setValue(away);

			if (UlvaineRules.leftFight(away, b)) {
				gone.add(entry.getKey());
			}
		}

		for (UUID id : gone) {
			participants.remove(id);
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);

			if (player != null) {
				leave(player);
			}
		}

		if (!gone.isEmpty() && ulvaine != null) {
			ulvaine.rescale(maxHealth());
		}
	}

	private void join(ServerPlayer player) {
		if (bar != null) {
			bar.addPlayer(player);
		}

		BossMusicPayload.send(player, BossMusicPayload.ULVAINE, 1);
	}

	private void leave(ServerPlayer player) {
		if (bar != null) {
			bar.removePlayer(player);
		}

		BossMusicPayload.send(player, BossMusicPayload.ULVAINE, BossMusicPayload.STOP);
	}

	/** Re-adds the online participants to the bar (a relog makes a new player object). */
	private void syncBar() {
		if (bar == null) {
			return;
		}

		for (UUID id : participants.keySet()) {
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);

			if (player != null && !bar.getPlayers().contains(player)) {
				join(player);
			}
		}
	}

	// ---------------------------------------------------------------- damage and phases

	/** Whether {@code u} takes this hit: only in the fight, outside a transition, from a listed participant. */
	boolean acceptsDamage(DarthUlvaine u, DamageSource source) {
		if (u != ulvaine || !isFighting() || isTransitioning() || isForming()) {
			return false;
		}

		return source.getEntity() instanceof ServerPlayer player && participants.containsKey(player.getUUID());
	}

	/** What each hit on her is multiplied by: the health-cap scale, and the pylons' cut while a chanter lives. */
	float damageMultiplier() {
		UlvaineBalance b = UlvaineBalance.get();
		double max = ulvaine != null ? ulvaine.scaledMaxHealth() : maxHealth();
		return UlvaineRules.damageScale(max) * UlvaineRules.pylonMultiplier(!chanters().isEmpty(), b);
	}

	/** The lowest her health may go now, for a maximum of {@code max}: the next phase's threshold, the yield, or 1 for the echo. */
	float healthFloor(float max) {
		if (!isFighting()) {
			return 0f;
		}

		UlvaineBalance b = UlvaineBalance.get();
		return switch (phase) {
			case UlvaineRules.PHASE_DUEL -> max * b.phase2At;
			case UlvaineRules.PHASE_STORM -> max * b.phase3At;
			default -> echo ? 1f : UlvaineRules.floor(max, b);
		};
	}

	/** Her health reached the current floor: the next phase, the yield, or the echo's end. */
	void onFloorReached(DarthUlvaine u) {
		if (u != ulvaine || !isFighting()) {
			return;
		}

		if (phase < UlvaineRules.PHASE_HYMN) {
			enterPhase(phase + 1);
		} else if (echo) {
			finishEcho();
		} else {
			yieldNow();
		}
	}

	/** She took damage: a phase she has passed, and the Crescendo. */
	void onHurt(DarthUlvaine u) {
		if (u != ulvaine || !isFighting()) {
			return;
		}

		UlvaineBalance b = UlvaineBalance.get();
		float fraction = u.getHealth() / Math.max(1f, u.getMaxHealth());
		int next = UlvaineRules.phaseOf(fraction, b);

		if (next > phase) {
			enterPhase(next);
		}

		if (UlvaineRules.crescendoDue(fraction, phase, crescendoBegun, b)) {
			beginCrescendo(u, b);
		}
	}

	private void enterPhase(int next) {
		DarthUlvaine u = ulvaine;

		if (u == null) {
			return;
		}

		UlvaineBalance b = UlvaineBalance.get();
		phase = next;
		transition = Math.max(1, b.transitionTicks);
		u.setPhase(next);
		u.setAction(next == UlvaineRules.PHASE_STORM ? DarthUlvaine.Action.STORM : DarthUlvaine.Action.SING);
		u.getNavigation().stop();
		rodTimer = b.rodInterval / 2;
		pulseTimer = b.pulseInterval / 2;
		EffectEvents.around(level, UlvaineEffects.EVENT, u.position(), null, u.getId(), next, UlvaineEffects.PHASE);
		play(u.position(), ModSounds.BOSS_ULVAINE_PHASE, 1.6f, 1f);

		if (next == UlvaineRules.PHASE_STORM) {
			spawnChanters();
		}

		if (!echo) {
			for (ServerPlayer player : inHeart()) {
				hooks.onBossPhase(player, UlvaineFight.BOSS_ID, next);
			}
		}
	}

	/** An operator's jump to a phase (tests, the playthrough's shortcut): her health just under its threshold. */
	void forcePhase(int target) {
		DarthUlvaine u = ulvaine;

		if (u == null || !isFighting() || target <= phase) {
			return;
		}

		UlvaineBalance b = UlvaineBalance.get();
		transition = 0;
		forming = 0;
		u.setForming(false);
		u.setHealthUnlocked(UlvaineRules.healthForPhase(target, u.getMaxHealth(), b));
		onHurt(u);
	}

	// ---------------------------------------------------------------- the storm (phase 2)

	/**
	 * Two chanters at the pylons (translucent shades for the echo), never saved; while either lives she
	 * takes {@code pylonDamagePct}.
	 */
	private void spawnChanters() {
		for (BlockPos pylon : layout.pylons()) {
			Hymnsworn chanter = Hymnsworns.summon(level, pylon, 0, 1, Hymnsworn.Variant.CHANTER, echo, owner);

			if (chanter != null) {
				chanters.add(chanter);
			}
		}
	}

	private void tickChanters(@Nullable ServerPlayer target) {
		chanters.removeIf(c -> !c.isAlive());

		if (age % RETARGET_TICKS != 0 || target == null) {
			return;
		}

		for (Hymnsworn chanter : chanters) {
			LivingEntity current = chanter.getTarget();

			if (current == null || !current.isAlive() || !(current instanceof ServerPlayer p) || !participants.containsKey(p.getUUID())) {
				chanter.setTarget(target);
			}
		}
	}

	/** Every {@code rodInterval} the next rod charges toward a participant's patch; {@code rodTell} later it strikes. */
	private void tickRods(DarthUlvaine u, UlvaineBalance b) {
		if (rodPatch != null && --rodTell <= 0) {
			strikeRod(u, b);
		}

		if (--rodTimer > 0 || rodPatch != null || layout.rods().isEmpty()) {
			return;
		}

		List<ServerPlayer> targets = inHeart();

		if (targets.isEmpty()) {
			rodTimer = 20;
			return;
		}

		rodTimer = Math.max(1, b.rodInterval);
		ServerPlayer target = targets.get(u.getRandom().nextInt(targets.size()));
		BlockPos rod = layout.rods().get(Math.floorMod(rodIndex++, layout.rods().size()));
		// The patch is fixed where the target stood: the tell shows where to leave.
		rodPatch = target.position();
		rodFrom = Vec3.atCenterOf(rod);
		rodTell = Math.max(1, b.rodTell);
		Vec3 dir = rodPatch.subtract(rodFrom);
		EffectEvents.around(level, UlvaineEffects.EVENT, rodPatch, dir.lengthSqr() < 1.0e-4 ? null : dir.normalize(), u.getId(), b.rodTell,
				UlvaineEffects.ROD_TELL);
		play(rodFrom, ModSounds.BOSS_ULVAINE_ROD_CHARGE, 1.4f, 1f);
	}

	private void strikeRod(DarthUlvaine u, UlvaineBalance b) {
		Vec3 at = rodPatch;
		Vec3 from = rodFrom;
		rodPatch = null;
		rodFrom = null;

		if (at == null) {
			return;
		}

		StormCall.strike(level, at, b.rodRadius, b.rodDamage, b.shockTicks, u, e -> !(e instanceof DarthUlvaine) && !Allegiances.friendly(u, e));
		Vec3 dir = from == null ? null : at.subtract(from);
		EffectEvents.around(level, UlvaineEffects.EVENT, at, dir == null || dir.lengthSqr() < 1.0e-4 ? null : dir.normalize(), u.getId(), b.rodRadius,
				UlvaineEffects.ROD_STRIKE);
		play(at, ModSounds.BOSS_ULVAINE_ROD_STRIKE, 1.5f, 0.9f + u.getRandom().nextFloat() * 0.2f);
	}

	// ---------------------------------------------------------------- the hymn (phase 3)

	/** Every {@code pulseInterval} a Hymn pulse rolls out from where she stands. */
	private void tickPulse(DarthUlvaine u, UlvaineBalance b) {
		if (pulseAge >= 0) {
			advancePulse(u, b);
			return;
		}

		if (--pulseTimer > 0) {
			return;
		}

		pulseTimer = Math.max(1, b.pulseInterval);
		pulseAge = 0;
		pulseOrigin = u.position();
		pulseResolved.clear();
		pulseImmune.clear();
		pushCooldowns.clear();

		for (ServerPlayer player : everyoneInHeart()) {
			pushCooldowns.put(player.getUUID(), Progression.get(player).cooldown(ForcePush.ABILITY_ID));
		}

		// dir carries the wave's shape: x its ticks, y its height.
		EffectEvents.around(level, UlvaineEffects.EVENT, pulseOrigin, new Vec3(b.pulseTicks, b.pulseHeight, 0), u.getId(), b.pulseRadius,
				UlvaineEffects.PULSE);
		play(pulseOrigin, ModSounds.BOSS_ULVAINE_PULSE, 1.6f, 1f);
	}

	/**
	 * The front moves out {@code pulseRadius} over {@code pulseTicks}; each player is met once. Jumping
	 * it (off the ground, or above its {@code pulseHeight}), casting Push into it during the pulse, or
	 * parrying it (a guard inside the parry window: the hit comes from her side) lets it pass;
	 * otherwise {@code pulseDamage} and Slowness I for {@code pulseSlowTicks}. The damage follows the
	 * whole front, not the look's gaps.
	 */
	private void advancePulse(DarthUlvaine u, UlvaineBalance b) {
		Vec3 origin = pulseOrigin;

		if (origin == null) {
			pulseAge = -1;
			return;
		}

		pulseAge++;
		double front = UlvaineRules.pulseFront(pulseAge, b);

		for (ServerPlayer player : everyoneInHeart()) {
			UUID id = player.getUUID();

			if (pulseResolved.contains(id)) {
				continue;
			}

			watchPush(player, origin);
			double dx = player.getX() - origin.x;
			double dz = player.getZ() - origin.z;
			double distance = Math.sqrt(dx * dx + dz * dz);

			if (distance > front || distance > b.pulseRadius) {
				continue;
			}

			pulseResolved.add(id);

			if (pulseImmune.contains(id) || !player.onGround() || player.getY() > origin.y + b.pulseHeight) {
				continue;
			}

			DamageSource source = new DamageSource(level.damageSources().source(HymnDamage.HYMN).typeHolder(), origin);

			if (SaberCombat.strike(player, level, source, b.pulseDamage, SaberCombat.Strike.NPC_LIGHT)) {
				player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, b.pulseSlowTicks, 0), u);
			}
		}

		if (pulseAge >= Math.max(1, b.pulseTicks)) {
			pulseAge = -1;
			pulseOrigin = null;
		}
	}

	/** A new Push cast (its cooldown went up) while facing her side of the wave: the caster ignores this pulse. */
	private void watchPush(ServerPlayer player, Vec3 origin) {
		int cooldown = Progression.get(player).cooldown(ForcePush.ABILITY_ID);
		Integer before = pushCooldowns.put(player.getUUID(), cooldown);

		if (before == null || cooldown <= before) {
			return;
		}

		Vec3 toward = origin.subtract(player.position()).multiply(1, 0, 1);
		Vec3 look = player.getLookAngle().multiply(1, 0, 1);

		if (toward.lengthSqr() < 1.0e-4 || look.lengthSqr() < 1.0e-4 || look.normalize().dot(toward.normalize()) >= PUSH_FACING) {
			pulseImmune.add(player.getUUID());
		}
	}

	/** At {@code crescendoAt} the four resonators light; once all four are broken the Crescendo is over for good. */
	private void beginCrescendo(DarthUlvaine u, UlvaineBalance b) {
		crescendoBegun = true;
		crescendoTimer = Math.max(1, b.crescendoTicks);
		EffectEvents.around(level, UlvaineEffects.EVENT, u.position(), null, u.getId(), b.crescendoTicks, UlvaineEffects.CRESCENDO);
		play(u.position(), ModSounds.BOSS_ULVAINE_CRESCENDO, 2f, 1f);
		lightResonators(b);
	}

	private void tickCrescendo(DarthUlvaine u, UlvaineBalance b) {
		if (!crescendoBegun || crescendoOver) {
			return;
		}

		if (resonators().isEmpty()) {
			crescendoOver = true;
			return;
		}

		if (--crescendoTimer > 0) {
			return;
		}

		blast(u, b);
		crescendoTimer = Math.max(1, b.crescendoTicks);
		lightResonators(b);
	}

	/** The whole hall takes {@code crescendoDamage}, except whoever a Barrier shelters. */
	private void blast(DarthUlvaine u, UlvaineBalance b) {
		blasts++;
		DamageSource source = level.damageSources().source(HymnDamage.HYMN);

		for (ServerPlayer player : everyoneInHeart()) {
			if (!sheltered(player)) {
				player.hurtServer(level, source, b.crescendoDamage);
			}
		}

		EffectEvents.around(level, UlvaineEffects.EVENT, layout.centre(), null, u.getId(), (float) layout.radius(), UlvaineEffects.BLAST);
		play(layout.centre(), ModSounds.BOSS_ULVAINE_BLAST, 2.5f, 1f);
	}

	/** Inside a channelled Barrier: the caster, or anyone within its radius. */
	static boolean sheltered(ServerPlayer player) {
		double radius = ForceBarrier.balance().radius;

		for (ServerPlayer other : player.level().players()) {
			if (ForceBarrier.isChannelling(other) && (other == player || other.distanceToSqr(player) <= radius * radius)) {
				return true;
			}
		}

		return false;
	}

	/** Lights all four spires: the broken ones stand again and the standing ones are made whole. */
	private void lightResonators(UlvaineBalance b) {
		List<BlockPos> spots = layout.resonators();

		for (int i = 0; i < resonators.length && i < spots.size(); i++) {
			HymnResonator standing = resonators[i];

			if (standing != null && standing.isAlive()) {
				standing.setHealth(standing.getMaxHealth());
				continue;
			}

			HymnResonator spire = UlvaineEntities.HYMN_RESONATOR.create(level, EntitySpawnReason.EVENT);

			if (spire == null) {
				continue;
			}

			BlockPos at = spots.get(i);
			spire.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
			spire.bind(this, i);
			spire.setPersistenceRequired();

			var health = spire.getAttribute(Attributes.MAX_HEALTH);

			if (health != null) {
				health.setBaseValue(b.resonatorHealth);
			}

			spire.setHealth(spire.getMaxHealth());

			if (level.addFreshEntity(spire)) {
				resonators[i] = spire;
				EffectEvents.around(level, UlvaineEffects.EVENT, spire.position().add(0, spire.getBbHeight() * 0.5, 0), null, spire.getId(), 1f,
						UlvaineEffects.RESONATOR);
				play(spire.position(), ModSounds.BOSS_ULVAINE_RESONATOR, 1.2f, 1.2f);
			}
		}
	}

	/** Only the participants break the spires. */
	boolean acceptsResonatorDamage(DamageSource source) {
		return isFighting() && source.getEntity() instanceof ServerPlayer player && participants.containsKey(player.getUUID());
	}

	/** A spire broke: its light goes out in a spray of shards. */
	void onResonatorBroken(HymnResonator resonator) {
		for (int i = 0; i < resonators.length; i++) {
			if (resonators[i] == resonator) {
				resonators[i] = null;
			}
		}

		EffectEvents.around(level, UlvaineEffects.EVENT, resonator.position().add(0, resonator.getBbHeight() * 0.5, 0), null, resonator.getId(), 0f,
				UlvaineEffects.RESONATOR);
		play(resonator.position(), ModSounds.BOSS_ULVAINE_RESONATOR, 1.4f, 0.7f);
	}

	// ---------------------------------------------------------------- the yield

	/** An operator's yield (the tests, {@code /oldrepublic ulvaine yield}): she kneels as at 10%. */
	void forceYield() {
		if (ulvaine != null && isFighting() && !echo) {
			yieldNow();
		}
	}

	private void yieldNow() {
		DarthUlvaine u = ulvaine;
		yielded = true;
		clearFight();

		if (u == null) {
			return;
		}

		u.setYielded(true);
		EffectEvents.around(level, UlvaineEffects.EVENT, u.position(), null, u.getId(), 0f, UlvaineEffects.YIELD);
		play(u.position(), ModSounds.BOSS_ULVAINE_YIELD, 1.2f, 1f);
		UlvaineFight.rememberYield(owner);
		deliverYield();
	}

	/** The owner's choice opens (W4's {@code c5_ulvaine_yield}) once the owner is alive in the Heart. */
	private void deliverYield() {
		if (yieldDelivered || echo) {
			return;
		}

		ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);

		if (player != null && fighting(player)) {
			yieldDelivered = true;
			hooks.onBossYielded(player, UlvaineFight.BOSS_ID);
		}
	}

	/** Whether the yield's choice was handed to the owner. */
	public boolean yieldDelivered() {
		return yieldDelivered;
	}

	/** The owner used her while she kneels: the choice opens again. */
	boolean onUsed(ServerPlayer player) {
		if (!yielded || echo || over || sceneTicks >= 0 || !player.getUUID().equals(owner)) {
			return false;
		}

		yieldDelivered = true;
		hooks.onBossYielded(player, UlvaineFight.BOSS_ID);
		return true;
	}

	/**
	 * The owner chose (W4's {@code ulvaine:resolve}): every participant's fight ends through the defeat
	 * hook (the owner's first clear), then the scene: spared (and the rest), she rises and walks out;
	 * bound, she is led away with her wrists bound; ended, a short end.
	 */
	void resolve(BossOutcome outcome) {
		if (!yielded) {
			yieldNow();
		}

		List<UUID> rewarded = new ArrayList<>(participants.keySet());

		if (!rewarded.contains(owner)) {
			rewarded.add(owner);
		}

		for (UUID id : rewarded) {
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);

			if (player != null) {
				hooks.onBossDefeated(player, UlvaineFight.BOSS_ID, outcome, owner, id.equals(owner));
			}
		}

		UlvaineFight.forgetYield(owner);
		DarthUlvaine u = ulvaine;

		if (u == null || u.isRemoved()) {
			over = true;
			return;
		}

		switch (outcome) {
			case EXECUTED -> {
				over = true;
				u.end(level);
			}
			case BOUND -> {
				u.setAction(DarthUlvaine.Action.BOUND);
				sceneTicks = SCENE_TICKS;
			}
			default -> {
				u.setYielded(false);
				u.setTarget(null);
				ServerPlayer ownerPlayer = level.getServer().getPlayerList().getPlayer(owner);
				Vec3 from = ownerPlayer != null ? ownerPlayer.position() : layout.centre();
				Vec3 away = u.position().subtract(from).multiply(1, 0, 1);
				Vec3 dir = away.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : away.normalize();
				Vec3 to = u.position().add(dir.scale(layout.radius()));
				u.getNavigation().moveTo(to.x, to.y, to.z, 0.7);
				sceneTicks = SCENE_TICKS;
			}
		}
	}

	private void tickScene() {
		DarthUlvaine u = ulvaine;

		if (--sceneTicks > 0 && u != null && !u.isRemoved()) {
			return;
		}

		if (u != null && !u.isRemoved()) {
			EffectEvents.around(level, UlvaineEffects.EVENT, u.position(), null, u.getId(), 1f, UlvaineEffects.ECHO_FADE);
			u.discard();
		}

		over = true;
	}

	// ---------------------------------------------------------------- the echo

	/**
	 * The echo falls (she fades at 0, with no yield): each participant's daily echo reward
	 * ({@link UlvaineFight#payEcho}), the defeat hook with {@code ECHO}, and she comes apart.
	 */
	private void finishEcho() {
		DarthUlvaine u = ulvaine;
		yielded = true;
		clearFight();
		long today = Clocks.day(level.getServer());

		for (UUID id : participants.keySet()) {
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);

			if (player != null) {
				UlvaineFight.payEcho(player, today);
				hooks.onBossDefeated(player, UlvaineFight.BOSS_ID, BossOutcome.ECHO, owner, false);
			}
		}

		if (u != null && !u.isRemoved()) {
			EffectEvents.around(level, UlvaineEffects.EVENT, u.position(), null, u.getId(), 0f, UlvaineEffects.ECHO_FADE);
			play(u.position(), ModSounds.BOSS_ULVAINE_ECHO_FADE, 1.4f, 1f);
			u.discard();
		}

		over = true;
	}

	// ---------------------------------------------------------------- the end of a fight

	/** She died some other way (an operator's {@code /kill}): the fight ends as a reset. */
	void onUlvaineLost(DarthUlvaine u) {
		if (u == ulvaine && !over) {
			reset();
		}
	}

	/** The fight stops: rods, pulses, spires and chanters go, the bar and the music stop. */
	private void clearFight() {
		rodPatch = null;
		rodFrom = null;
		pulseAge = -1;
		pulseOrigin = null;
		crescendoOver = true;

		for (int i = 0; i < resonators.length; i++) {
			if (resonators[i] != null) {
				resonators[i].discard();
				resonators[i] = null;
			}
		}

		for (Hymnsworn chanter : chanters) {
			if (chanter.isAlive()) {
				chanter.discard();
			}
		}

		chanters.clear();

		for (UUID id : participants.keySet()) {
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);

			if (player != null) {
				BossMusicPayload.send(player, BossMusicPayload.ULVAINE, BossMusicPayload.STOP);
			}
		}

		if (bar != null) {
			bar.removeAllPlayers();
			bar = null;
		}
	}

	/**
	 * The reset (no participant alive in the Heart for {@code resetTicks}, or an operator): everything
	 * cleared and she is gone; the owner's next visit starts the fight again at full health.
	 */
	void reset() {
		if (over) {
			return;
		}

		clearFight();
		over = true;

		if (ulvaine != null && !ulvaine.isRemoved()) {
			ulvaine.discard();
		}

		ulvaine = null;
	}

	private void play(Vec3 at, SoundEvent sound, float volume, float pitch) {
		Entity source = ulvaine;
		level.playSound(null, at.x, at.y, at.z, sound, source != null ? source.getSoundSource() : SoundSource.HOSTILE, volume, pitch);
	}
}
