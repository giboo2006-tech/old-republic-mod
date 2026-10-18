package com.oldrepublic.boss.kaast;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.enemy.mandalorian.Hunters;
import com.oldrepublic.enemy.mandalorian.MandalorianHunter;
import com.oldrepublic.enemy.mandalorian.Netting;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.quest.BossOutcome;
import com.oldrepublic.quest.QuestHooks;
import com.oldrepublic.quest.story.KaastOutcome;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * One running Kaast fight in one camp (spec MILESTONE_4.md 10.5, 10.6): the owner, the listed
 * participants (the M3 co-op rules: anyone who entered the ring; one out of it for
 * {@code participantLeaveTicks} leaves the list), the health scaling, the boss bar and the music, the
 * gate, the phases, Rattle, the mines and the hunters he calls, the yield, the reset and the
 * resolution's scene. Nothing here is saved: after a restart the next approach starts a fresh fight,
 * or a knelt Kaast when the owner's duel is already yielded.
 */
public final class KaastSession {
	/** The ticks the spare and strip scenes take before he leaves. */
	private static final int SCENE_TICKS = 60;

	private final UUID owner;
	private final ServerLevel level;
	private final CampLayout layout;
	private final boolean echo;
	/** Participant id to the ticks it has been out of the ring. */
	private final Map<UUID, Integer> participants = new LinkedHashMap<>();
	private final List<CampMine> mines = new ArrayList<>();
	private final List<MandalorianHunter> hunters = new ArrayList<>();
	private @Nullable TorvinKaast kaast;
	private @Nullable Rattle rattle;
	private boolean rattleDestroyed;
	private @Nullable ServerBossEvent bar;
	private int phase = 1;
	private int transition;
	private int emptyTicks;
	private boolean huntersCalled;
	private boolean yielded;
	private boolean yieldDelivered;
	private int minesSet;
	private int sceneTicks = -1;
	private boolean over;

	KaastSession(UUID owner, ServerLevel level, CampLayout layout, boolean echo) {
		this.owner = owner;
		this.level = level;
		this.layout = layout;
		this.echo = echo;
	}

	// ---------------------------------------------------------------- reading

	public UUID owner() {
		return owner;
	}

	public ServerLevel level() {
		return level;
	}

	public CampLayout layout() {
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

	public boolean isYielded() {
		return yielded;
	}

	public boolean isOver() {
		return over;
	}

	public @Nullable TorvinKaast kaast() {
		return kaast;
	}

	public @Nullable Rattle rattle() {
		return rattle;
	}

	public boolean isParticipant(UUID id) {
		return participants.containsKey(id);
	}

	/** The listed participants' ids, in joining order. */
	public List<UUID> participants() {
		return List.copyOf(participants.keySet());
	}

	/** Kaast's scaled maximum health for the listed participants. */
	public double maxHealth() {
		return KaastRules.maxHealth(participants.size(), KaastBalance.get());
	}

	/** How many mines phase 2 sets, and how many are set so far. */
	public int minesWanted() {
		return KaastRules.mines(participants.size(), KaastBalance.get());
	}

	public int minesSet() {
		return minesSet;
	}

	public List<CampMine> mines() {
		return List.copyOf(mines);
	}

	/** Whether Rattle's scan line is on {@code target} now. */
	public boolean marked(LivingEntity target) {
		return rattle != null && rattle.isAlive() && rattle.isScanning(target);
	}

	/** The participant Kaast fights: the nearest one in the ring. */
	public @Nullable ServerPlayer kaastTarget() {
		ServerPlayer best = null;
		double bestDistance = Double.MAX_VALUE;
		Vec3 from = kaast != null ? kaast.position() : Vec3.atCenterOf(layout.heart());

		for (ServerPlayer player : inRing()) {
			double distance = player.position().distanceToSqr(from);

			if (distance < bestDistance) {
				best = player;
				bestDistance = distance;
			}
		}

		return best;
	}

	/** The listed participants inside the ring now, alive and playing. */
	public List<ServerPlayer> inRing() {
		List<ServerPlayer> out = new ArrayList<>();

		for (UUID id : participants.keySet()) {
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);

			if (player != null && fighting(player)) {
				out.add(player);
			}
		}

		return out;
	}

	private boolean fighting(ServerPlayer player) {
		return player.level() == level && player.isAlive() && !player.isSpectator() && layout.inRing(player.getX(), player.getY(), player.getZ());
	}

	// ---------------------------------------------------------------- start

	/** Spawns Kaast (and Rattle) and closes the gate; {@code knelt} spawns him already yielded (a restart). */
	boolean start(boolean knelt) {
		TorvinKaast spawned = KaastEntities.TORVIN_KAAST.create(level, EntitySpawnReason.EVENT);

		if (spawned == null) {
			return false;
		}

		int nest = knelt ? -1 : farthestNest();
		BlockPos at = nest >= 0 ? layout.nests().get(nest) : layout.heart();
		spawned.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
		spawned.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
		spawned.attach(this, echo);
		spawned.setNest(nest);

		if (!level.addFreshEntity(spawned)) {
			return false;
		}

		kaast = spawned;
		addParticipants();
		spawned.rescale(maxHealth());
		spawned.setHealth(spawned.getMaxHealth());

		if (echo) {
			EffectEvents.around(level, KaastEffects.EVENT, spawned.position(), null, spawned.getId(), 1f, KaastEffects.ECHO);
		}

		if (knelt) {
			yielded = true;
			spawned.kneelInYield(level);
			return true;
		}

		spawnRattle();
		setGate(false);
		bar = new ServerBossEvent(UUID.randomUUID(), barName(), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_10);

		for (ServerPlayer player : inRing()) {
			join(player);
		}

		return true;
	}

	/** The nest farthest from the owner (he starts out of reach). */
	private int farthestNest() {
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
		Vec3 from = player != null ? player.position() : Vec3.atCenterOf(layout.heart());
		int best = 0;
		double bestDistance = -1;

		for (int i = 0; i < layout.nests().size(); i++) {
			double distance = Vec3.atCenterOf(layout.nests().get(i)).distanceToSqr(from);

			if (distance > bestDistance) {
				best = i;
				bestDistance = distance;
			}
		}

		return layout.nests().isEmpty() ? -1 : best;
	}

	private void spawnRattle() {
		if (rattleDestroyed || rattle != null && rattle.isAlive() || kaast == null) {
			return;
		}

		Rattle droid = KaastEntities.KAAST_DROID.create(level, EntitySpawnReason.EVENT);

		if (droid == null) {
			return;
		}

		droid.snapTo(kaast.getX(), kaast.getY() + 3, kaast.getZ(), 0f, 0f);
		droid.attach(this);
		AttributeInstance health = droid.getAttribute(Attributes.MAX_HEALTH);

		if (health != null) {
			health.setBaseValue(KaastBalance.get().rattleHealth);
		}

		droid.setHealth(droid.getMaxHealth());

		if (level.addFreshEntity(droid)) {
			rattle = droid;
		}
	}

	void onRattleDestroyed() {
		rattleDestroyed = true;
		rattle = null;
	}

	private Component barName() {
		return Component.translatable(echo ? "boss.oldrepublic.kaast.echo" : "boss.oldrepublic.kaast.name");
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

		if (kaast == null || kaast.isRemoved()) {
			reset();
			return;
		}

		addParticipants();

		if (yielded) {
			deliverYield();
			return;
		}

		tickParticipants();
		kaast.setTarget(kaastTarget());

		if (transition > 0) {
			transition--;
		}

		mines.removeIf(CampMine::isRemoved);
		hunters.removeIf(h -> !h.isAlive());

		if (rattle != null && rattle.isRemoved()) {
			rattle = null;
		}

		for (MandalorianHunter hunter : hunters) {
			if (hunter.getTarget() == null || !hunter.getTarget().isAlive()) {
				hunter.setTarget(kaastTarget());
			}
		}

		if (bar != null) {
			bar.setProgress(Math.clamp(kaast.getHealth() / Math.max(1f, kaast.getMaxHealth()), 0f, 1f));
		}

		emptyTicks = inRing().isEmpty() ? emptyTicks + 1 : 0;

		if (KaastRules.resetDue(emptyTicks, KaastBalance.get())) {
			reset();
		}
	}

	/** Anyone playing inside the ring joins the list. */
	private void addParticipants() {
		for (ServerPlayer player : level.players()) {
			if (!participants.containsKey(player.getUUID()) && fighting(player)) {
				participants.put(player.getUUID(), 0);

				if (bar != null && !yielded) {
					join(player);
				}

				if (kaast != null && !yielded) {
					kaast.rescale(maxHealth());
				}
			}
		}
	}

	/** Out of the ring for {@code participantLeaveTicks}: off the list, the bar and the music. */
	private void tickParticipants() {
		KaastBalance b = KaastBalance.get();
		List<UUID> gone = new ArrayList<>();

		for (Map.Entry<UUID, Integer> entry : participants.entrySet()) {
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
			int outside = player != null && fighting(player) ? 0 : entry.getValue() + 1;
			entry.setValue(outside);

			if (KaastRules.leftFight(outside, b)) {
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

		if (!gone.isEmpty() && kaast != null) {
			kaast.rescale(maxHealth());
		}
	}

	private void join(ServerPlayer player) {
		if (bar != null) {
			bar.addPlayer(player);
		}

		// The current phase's banner and music for whoever joins (everyone at the start).
		int id = kaast == null ? -1 : kaast.getId();
		EffectEvents.toPlayer(player, KaastEffects.EVENT, player.position(), null, id, phase, KaastEffects.PHASE);
		EffectEvents.toPlayer(player, KaastEffects.EVENT, player.position(), null, id, phase, KaastEffects.MUSIC);
	}

	private void leave(ServerPlayer player) {
		if (bar != null) {
			bar.removePlayer(player);
		}

		EffectEvents.toPlayer(player, KaastEffects.EVENT, player.position(), null, -1, 0f, KaastEffects.MUSIC);
	}

	/** Kaast took damage: the phase thresholds, the hunters' call and the yield. */
	void onKaastHurt(TorvinKaast hurt) {
		if (hurt != kaast || yielded) {
			return;
		}

		KaastBalance b = KaastBalance.get();
		float fraction = hurt.getHealth() / Math.max(1f, hurt.getMaxHealth());

		if (KaastRules.yieldDue(hurt.getHealth(), hurt.getMaxHealth(), b)) {
			yieldNow();
			return;
		}

		int next = KaastRules.phaseOf(fraction, b);

		if (next > phase) {
			enterPhase(next);
		}

		if (phase >= 2 && KaastRules.huntersDue(fraction, huntersCalled, b)) {
			callHunters();
		}
	}

	private void enterPhase(int next) {
		KaastBalance b = KaastBalance.get();
		phase = next;
		transition = b.transitionTicks;

		if (kaast != null) {
			kaast.enterPhase(level, next);
			level.playSound(null, kaast.getX(), kaast.getY(), kaast.getZ(), ModSounds.BOSS_KAAST_PHASE, kaast.getSoundSource(), 1.5f, 1f);
		}

		if (next == 2 && rattle != null) {
			rattle.leave(level);
			rattle = null;
		}

		if (next == 3) {
			spawnRattle();
		}

		for (ServerPlayer player : inRing()) {
			EffectEvents.toPlayer(player, KaastEffects.EVENT, player.position(), null, kaast == null ? -1 : kaast.getId(), next, KaastEffects.PHASE);
			EffectEvents.toPlayer(player, KaastEffects.EVENT, player.position(), null, -1, next, KaastEffects.MUSIC);
			QuestHooks.onBossPhase(player, KaastFight.BOSS_ID, next);
		}
	}

	private void callHunters() {
		huntersCalled = true;
		int count = KaastRules.hunters(participants.size(), KaastBalance.get());
		hunters.addAll(Hunters.spawnForFight(level, layout.heart(), count, owner));
	}

	/** Records a mine Kaast set. */
	void mineSet(CampMine mine) {
		mines.add(mine);
		minesSet++;
	}

	/** An operator's jump to a phase (tests, 17.3): his health just under its threshold. */
	void forcePhase(int target) {
		if (kaast == null || yielded) {
			return;
		}

		KaastBalance b = KaastBalance.get();
		kaast.setHealth(KaastRules.healthForPhase(target, kaast.getMaxHealth(), b));
		onKaastHurt(kaast);
	}

	// ---------------------------------------------------------------- the yield

	/** An operator's yield (tests, 17.3): he kneels and the fight clears, as at his yield threshold. */
	void forceYield() {
		if (kaast != null && !yielded) {
			yieldNow();
		}
	}

	private void yieldNow() {
		yielded = true;
		clearFight();

		if (kaast != null) {
			kaast.kneelInYield(level);
		}

		if (echo) {
			finishEcho();
			return;
		}

		deliverYield();
	}

	/** The owner's choice opens (W2) as soon as the owner is online. */
	private void deliverYield() {
		if (yieldDelivered || echo) {
			return;
		}

		ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);

		if (player != null) {
			yieldDelivered = true;
			QuestHooks.onBossYielded(player, KaastFight.BOSS_ID);
		}
	}

	/** The echo yields: the rematch is won, each participant's rewards go through the defeat hook, the echo fades. */
	private void finishEcho() {
		for (UUID id : participants.keySet()) {
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);

			if (player != null) {
				QuestHooks.onBossDefeated(player, KaastFight.BOSS_ID, BossOutcome.ECHO, owner, false);
			}
		}

		if (kaast != null) {
			EffectEvents.around(level, KaastEffects.EVENT, kaast.position(), null, kaast.getId(), 0f, KaastEffects.ECHO);
			kaast.discard();
		}

		over = true;
	}

	/**
	 * The owner chose (W2's {@code kaast:resolve}): spared, he jets away; stripped, he walks out into
	 * the waste; ended, a short end. Each participant's fight ends through the defeat hook (the
	 * owner's first clear, helpers' daily reward).
	 */
	void resolve(KaastOutcome outcome) {
		if (!yielded) {
			yieldNow();
		}

		BossOutcome boss = switch (outcome) {
			case SPARED -> BossOutcome.SPARED;
			case STRIPPED -> BossOutcome.RELEASED;
			case ENDED -> BossOutcome.EXECUTED;
			default -> BossOutcome.SPARED;
		};
		List<UUID> rewarded = new ArrayList<>(participants.keySet());

		if (!rewarded.contains(owner)) {
			rewarded.add(owner);
		}

		for (UUID id : rewarded) {
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);

			if (player != null) {
				QuestHooks.onBossDefeated(player, KaastFight.BOSS_ID, boss, owner, id.equals(owner));
			}
		}

		if (kaast == null || kaast.isRemoved()) {
			over = true;
			return;
		}

		switch (outcome) {
			case ENDED -> {
				kaast.end(level);
				over = true;
			}
			case STRIPPED -> {
				Vec3 away = kaast.position().subtract(Vec3.atCenterOf(layout.heart())).multiply(1, 0, 1);
				Vec3 dir = away.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : away.normalize();
				Vec3 to = kaast.position().add(dir.scale(layout.radius() + 8));
				kaast.getNavigation().moveTo(to.x, to.y, to.z, 0.8);
				level.playSound(null, kaast.getX(), kaast.getY(), kaast.getZ(), ModSounds.BOSS_KAAST_WALK_AWAY, kaast.getSoundSource(), 1f, 1f);
				sceneTicks = SCENE_TICKS;
			}
			default -> sceneTicks = SCENE_TICKS / 2;
		}
	}

	private void tickScene() {
		if (--sceneTicks > 0 && kaast != null && !kaast.isRemoved()) {
			return;
		}

		if (kaast != null && !kaast.isRemoved()) {
			kaast.leave(level);
		}

		over = true;
	}

	// ---------------------------------------------------------------- the end of a fight

	/** The fight stops: mines, nets, hunters and Rattle go, the gate opens, the bar and the music stop. */
	private void clearFight() {
		for (CampMine mine : mines) {
			mine.discard();
		}

		mines.clear();

		for (MandalorianHunter hunter : hunters) {
			if (hunter.isAlive()) {
				hunter.leave(level);
			}
		}

		hunters.clear();

		if (rattle != null) {
			rattle.leave(level);
			rattle = null;
		}

		for (UUID id : participants.keySet()) {
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);

			if (player != null) {
				if (Netting.isNetted(player)) {
					Netting.free(player, Netting.FREED_GONE);
				}

				EffectEvents.toPlayer(player, KaastEffects.EVENT, player.position(), null, -1, 0f, KaastEffects.MUSIC);
			}
		}

		if (bar != null) {
			bar.removeAllPlayers();
			bar = null;
		}

		setGate(true);
	}

	/**
	 * The reset (nobody in the ring for {@code resetTicks}, or an operator): everything cleared and
	 * Kaast gone; the next approach starts a fresh fight at full health with Rattle back.
	 */
	void reset() {
		if (over) {
			return;
		}

		clearFight();

		if (kaast != null && !kaast.isRemoved()) {
			kaast.discard();
		}

		kaast = null;
		over = true;
	}

	/** Opens or closes the camp's gate. */
	void setGate(boolean open) {
		for (BlockPos pos : layout.gates()) {
			if (!level.hasChunkAt(pos)) {
				continue;
			}

			BlockState state = level.getBlockState(pos);

			if (state.getBlock() instanceof CampGateBlock && state.getValue(CampGateBlock.OPEN) != open) {
				level.setBlockAndUpdate(pos, state.setValue(CampGateBlock.OPEN, open));
			}
		}
	}
}
