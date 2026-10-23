package com.oldrepublic.boss;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.boss.block.PylonCrystal;
import com.oldrepublic.boss.block.ReliquaryHeartBlockEntity;
import com.oldrepublic.boss.block.ReliquarySealBlock;
import com.oldrepublic.boss.block.ResonancePylonBlock;
import com.oldrepublic.boss.raid.BossMusicPayload;
import com.oldrepublic.combat.Holds;
import com.oldrepublic.dialogue.DialogueSession;
import com.oldrepublic.dialogue.Dialogues;
import com.oldrepublic.enemy.construct.RuinSentinel;
import com.oldrepublic.enemy.construct.Sentinels;
import com.oldrepublic.force.ForcePush;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.Path;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.BossOutcome;
import com.oldrepublic.quest.story.ChapterThree;
import com.oldrepublic.quest.story.ChapterThreeStage;
import com.oldrepublic.saber.duel.GuardState;
import com.oldrepublic.saber.duel.NpcGuard;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * One Reliquary's Collector fight, held in memory by {@link BossArenas} and keyed by the heart (spec
 * 8.2-8.7, 8.9). It owns Orren Vasch, the pylon foci, the phase 2 sentinels, the boss bar and the
 * participants, and drives the heart's state through {@link HeartRules}. Nothing here is saved: a
 * fight in progress resets when the session is dropped (unload, restart, everyone gone); only the
 * heart's YIELDED and RESOLVED survive.
 *
 * <pre>
 * WAITING (lectern) --owner crosses, first attempt--> MEETING --closed or 400 ticks--> FIGHTING
 * WAITING --a later attempt: 6 blocks from the dais, the lectern or Vasch used----->  FIGHTING
 * FORMING (the echo forms, 60 ticks) ----------------------------------------------> FIGHTING
 * FIGHTING <--> TRANSITION (60% and 30%, 60 ticks each) --10%--> YIELDED --resolve--> RESOLVING --> ENDED
 * FIGHTING --nobody alive inside for 200 ticks--> WAITING (reset; an echo ends instead)
 * </pre>
 *
 * Ticked once per server tick by {@link BossArenas} (END_LEVEL_TICK), so a game test can run a session
 * on a fixture without a heart block entity ({@code needsHeart} false).
 */
public final class ArenaSession {
	/** Where the session is in the flow above. */
	public enum Stage {
		WAITING,
		MEETING,
		FORMING,
		FIGHTING,
		TRANSITION,
		YIELDED,
		RESOLVING,
		ENDED
	}

	public static final Identifier MEET_DIALOGUE = OldRepublicMod.id("c3_collector_meet");
	public static final Identifier STANCE_DIALOGUE = OldRepublicMod.id("c3_collector_stance");
	static final long NEVER = Long.MIN_VALUE / 4;
	/** The echo never yields: it dissolves when a hit takes it to this much health in phase 3. */
	static final float ECHO_FLOOR = 1f;
	/** A listed participant within this distance of the threshold, on the antechamber side, opens the doorway seal. */
	private static final double RETURN_REACH = 3.5;
	/** The four pylons wake one by one, this many ticks apart, during the first transition (8.3). */
	private static final int WAKE_SPACING = 15;
	/** The leap to the dais: flight ticks, and when he is set down on it if the leap fell short. */
	private static final int LEAP_TICKS = 14;
	private static final int LEAP_SETTLE_TICKS = 22;
	/** The echo's dissolve, and how long a resolved session lingers for its scene at most. */
	private static final int DISSOLVE_TICKS = 40;
	/** How often the threat target and the bar's viewers are refreshed. */
	private static final int REFRESH_TICKS = 10;
	/** How far off the dais the Unsung stagger throws him. */
	private static final double UNSUNG_SHOVE = 0.9;
	/** In Peaceful he says his line, then kneels this much later: an overlay line's time on screen (6.9). */
	public static final int PEACEFUL_TALK_TICKS = 60;

	/** One hit on him, for the threat window. */
	private record Hit(long at, float amount) {
	}

	/** A listed participant. */
	private static final class Participant {
		final UUID id;
		long awaySince = NEVER;
		boolean music;
		int pushCooldown;
		final ArrayDeque<Hit> hits = new ArrayDeque<>();

		Participant(UUID id) {
			this.id = id;
		}
	}

	/** One resonance pylon and its crystal. */
	private static final class Pylon {
		final int index;
		final BlockPos pos;
		PylonCrystal crystal = PylonCrystal.DORMANT;
		float resonance;
		float max;
		@Nullable PylonFocus focus;
		long silencedAt = NEVER;
		long regrowStart = NEVER;
		int resung;

		Pylon(int index, BlockPos pos) {
			this.index = index;
			this.pos = pos.immutable();
		}
	}

	private final ServerLevel level;
	private final BlockPos heart;
	private final ArenaLayout layout;
	private final @Nullable UUID owner;
	private final CollectorHooks.Sink hooks;
	private final boolean needsHeart;
	private final boolean echo;
	private final boolean unchained;
	private final Map<UUID, Participant> participants = new LinkedHashMap<>();
	private final List<Pylon> pylons = new ArrayList<>();
	private final List<RuinSentinel> sentinels = new ArrayList<>();
	private final Set<UUID> stanceCredited = new HashSet<>();

	private Stage stage = Stage.WAITING;
	private HeartState heartState;
	private int attempts;
	private @Nullable OrrenVasch vasch;
	private @Nullable ServerBossEvent bar;
	private int phase = CollectorRules.PHASE_ONE;
	private long now;
	private long stageStart;
	private int transitionTo;
	private boolean pendingFloor;
	private boolean vaschLost;
	private long crossedAt = NEVER;
	private boolean doorOpen = true;
	private long noneInsideSince = NEVER;
	private @Nullable UUID threat;
	private long specialSwitchAt = NEVER;
	private boolean specialOther;
	private boolean wardDown;
	private long unsungUntil = NEVER;
	private int sentWard = -1;
	private boolean peacefulTalk;
	private long peacefulKneelAt = NEVER;
	private boolean yieldNotifiedNoHeart;
	private boolean echoCleared;
	private CollectorScenes.@Nullable Scene scene;
	private @Nullable Difficulty difficultyOverride;

	/**
	 * A session at {@code heart} for {@code owner}. {@code needsHeart}: the session ends when the heart
	 * block entity is gone (production); a game test on a fixture passes false. {@code echo} and
	 * {@code unchained} make it the rematch (8.9).
	 */
	ArenaSession(ServerLevel level, BlockPos heart, ArenaLayout layout, @Nullable UUID owner, CollectorHooks.Sink hooks, boolean needsHeart, boolean echo,
			boolean unchained) {
		this.level = level;
		this.heart = heart.immutable();
		this.layout = layout;
		this.owner = owner;
		this.hooks = hooks;
		this.needsHeart = needsHeart;
		this.echo = echo;
		this.unchained = echo && unchained;
		this.now = level.getGameTime();
		this.stageStart = now;
		ReliquaryHeartBlockEntity entity = heartEntity();
		this.heartState = entity != null ? entity.state() : HeartState.READY;
		this.attempts = entity != null ? entity.attempts() : 0;

		for (int i = 0; i < layout.pylons().size(); i++) {
			pylons.add(new Pylon(i, layout.pylons().get(i)));
		}
	}

	// ---------------------------------------------------------------- reads

	public ServerLevel level() {
		return level;
	}

	public BlockPos heart() {
		return heart;
	}

	public ArenaLayout layout() {
		return layout;
	}

	public @Nullable UUID owner() {
		return owner;
	}

	public Stage stage() {
		return stage;
	}

	/** The fight phase, 1 to 3. */
	public int phase() {
		return phase;
	}

	public @Nullable OrrenVasch vasch() {
		return vasch;
	}

	public boolean isEcho() {
		return echo;
	}

	public boolean isUnchained() {
		return unchained;
	}

	/** The heart's state as this session last set it (the block entity's when it has one). */
	public HeartState heartState() {
		return heartState;
	}

	/** Fights the owner started at this heart (the meeting is skipped from 1 on). */
	public int attempts() {
		return attempts;
	}

	/** A fight is running (the transitions included): his presence keeps players from sleeping. */
	public boolean isFighting() {
		return stage == Stage.FIGHTING || stage == Stage.TRANSITION;
	}

	public boolean isTransitioning() {
		return stage == Stage.TRANSITION;
	}

	/** Whether {@code id} is listed (alive or dead, inside or briefly away). */
	public boolean isParticipant(UUID id) {
		return participants.containsKey(id);
	}

	/** The listed participants' ids, oldest first. */
	public List<UUID> participants() {
		return List.copyOf(participants.keySet());
	}

	/** Participants online in this level, alive, and inside the hall box. */
	public List<ServerPlayer> participantsInHall() {
		List<ServerPlayer> inside = new ArrayList<>(participants.size());

		for (UUID id : participants.keySet()) {
			ServerPlayer player = player(id);

			if (player != null && player.isAlive() && !player.isSpectator() && layout.isInHall(player)) {
				inside.add(player);
			}
		}

		return inside;
	}

	/** Damage he deals: x1.15 for the echo, x1.25 Unchained (8.9). */
	public float outgoing() {
		return CollectorRules.outgoingMultiplier(echo, unchained, CollectorBalance.get());
	}

	/** Pylons singing now (the ward's strength). */
	public int singingCount() {
		int singing = 0;

		for (Pylon pylon : pylons) {
			if (pylon.crystal == PylonCrystal.SINGING) {
				singing++;
			}
		}

		return singing;
	}

	/** The crystal of pylon {@code index}. */
	public PylonCrystal pylonCrystal(int index) {
		return pylons.get(index).crystal;
	}

	/** Resonance left in pylon {@code index}. */
	public float pylonResonance(int index) {
		return pylons.get(index).resonance;
	}

	/** The focus of pylon {@code index}, or null while it neither sings nor regrows. */
	public @Nullable PylonFocus pylonFocus(int index) {
		return pylons.get(index).focus;
	}

	/** Whether the Resonance Ward is down for the rest of phase 2 (after the Unsung window began). */
	public boolean isWardDown() {
		return wardDown;
	}

	/** Whether the Unsung window runs: x{@code unsungMultiplier} taken. */
	public boolean isUnsung() {
		return now < unsungUntil;
	}

	/** The ward's damage factor now: phase 2 only, before the Unsung window; Unsung is separate. */
	public float wardFactor() {
		if (phase != CollectorRules.PHASE_TWO || stage != Stage.FIGHTING) {
			return 1f;
		}

		return CollectorRules.wardMultiplier(singingCount(), false, wardDown, CollectorBalance.get());
	}

	/** The phase 2 sentinels still standing. */
	public List<RuinSentinel> sentinels() {
		sentinels.removeIf(s -> s.isRemoved() || !s.isAlive());
		return List.copyOf(sentinels);
	}

	/** Whether {@code focus} is one of this session's live pylon foci. */
	boolean ownsFocus(PylonFocus focus) {
		return stage != Stage.ENDED && focus.index() >= 0 && focus.index() < pylons.size() && pylons.get(focus.index()).focus == focus;
	}

	/**
	 * Game tests: the difficulty the session plays by (Peaceful: he talks, then kneels), without
	 * changing the world's difficulty, which other tests share. Null restores the world's.
	 */
	public void overrideDifficulty(@Nullable Difficulty difficulty) {
		difficultyOverride = difficulty;
	}

	private boolean peaceful() {
		return (difficultyOverride != null ? difficultyOverride : level.getDifficulty()) == Difficulty.PEACEFUL;
	}

	// ---------------------------------------------------------------- the per-tick driver

	/** One server tick. */
	void tick() {
		if (stage == Stage.ENDED) {
			return;
		}

		now = level.getGameTime();

		if (needsHeart && heartEntity() == null) {
			end();
			return;
		}

		CollectorBalance b = CollectorBalance.get();

		if (vaschLost || vasch == null || vasch.isRemoved()) {
			vaschLost = false;

			if (!recoverVasch(b)) {
				return;
			}
		}

		if (peacefulKneelAt != NEVER) {
			// Peaceful: he is saying his line; nothing else runs until he kneels.
			tickPeacefulTalk();
			tickDoorSeal(b);
			return;
		}

		switch (stage) {
			case WAITING -> tickWaiting(b);
			case MEETING -> tickMeeting(b);
			case FORMING -> tickForming(b);
			case FIGHTING, TRANSITION -> tickFight(b);
			case YIELDED -> tickYielded(b);
			case RESOLVING -> tickResolving();
			default -> {
			}
		}

		tickDoorSeal(b);
	}

	/** Vasch is gone (killed by a command, fell out of the world, unloaded): bring him back where the flow needs him. */
	private boolean recoverVasch(CollectorBalance b) {
		switch (stage) {
			case RESOLVING -> {
				// The escort walked him out (or the scene ended him): the session is over.
				end();
				return false;
			}
			case YIELDED -> {
				vasch = spawnVasch(layout.daisFeet(), facingFromDais());
				kneel();
				return vasch != null;
			}
			case WAITING -> {
				vasch = spawnAtLectern();
				return vasch != null;
			}
			default -> {
				if (echo) {
					end();
					return false;
				}

				// A fight cannot go on without him: it resets.
				vasch = spawnAtLectern();

				if (vasch != null) {
					reset();
				}

				return vasch != null;
			}
		}
	}

	private void tickWaiting(CollectorBalance b) {
		OrrenVasch v = vasch;

		if (v == null) {
			return;
		}

		v.setAction(OrrenVasch.Action.READING);

		if (v.position().distanceToSqr(layout.lecternFeet()) > 4.0) {
			v.teleportTo(layout.lecternFeet().x, layout.lecternFeet().y, layout.lecternFeet().z);
		}

		if (attempts == 0) {
			ServerPlayer ownerPlayer = owner == null ? null : player(owner);

			if (ownerPlayer != null && ownerPlayer.isAlive() && layout.isInHall(ownerPlayer)) {
				beginMeeting(ownerPlayer);
			}

			return;
		}

		for (ServerPlayer player : level.players()) {
			if (eligible(player) && layout.isInHall(player) && HeartRules.daisStart(layout.daisDistanceSqr(player.position()), b)) {
				startFight("again");
				return;
			}
		}
	}

	/** The first attempt: the owner crossed the threshold; he speaks (W2's {@code c3_collector_meet}). */
	private void beginMeeting(ServerPlayer ownerPlayer) {
		setStage(Stage.MEETING);
		crossedAt = now;
		OrrenVasch v = vasch;

		if (v != null) {
			v.setAction(OrrenVasch.Action.NONE);
			v.getLookControl().setLookAt(ownerPlayer);
		}

		Dialogues.open(ownerPlayer, MEET_DIALOGUE, speakerFor(ownerPlayer));
	}

	private void tickMeeting(CollectorBalance b) {
		if (HeartRules.meetingTimedOut(now - stageStart, b)) {
			ServerPlayer ownerPlayer = owner == null ? null : player(owner);

			if (ownerPlayer != null) {
				closeIfOpen(ownerPlayer, MEET_DIALOGUE);
			}

			afterMeeting();
		}
	}

	/** The meeting dialogue closed for {@code player} (from {@link CollectorDialogues}). */
	void onMeetingClosed(ServerPlayer player) {
		if (stage == Stage.MEETING && player.getUUID().equals(owner)) {
			afterMeeting();
		}
	}

	private void afterMeeting() {
		if (peaceful()) {
			peacefulKneel();
		} else {
			startFight(null);
		}
	}

	private void tickForming(CollectorBalance b) {
		joinNewcomers();

		if (now - stageStart >= b.echoFormTicks) {
			OrrenVasch v = vasch;

			if (v != null) {
				v.setForming(false);
			}

			beginFight(b);
		}
	}

	private void tickYielded(CollectorBalance b) {
		OrrenVasch v = vasch;

		if (v == null) {
			return;
		}

		v.getNavigation().stop();

		if (!layout.isInHall(v)) {
			v.teleportTo(layout.daisFeet().x, layout.daisFeet().y, layout.daisFeet().z);
		}

		notifyYieldIfDue();
	}

	private void tickResolving() {
		CollectorScenes.Scene current = scene;

		if (current == null || current.tick(now)) {
			end();
		}
	}

	// ---------------------------------------------------------------- starting the fight

	/**
	 * Starts the fight now (the meeting closed or timed out, a later attempt's dais or lectern, a
	 * command). {@code bark} is a line key spoken as it begins ({@code again}, {@code face_me}), or null.
	 */
	public void startFight(@Nullable String bark) {
		if (stage != Stage.WAITING && stage != Stage.MEETING) {
			return;
		}

		if (peaceful()) {
			peacefulKneel();
			return;
		}

		ServerPlayer ownerPlayer = owner == null ? null : player(owner);

		if (ownerPlayer != null) {
			closeIfOpen(ownerPlayer, MEET_DIALOGUE);
		}

		if (!echo) {
			attempts++;
			ReliquaryHeartBlockEntity entity = heartEntity();

			if (entity != null) {
				entity.addAttempt();
			}

			step(HeartRules.Event.FIGHT_START);
		}

		boolean firstAttempt = attempts == 1 && bark == null;
		beginFight(CollectorBalance.get());

		if (bark != null) {
			say(bark);
		} else if (firstAttempt) {
			say("give_back");
		}
	}

	/** The rematch: the echo forms from smoke at the dais over {@code echoFormTicks} (8.9). */
	void beginEcho() {
		CollectorBalance b = CollectorBalance.get();
		syncBlocks();
		vasch = spawnVasch(layout.daisFeet(), facingFromDais());

		if (vasch == null) {
			end();
			return;
		}

		vasch.setForming(true);
		vasch.setAction(OrrenVasch.Action.NONE);
		setStage(Stage.FORMING);
		crossedAt = NEVER;
		// Around the dais, not toTracking: nobody tracks him yet in the tick he is spawned.
		EffectEvents.around(level, BossEffects.ECHO, vasch.position(), null, vasch.getId(), b.echoFormTicks, BossEffects.ECHO_FORM);
		joinNewcomers();
	}

	private void beginFight(CollectorBalance b) {
		OrrenVasch v = vasch;

		if (v == null) {
			return;
		}

		for (ServerPlayer player : level.players()) {
			if (eligible(player) && layout.isInHall(player)) {
				participants.computeIfAbsent(player.getUUID(), Participant::new);
			}
		}

		phase = CollectorRules.PHASE_ONE;
		v.setPhase(phase);
		v.setMaxHealth(CollectorRules.maxHealth(participants.size(), unchained, b), false);
		v.setAction(OrrenVasch.Action.NONE);
		v.setBladeLit(true);
		v.setGuardState(GuardState.READY);
		setStage(Stage.FIGHTING);

		if (crossedAt == NEVER) {
			crossedAt = now;
		}

		bar = new ServerBossEvent(UUID.randomUUID(), barName(phase), BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.PROGRESS);
		bar.setProgress(1f);
		CollectorCombatGoal combat = v.combat();

		if (combat != null) {
			combat.onPhaseStart(phase, now);
		}

		for (Participant participant : participants.values()) {
			ServerPlayer player = player(participant.id);

			if (player != null) {
				bar.addPlayer(player);
				sendPhase(player, BossEffects.PHASE_START, 1f);
				participant.music = true;
				participant.pushCooldown = Progression.get(player).cooldown(ForcePush.ABILITY_ID);
				hooks.onBossPhase(player, BossArenas.COLLECTOR, phase);
			}
		}
	}

	/**
	 * Peaceful (6.9): no fight; he talks (to an Independent owner, his offer), then kneels
	 * {@link #PEACEFUL_TALK_TICKS} later, so the line can be read before the yield's own.
	 */
	private void peacefulKneel() {
		if (peacefulTalk) {
			return;
		}

		peacefulTalk = true;
		ServerPlayer ownerPlayer = owner == null ? null : player(owner);
		say(ownerPlayer != null && Identity.path(ownerPlayer) == Path.INDEPENDENT ? "offer" : "peaceful");
		peacefulKneelAt = now + PEACEFUL_TALK_TICKS;
		OrrenVasch v = vasch;

		if (v != null) {
			v.setAction(OrrenVasch.Action.NONE);
			v.getNavigation().stop();

			if (ownerPlayer != null) {
				v.getLookControl().setLookAt(ownerPlayer);
			}
		}
	}

	/** Whether he is saying his Peaceful line now (he kneels when it ends). */
	public boolean isPeacefulTalking() {
		return peacefulKneelAt != NEVER;
	}

	/** The Peaceful line is over: everyone awakened in the hall (and the owner) is listed, and he kneels. */
	private void tickPeacefulTalk() {
		if (now < peacefulKneelAt) {
			return;
		}

		peacefulKneelAt = NEVER;

		for (ServerPlayer player : level.players()) {
			if (eligible(player) && layout.isInHall(player)) {
				participants.computeIfAbsent(player.getUUID(), Participant::new);
			}
		}

		if (owner != null && !participants.containsKey(owner)) {
			ServerPlayer ownerPlayer = player(owner);

			if (ownerPlayer != null) {
				participants.put(owner, new Participant(owner));
			}
		}

		yieldNow();
	}

	// ---------------------------------------------------------------- the fight

	private void tickFight(CollectorBalance b) {
		OrrenVasch v = vasch;

		if (v == null) {
			return;
		}

		// Only phase 1 proper: once the first transition begins, the roster is closed (8.6).
		if (phase == CollectorRules.PHASE_ONE && stage == Stage.FIGHTING) {
			joinNewcomers();
		}

		tickParticipants(b);

		if (stage != Stage.FIGHTING && stage != Stage.TRANSITION) {
			return;
		}

		if (!v.isRemoved() && !layout.isInHall(v)) {
			leash(v);
		}

		if (now % REFRESH_TICKS == 0 || v.getTarget() == null || !v.getTarget().isAlive()) {
			ServerPlayer target = threatTarget(b);
			v.setTarget(target);
			threat = target == null ? null : target.getUUID();
		}

		watchPushes(v);

		if (stage == Stage.TRANSITION) {
			tickTransition(v, b);
		} else {
			CollectorCombatGoal combat = v.combat();

			if (combat != null) {
				combat.serverTick(level, now);
			}

			if (phase == CollectorRules.PHASE_TWO) {
				tickPylons(b);
			}

			if (pendingFloor) {
				pendingFloor = false;
				onFloor();
			}
		}

		if (bar != null && vasch != null && vasch.getMaxHealth() > 0f) {
			bar.setProgress(Mth.clamp(vasch.getHealth() / vasch.getMaxHealth(), 0f, 1f));
		}
	}

	/** Awakened players who walk into the hall during phase 1 join; his maximum rises and keeps its fraction (8.6). */
	private void joinNewcomers() {
		CollectorBalance b = CollectorBalance.get();
		OrrenVasch v = vasch;

		for (ServerPlayer player : level.players()) {
			if (participants.containsKey(player.getUUID()) || !eligible(player) || !layout.isInHall(player)) {
				continue;
			}

			Participant participant = new Participant(player.getUUID());
			participants.put(player.getUUID(), participant);

			if (crossedAt == NEVER) {
				crossedAt = now;
			}

			participant.pushCooldown = Progression.get(player).cooldown(ForcePush.ABILITY_ID);

			if (stage == Stage.FIGHTING && v != null) {
				v.setMaxHealth(CollectorRules.maxHealth(participants.size(), unchained, b), true);
			}

			if (bar != null) {
				bar.addPlayer(player);
				sendPhase(player, phaseVariant(), 0f);
				participant.music = true;
				hooks.onBossPhase(player, BossArenas.COLLECTOR, phase);
			}
		}
	}

	/** Away timers, the bar's viewers, the music per participant, and the reset timer. */
	private void tickParticipants(CollectorBalance b) {
		boolean aliveInside = false;
		List<UUID> dropped = new ArrayList<>();

		for (Participant participant : participants.values()) {
			ServerPlayer player = player(participant.id);
			boolean away = player == null || player.isAlive() && !layout.isInReliquary(player.position());

			if (away) {
				if (participant.awaySince == NEVER) {
					participant.awaySince = now;
				}

				if (HeartRules.participantDropped(now - participant.awaySince, b)) {
					dropped.add(participant.id);
					continue;
				}
			} else {
				participant.awaySince = NEVER;
			}

			if (player == null) {
				continue;
			}

			boolean inHall = player.isAlive() && !player.isSpectator() && layout.isInHall(player);
			aliveInside |= inHall;

			if (bar != null && now % REFRESH_TICKS == 0) {
				bar.addPlayer(player);
			}

			if (inHall && !participant.music) {
				participant.music = true;
				sendPhase(player, phaseVariant(), 0f);
			} else if (!inHall && participant.music) {
				participant.music = false;
				sendPhase(player, BossEffects.PHASE_MUSIC_STOP, 1f);
			}
		}

		for (UUID id : dropped) {
			Participant participant = participants.remove(id);
			ServerPlayer player = player(id);

			if (player != null && participant != null && participant.music) {
				sendPhase(player, BossEffects.PHASE_MUSIC_STOP, 1f);
			}

			if (player != null && bar != null) {
				bar.removePlayer(player);
			}
		}

		if (bar != null && now % REFRESH_TICKS == 0) {
			for (ServerPlayer viewer : List.copyOf(bar.getPlayers())) {
				if (viewer.isRemoved() || !participants.containsKey(viewer.getUUID())) {
					bar.removePlayer(viewer);
				}
			}
		}

		if (aliveInside) {
			noneInsideSince = NEVER;
		} else {
			if (noneInsideSince == NEVER) {
				noneInsideSince = now;
			}

			if (HeartRules.resetDue(now - noneInsideSince, b)) {
				reset();
			}
		}
	}

	/** He left the hall box: smoke where he was, and he reappears at the dais (8.4). */
	private void leash(OrrenVasch v) {
		EffectEvents.toTracking(v, BossEffects.LEASH, v.position(), null, v.getId(), 1f, BossEffects.LEASH_VANISH);
		Vec3 dais = layout.daisFeet();
		v.teleportTo(dais.x, dais.y, dais.z);
		v.setDeltaMovement(Vec3.ZERO);
		v.getNavigation().stop();
		EffectEvents.toTracking(v, BossEffects.LEASH, dais, null, v.getId(), 1f, BossEffects.LEASH_REAPPEAR);
	}

	/** A participant cast Push (any branch, Deflecting Pulse included): its cooldown rose this tick. */
	private void watchPushes(OrrenVasch v) {
		CollectorCombatGoal combat = v.combat();

		for (Participant participant : participants.values()) {
			ServerPlayer player = player(participant.id);

			if (player == null) {
				continue;
			}

			int cooldown = Progression.get(player).cooldown(ForcePush.ABILITY_ID);

			if (cooldown > participant.pushCooldown && combat != null && stage == Stage.FIGHTING) {
				combat.onPushCast(player);
			}

			participant.pushCooldown = cooldown;
		}
	}

	/** The participant with the most damage in the last {@code threatWindowTicks}, else the nearest inside. */
	private @Nullable ServerPlayer threatTarget(CollectorBalance b) {
		OrrenVasch v = vasch;
		ServerPlayer best = null;
		float bestDamage = 0f;
		ServerPlayer nearest = null;
		double nearestSqr = Double.MAX_VALUE;

		for (Participant participant : participants.values()) {
			while (!participant.hits.isEmpty() && now - participant.hits.peekFirst().at() > b.threatWindowTicks) {
				participant.hits.pollFirst();
			}

			ServerPlayer player = player(participant.id);

			if (player == null || !player.isAlive() || player.isSpectator() || !layout.isInHall(player)) {
				continue;
			}

			float damage = 0f;

			for (Hit hit : participant.hits) {
				damage += hit.amount();
			}

			if (damage > bestDamage) {
				bestDamage = damage;
				best = player;
			}

			double sqr = v == null ? 0 : player.distanceToSqr(v);

			if (sqr < nearestSqr) {
				nearestSqr = sqr;
				nearest = player;
			}
		}

		return best != null ? best : nearest;
	}

	/**
	 * The Hurl and Lunge target (8.6): the threat target, but every {@code threatSwitchTicks} it
	 * alternates to the participant nearest him other than the threat target.
	 */
	public @Nullable ServerPlayer specialTarget(long time) {
		CollectorBalance b = CollectorBalance.get();

		if (specialSwitchAt == NEVER) {
			specialSwitchAt = time + b.threatSwitchTicks;
			specialOther = false;
		} else if (time >= specialSwitchAt) {
			specialSwitchAt = time + b.threatSwitchTicks;
			specialOther = !specialOther;
		}

		ServerPlayer main = threat == null ? null : player(threat);

		if (!specialOther || vasch == null) {
			return main;
		}

		ServerPlayer other = null;
		double otherSqr = Double.MAX_VALUE;

		for (ServerPlayer player : participantsInHall()) {
			if (player != main && player.distanceToSqr(vasch) < otherSqr) {
				otherSqr = player.distanceToSqr(vasch);
				other = player;
			}
		}

		return other != null ? other : main;
	}

	// ---------------------------------------------------------------- damage gates (from OrrenVasch)

	/** Whether a hit may land (8.4): a running fight, a listed participant standing in the hall box. */
	boolean acceptsDamage(OrrenVasch v, DamageSource source) {
		if (v != vasch || stage != Stage.FIGHTING || pendingFloor) {
			return false;
		}

		return source.getEntity() instanceof ServerPlayer attacker && participants.containsKey(attacker.getUUID()) && layout.isInHall(attacker);
	}

	/** A hit landed for {@code dealt}: threat, and the Grip's break rules. */
	void onVaschHurt(DamageSource source, float dealt) {
		ServerPlayer attacker = source.getEntity() instanceof ServerPlayer p ? p : null;

		if (attacker != null) {
			Participant participant = participants.get(attacker.getUUID());

			if (participant != null && dealt > 0f) {
				participant.hits.addLast(new Hit(now, dealt));
			}
		}

		OrrenVasch v = vasch;
		CollectorCombatGoal combat = v == null ? null : v.combat();

		if (combat != null && dealt > 0f) {
			combat.onHitTaken(attacker, dealt);
		}
	}

	/** The health no hit may take him below now: the next threshold (8.3); 0 when no floor applies. */
	float healthFloor() {
		OrrenVasch v = vasch;

		if (v == null || stage != Stage.FIGHTING) {
			return 0f;
		}

		if (echo && phase >= CollectorRules.PHASE_THREE) {
			return ECHO_FLOOR;
		}

		return CollectorRules.floorFraction(phase, echo, unchained, CollectorBalance.get()) * v.getMaxHealth();
	}

	/** A hit reached the floor: the transition, the yield or the echo's end follows on this tick's session update. */
	void onFloorReached() {
		if (stage == Stage.FIGHTING) {
			pendingFloor = true;
		}
	}

	/** Vasch died or was removed outside the session's own flow. */
	void onVaschLost() {
		vaschLost = true;
	}

	private void onFloor() {
		switch (phase) {
			case CollectorRules.PHASE_ONE -> beginTransition(CollectorRules.PHASE_TWO);
			case CollectorRules.PHASE_TWO -> beginTransition(CollectorRules.PHASE_THREE);
			default -> {
				if (echo) {
					dissolveEcho();
				} else {
					yieldNow();
				}
			}
		}
	}

	// ---------------------------------------------------------------- transitions

	private void beginTransition(int to) {
		OrrenVasch v = vasch;

		if (v == null) {
			return;
		}

		CollectorCombatGoal combat = v.combat();

		if (combat != null) {
			combat.abortMove(true);
			combat.clearRidges();
		}

		v.clearSpent();
		v.getNavigation().stop();
		setStage(Stage.TRANSITION);
		transitionTo = to;
		CollectorBalance b = CollectorBalance.get();

		if (to == CollectorRules.PHASE_TWO) {
			v.setAction(OrrenVasch.Action.LEAP);
			leapToDais(v);
			say("sing");
		} else {
			// The remaining pylons shatter into him; he takes up the holocron, hood up (8.3).
			for (Pylon pylon : pylons) {
				if (pylon.crystal != PylonCrystal.EMPTY || pylon.focus != null) {
					shatter(pylon, v);
				}
			}

			unsungUntil = NEVER;
			wardDown = true;
			sendWard(v, 0);
			v.setAction(OrrenVasch.Action.UNKEPT);
			phase = CollectorRules.PHASE_THREE;
			v.setPhase(phase);
			say("burns");
		}

		if (bar != null) {
			bar.setColor(to == CollectorRules.PHASE_TWO ? BossEvent.BossBarColor.PURPLE : BossEvent.BossBarColor.RED);
		}

		int variant = to == CollectorRules.PHASE_TWO ? BossEffects.PHASE_TRANSITION_ONE : BossEffects.PHASE_TRANSITION_TWO;

		for (Participant participant : participants.values()) {
			ServerPlayer player = player(participant.id);

			if (player != null) {
				sendPhase(player, variant, 1f);
				participant.music = layout.isInHall(player) && player.isAlive();
				hooks.onBossPhase(player, BossArenas.COLLECTOR, to);
			}
		}

		if (bar != null) {
			bar.setName(barName(to));
		}

		OldRepublicMod.LOGGER.debug("Collector at {} enters transition to phase {} ({} ticks)", heart, to, b.transitionTicks);
	}

	/** An arc toward the dais top; he is set down on it if it falls short. */
	private void leapToDais(OrrenVasch v) {
		Vec3 to = layout.daisFeet().subtract(v.position());
		// Horizontal air drag (0.91 a tick) shortens a 14-tick flight to about 8.1 ticks of travel.
		double reach = (1 - Math.pow(0.91, LEAP_TICKS)) / 0.09;
		double vy = 0.08 * LEAP_TICKS / 2.0 + Math.max(0, to.y) / LEAP_TICKS;
		v.setDeltaMovement(to.x / reach, Math.min(1.2, vy), to.z / reach);
		v.hurtMarked = true;
	}

	private void tickTransition(OrrenVasch v, CollectorBalance b) {
		long age = now - stageStart;

		if (transitionTo == CollectorRules.PHASE_TWO) {
			if (age == LEAP_SETTLE_TICKS && v.position().distanceToSqr(layout.daisFeet()) > 2.25) {
				v.teleportTo(layout.daisFeet().x, layout.daisFeet().y, layout.daisFeet().z);
				v.setDeltaMovement(Vec3.ZERO);
			}

			for (Pylon pylon : pylons) {
				if (pylon.crystal == PylonCrystal.DORMANT && age >= (long) WAKE_SPACING * (pylon.index + 1)) {
					sing(pylon, v, b);
				}
			}
		}

		if (age < b.transitionTicks) {
			return;
		}

		phase = transitionTo;
		v.setPhase(phase);
		v.setAction(OrrenVasch.Action.NONE);
		setStage(Stage.FIGHTING);

		if (phase == CollectorRules.PHASE_TWO) {
			for (Pylon pylon : pylons) {
				if (pylon.crystal == PylonCrystal.DORMANT) {
					sing(pylon, v, b);
				}
			}

			wardDown = false;
			unsungUntil = NEVER;
			spawnSentinels(b);
			sendWard(v, singingCount());
		}

		CollectorCombatGoal combat = v.combat();

		if (combat != null) {
			combat.onPhaseStart(phase, now);
		}
	}

	/** Jumps the fight to phase {@code n} through its transition (1-3), or to the yield (4): the command. */
	public boolean forcePhase(int n) {
		if (stage == Stage.WAITING || stage == Stage.MEETING) {
			startFight(null);
		}

		if (stage == Stage.FORMING) {
			if (vasch != null) {
				vasch.setForming(false);
			}

			beginFight(CollectorBalance.get());
		}

		if (stage != Stage.FIGHTING && stage != Stage.TRANSITION || vasch == null) {
			return false;
		}

		CollectorBalance b = CollectorBalance.get();

		if (n >= 4) {
			if (echo) {
				dissolveEcho();
			} else {
				vasch.setHealthUnlocked(b.yieldAt * vasch.getMaxHealth());
				yieldNow();
			}

			return true;
		}

		if (stage == Stage.TRANSITION) {
			return false;
		}

		if (n <= phase) {
			return n == phase;
		}

		float fraction = n == CollectorRules.PHASE_TWO ? b.phase2At : CollectorRules.phase3At(unchained, b);
		vasch.setHealthUnlocked(fraction * vasch.getMaxHealth());
		pendingFloor = false;
		beginTransition(n);
		return true;
	}

	// ---------------------------------------------------------------- phase 2: pylons, ward, sentinels

	private void tickPylons(CollectorBalance b) {
		OrrenVasch v = vasch;

		if (v == null) {
			return;
		}

		boolean anotherSinging;

		for (Pylon pylon : pylons) {
			switch (pylon.crystal) {
				case SILENCED, EMPTY -> {
					anotherSinging = singingCount() > 0;

					if (pylon.silencedAt != NEVER && CollectorRules.mayResing(now, pylon.silencedAt, pylon.resung, anotherSinging, wardDown, b)) {
						regrow(pylon, v, b);
					}
				}
				case REGROWING -> {
					if (now - pylon.regrowStart >= b.regrowTellTicks) {
						pylon.resung++;
						sing(pylon, v, b);
						EffectEvents.around(level, BossEffects.PYLON_STATE, crystalCentre(pylon), null, v.getId(), pylon.resonance, BossEffects.PYLON_RESUNG);
					}
				}
				default -> {
				}
			}
		}

		if (unsungUntil != NEVER && now >= unsungUntil) {
			unsungUntil = NEVER;
		}

		int singing = singingCount();

		if (singing != sentWard) {
			sendWard(v, singing);
		}
	}

	/** A pylon wakes (or re-sings): full resonance for the participant count, a fresh focus, currents to him. */
	private void sing(Pylon pylon, OrrenVasch v, CollectorBalance b) {
		pylon.max = CollectorRules.pylonResonance(participants.size(), b);
		pylon.resonance = pylon.max;
		pylon.regrowStart = NEVER;
		pylon.silencedAt = NEVER;
		setCrystal(pylon, PylonCrystal.SINGING);
		ensureFocus(pylon);
		EffectEvents.around(level, BossEffects.PYLON_STATE, crystalCentre(pylon), null, v.getId(), pylon.resonance, BossEffects.PYLON_SING);
	}

	private void regrow(Pylon pylon, OrrenVasch v, CollectorBalance b) {
		pylon.regrowStart = now;
		setCrystal(pylon, PylonCrystal.REGROWING);
		ensureFocus(pylon);
		EffectEvents.around(level, BossEffects.PYLON_STATE, crystalCentre(pylon), null, v.getId(), b.regrowTellTicks, BossEffects.PYLON_REGROW);
	}

	/** Silenced: cracked dark (or ripped out), focus gone, the re-sing clock restarts. */
	private void silence(Pylon pylon, boolean ripped) {
		pylon.resonance = 0f;
		pylon.silencedAt = now;
		pylon.regrowStart = NEVER;
		setCrystal(pylon, ripped ? PylonCrystal.EMPTY : PylonCrystal.SILENCED);
		discardFocus(pylon);
		EffectEvents.around(level, BossEffects.PYLON_STATE, crystalCentre(pylon), null, vasch == null ? -1 : vasch.getId(), ripped ? 1f : 0f,
				BossEffects.PYLON_SILENCED);
		checkUnsung();
	}

	private void shatter(Pylon pylon, OrrenVasch v) {
		pylon.resonance = 0f;
		pylon.regrowStart = NEVER;
		pylon.silencedAt = NEVER;
		setCrystal(pylon, PylonCrystal.EMPTY);
		discardFocus(pylon);
		EffectEvents.around(level, BossEffects.PYLON_STATE, crystalCentre(pylon), null, v.getId(), 1f, BossEffects.PYLON_SHATTER);
	}

	/** The last pylon fell silent: Unsung (8.3), then the ward stays down for the phase. */
	private void checkUnsung() {
		OrrenVasch v = vasch;

		if (v == null || phase != CollectorRules.PHASE_TWO || stage != Stage.FIGHTING || wardDown || singingCount() > 0) {
			return;
		}

		CollectorBalance b = CollectorBalance.get();
		wardDown = true;
		unsungUntil = now + b.unsungTicks;

		for (Pylon pylon : pylons) {
			if (pylon.crystal == PylonCrystal.REGROWING) {
				pylon.regrowStart = NEVER;
				setCrystal(pylon, PylonCrystal.SILENCED);
				discardFocus(pylon);
			}
		}

		CollectorCombatGoal combat = v.combat();

		if (combat != null) {
			combat.abortMove(false);
		}

		NpcGuard.breakGuard(v, b.unsungTicks);
		Vec3 off = v.position().subtract(layout.daisFeet()).multiply(1, 0, 1);
		Vec3 toHall = Vec3.atBottomCenterOf(layout.threshold()).subtract(layout.daisFeet()).multiply(1, 0, 1);
		Vec3 shove = off.lengthSqr() > 0.25 ? off : toHall;

		if (shove.lengthSqr() > 1.0e-4) {
			shove = shove.normalize().scale(UNSUNG_SHOVE);
			v.setDeltaMovement(shove.x, 0.35, shove.z);
			v.hurtMarked = true;
		}

		EffectEvents.toTracking(v, BossEffects.WARD, v.position(), null, v.getId(), 0f, BossEffects.WARD_UNSUNG);
		sentWard = 0;
	}

	/** A source took {@code loss} resonance off the focus's pylon (8.3); a regrowing crystal is cut back. */
	void damagePylon(PylonFocus focus, float loss, @Nullable Entity source) {
		if (!ownsFocus(focus) || phase != CollectorRules.PHASE_TWO || stage != Stage.FIGHTING || loss <= 0f) {
			return;
		}

		Pylon pylon = pylons.get(focus.index());

		if (pylon.crystal == PylonCrystal.REGROWING) {
			silence(pylon, false);
			return;
		}

		if (pylon.crystal != PylonCrystal.SINGING) {
			return;
		}

		pylon.resonance = Math.max(0f, pylon.resonance - loss);

		if (pylon.resonance <= 0f) {
			silence(pylon, false);
		} else {
			EffectEvents.around(level, BossEffects.PYLON_STATE, crystalCentre(pylon), null, vasch == null ? -1 : vasch.getId(), pylon.resonance,
					BossEffects.PYLON_HIT);
		}
	}

	/** Pull ripped the crystal out at once. */
	void ripPylon(PylonFocus focus, ServerPlayer caster) {
		if (ownsFocus(focus) && phase == CollectorRules.PHASE_TWO && stage == Stage.FIGHTING) {
			silence(pylons.get(focus.index()), true);
		}
	}

	/** Lift's channel silenced the crystal. */
	void silencePylon(PylonFocus focus, ServerPlayer caster) {
		if (ownsFocus(focus) && phase == CollectorRules.PHASE_TWO && stage == Stage.FIGHTING) {
			silence(pylons.get(focus.index()), false);
		}
	}

	private void ensureFocus(Pylon pylon) {
		if (pylon.focus != null && !pylon.focus.isRemoved()) {
			return;
		}

		PylonFocus focus = PylonFocus.create(level, this, pylon.index, pylon.pos);
		pylon.focus = focus;
		level.addFreshEntity(focus);
	}

	private void discardFocus(Pylon pylon) {
		PylonFocus focus = pylon.focus;
		pylon.focus = null;

		if (focus != null && !focus.isRemoved()) {
			focus.discard();
		}
	}

	private void setCrystal(Pylon pylon, PylonCrystal crystal) {
		pylon.crystal = crystal;

		if (!level.isLoaded(pylon.pos)) {
			// Unloading (or never loaded): the next session's syncBlocks puts the block right.
			return;
		}

		BlockState state = level.getBlockState(pylon.pos);

		if (state.getBlock() instanceof ResonancePylonBlock && state.getValue(ResonancePylonBlock.CRYSTAL) != crystal) {
			level.setBlock(pylon.pos, state.setValue(ResonancePylonBlock.CRYSTAL, crystal), Block.UPDATE_ALL);
		}
	}

	private Vec3 crystalCentre(Pylon pylon) {
		return Vec3.atBottomCenterOf(pylon.pos).add(0, PylonFocus.CRYSTAL_HEIGHT + 0.4, 0);
	}

	private void sendWard(OrrenVasch v, int singing) {
		sentWard = singing;
		EffectEvents.toTracking(v, BossEffects.WARD, v.position(), null, v.getId(), wardDown ? 0 : singing, BossEffects.WARD_LEVEL);
	}

	/** Two bound Watchers, +1 per extra participant, at most {@code sentinelsMax}, in the gallery alcoves (8.3). */
	private void spawnSentinels(CollectorBalance b) {
		List<BlockPos> alcoves = layout.sentinels();

		if (alcoves.isEmpty()) {
			return;
		}

		int count = CollectorRules.sentinelCount(participants.size(), b);

		for (int i = 0; i < count; i++) {
			RuinSentinel sentinel = Sentinels.spawnArchive(level, alcoves.get(i % alcoves.size()), owner);

			if (sentinel != null) {
				sentinels.add(sentinel);
			}
		}
	}

	private void discardSentinels() {
		for (RuinSentinel sentinel : sentinels) {
			if (!sentinel.isRemoved()) {
				sentinel.discard();
			}
		}

		sentinels.clear();
	}

	private void resetPylons() {
		for (Pylon pylon : pylons) {
			discardFocus(pylon);
			pylon.resonance = 0f;
			pylon.silencedAt = NEVER;
			pylon.regrowStart = NEVER;
			pylon.resung = 0;
			setCrystal(pylon, PylonCrystal.DORMANT);
		}

		wardDown = false;
		unsungUntil = NEVER;
		sentWard = -1;
	}

	// ---------------------------------------------------------------- yield, resolve, echo end

	/** He yields at 10% (or kneels at once in Peaceful): the heart becomes YIELDED, which persists (8.3, 8.6). */
	public void yieldNow() {
		OrrenVasch v = vasch;

		if (v == null || stage == Stage.YIELDED || stage == Stage.RESOLVING || stage == Stage.ENDED) {
			return;
		}

		CollectorCombatGoal combat = v.combat();

		if (combat != null) {
			combat.abortMove(true);
			combat.clearRidges();
		}

		v.clearSpent();
		discardSentinels();

		for (Pylon pylon : pylons) {
			discardFocus(pylon);
		}

		kneel();
		step(HeartRules.Event.YIELD);
		setStage(Stage.YIELDED);
		EffectEvents.toTracking(v, BossEffects.YIELD, v.position(), null, v.getId(), 1f, 0);

		for (Participant participant : participants.values()) {
			ServerPlayer player = player(participant.id);

			if (player != null) {
				sendPhase(player, BossEffects.PHASE_YIELD, 1f);
				participant.music = false;
			}
		}

		removeBar();
		say("yield");

		ServerPlayer ownerPlayer = owner == null ? null : player(owner);

		for (UUID id : participants.keySet()) {
			ServerPlayer helper = player(id);

			if (helper == null || id.equals(owner)) {
				continue;
			}

			ChapterThreeStage helperStage = ChapterThree.stage(helper);

			if (helperStage == ChapterThreeStage.RELIQUARY || helperStage == ChapterThreeStage.CONFRONTATION) {
				stanceCredited.add(id);
				Dialogues.open(helper, STANCE_DIALOGUE, speakerFor(helper));
			}
		}

		if (ownerPlayer != null) {
			notifyYieldIfDue();
			Dialogues.open(ownerPlayer, OrrenVasch.YIELD_DIALOGUE, speakerFor(ownerPlayer));
		}
	}

	/** Runs the owner's {@code onBossYielded} once per yield, when the owner is here (8.6: a yield reached is saved). */
	private void notifyYieldIfDue() {
		if (owner == null) {
			return;
		}

		ReliquaryHeartBlockEntity entity = heartEntity();

		if (entity != null && entity.yieldNotified()) {
			return;
		}

		ServerPlayer ownerPlayer = player(owner);

		if (ownerPlayer == null) {
			return;
		}

		if (entity != null) {
			entity.setYieldNotified(true);
		} else if (yieldNotifiedNoHeart) {
			return;
		}

		yieldNotifiedNoHeart = true;
		hooks.onBossYielded(ownerPlayer, BossArenas.COLLECTOR);
	}

	/** He kneels where he is (at the dais when he is outside the hall), blade out, guard down. */
	private void kneel() {
		OrrenVasch v = vasch;

		if (v == null) {
			return;
		}

		if (!layout.isInHall(v)) {
			v.teleportTo(layout.daisFeet().x, layout.daisFeet().y, layout.daisFeet().z);
		}

		CollectorBalance b = CollectorBalance.get();
		float floor = b.yieldAt * v.getMaxHealth();

		if (v.getHealth() < floor) {
			v.setHealthUnlocked(floor);
		}

		v.setYielded(true);
	}

	/**
	 * The owner chose (8.7): the heart becomes RESOLVED, the vault opens, the scene plays, and
	 * {@code onBossDefeated} runs once per participant (the owner always) with {@code firstClear}.
	 * Returns false when the session is not waiting on the owner's choice.
	 */
	public boolean resolve(BossOutcome outcome) {
		OrrenVasch v = vasch;

		if (stage != Stage.YIELDED || v == null || outcome == BossOutcome.ECHO) {
			return false;
		}

		ReliquaryHeartBlockEntity entity = heartEntity();
		boolean firstClear = entity == null || !entity.firstClear();

		if (entity != null) {
			entity.setFirstClear(true);
			entity.setYieldNotified(false);
		}

		step(HeartRules.Event.RESOLVE);
		openVault();
		setDoor(true);
		setStage(Stage.RESOLVING);
		ServerPlayer ownerPlayer = owner == null ? null : player(owner);
		Path path = ownerPlayer == null ? Path.NONE : Identity.path(ownerPlayer);
		scene = CollectorScenes.start(this, v, outcome, path);

		Set<UUID> credited = new LinkedHashSet<>();

		if (owner != null) {
			credited.add(owner);
		}

		credited.addAll(participants.keySet());

		for (UUID id : credited) {
			ServerPlayer player = player(id);

			if (player == null && id.equals(owner)) {
				player = level.getServer().getPlayerList().getPlayer(id);
			}

			if (player != null && owner != null) {
				hooks.onBossDefeated(player, BossArenas.COLLECTOR, outcome, owner, firstClear);
			}
		}

		return true;
	}

	/** Opens the vault seal and lets the hoard be seen (8.2, {@code hoard_open}). */
	void openVault() {
		boolean opened = false;

		for (BlockPos pos : layout.vaultSeals()) {
			if (!level.isLoaded(pos)) {
				continue;
			}

			BlockState state = level.getBlockState(pos);

			if (state.getBlock() instanceof ReliquarySealBlock && !state.getValue(ReliquarySealBlock.OPEN)) {
				level.setBlock(pos, state.setValue(ReliquarySealBlock.OPEN, true), Block.UPDATE_ALL);
				opened = true;
			}
		}

		if (opened || layout.vaultSeals().isEmpty()) {
			BlockPos at = layout.plinth() != null ? layout.plinth() : layout.vaultSeals().isEmpty() ? layout.dais() : layout.vaultSeals().get(0);
			// dir points from the hoard to the vault seal, so the client crumbles the seal and lifts the motes at the hoard.
			Vec3 toSeal = layout.vaultSeals().isEmpty() ? null : Vec3.atCenterOf(layout.vaultSeals().get(0)).subtract(Vec3.atCenterOf(at));
			EffectEvents.around(level, BossEffects.HOARD_OPEN, Vec3.atCenterOf(at), toSeal, -1, 1f, 0);
		}
	}

	/** The echo reached the floor of phase 3: it dissolves into smoke and everyone listed is credited (8.9). */
	private void dissolveEcho() {
		OrrenVasch v = vasch;

		if (v == null) {
			return;
		}

		CollectorCombatGoal combat = v.combat();

		if (combat != null) {
			combat.abortMove(true);
			combat.clearRidges();
		}

		discardSentinels();

		for (Pylon pylon : pylons) {
			discardFocus(pylon);
		}

		v.setTarget(null);
		v.getNavigation().stop();
		v.setBladeLit(false);
		v.setForming(true);
		setStage(Stage.RESOLVING);
		EffectEvents.toTracking(v, BossEffects.ECHO, v.position(), null, v.getId(), DISSOLVE_TICKS, BossEffects.ECHO_DISSOLVE);

		for (Participant participant : participants.values()) {
			ServerPlayer player = player(participant.id);

			if (player != null) {
				sendPhase(player, BossEffects.PHASE_VICTORY, 1f);
				participant.music = false;
			}
		}

		removeBar();
		ReliquaryHeartBlockEntity entity = heartEntity();

		if (entity != null) {
			entity.addEchoClear();
		}

		echoCleared = true;

		for (UUID id : participants.keySet()) {
			ServerPlayer player = player(id);

			if (player != null && owner != null) {
				hooks.onBossDefeated(player, BossArenas.COLLECTOR, BossOutcome.ECHO, owner, false);
			}
		}

		scene = CollectorScenes.dissolve(v, now + DISSOLVE_TICKS);
	}

	/** Whether this echo session was won (its first clear unlocks Unchained even without a heart entity). */
	public boolean echoCleared() {
		return echoCleared;
	}

	// ---------------------------------------------------------------- reset and end

	/**
	 * The fight resets (8.6): he returns to the lectern at full health, pylons reset, sentinels go,
	 * items in the hall move to the antechamber floor, the seal opens and the heart is READY again; the
	 * next attempt starts at the dais. An echo simply ends.
	 */
	public void reset() {
		OrrenVasch v = vasch;

		if (stage == Stage.YIELDED || stage == Stage.RESOLVING || stage == Stage.ENDED) {
			return;
		}

		CollectorBalance b = CollectorBalance.get();

		if (v != null) {
			CollectorCombatGoal combat = v.combat();

			if (combat != null) {
				combat.abortMove(true);
				combat.clearRidges();
			}
		}

		for (Participant participant : participants.values()) {
			ServerPlayer player = player(participant.id);

			if (player != null) {
				if (Holds.isHeld(player)) {
					Holds.release(player, "reset");
				}

				sendPhase(player, BossEffects.PHASE_RESET, 1f);
			}
		}

		removeBar();
		discardSentinels();
		discardDebris();
		resetPylons();
		moveItemsToAntechamber();
		participants.clear();
		threat = null;
		specialSwitchAt = NEVER;
		noneInsideSince = NEVER;
		pendingFloor = false;
		crossedAt = NEVER;
		phase = CollectorRules.PHASE_ONE;

		if (echo) {
			if (v != null) {
				EffectEvents.toTracking(v, BossEffects.ECHO, v.position(), null, v.getId(), DISSOLVE_TICKS, BossEffects.ECHO_DISSOLVE);
			}

			end();
			return;
		}

		if (v != null) {
			EffectEvents.toTracking(v, BossEffects.LEASH, v.position(), null, v.getId(), 1f, BossEffects.LEASH_VANISH);
			v.clearSpent();
			v.setTarget(null);
			v.getNavigation().stop();
			v.setPhase(phase);
			v.setMaxHealth(CollectorRules.maxHealth(1, false, b), false);
			v.setGuardState(GuardState.READY);
			v.setBladeLit(false);
			Vec3 lectern = layout.lecternFeet();
			v.teleportTo(lectern.x, lectern.y, lectern.z);
			v.setDeltaMovement(Vec3.ZERO);
			v.setAction(OrrenVasch.Action.READING);
			EffectEvents.toTracking(v, BossEffects.LEASH, lectern, null, v.getId(), 1f, BossEffects.LEASH_REAPPEAR);
		}

		step(HeartRules.Event.RESET);
		setStage(Stage.WAITING);
	}

	/** Item entities in the hall box move to the antechamber floor, keeping their owner and pickup rules (8.6). */
	private void moveItemsToAntechamber() {
		Vec3 floor = Vec3.atBottomCenterOf(layout.antechamberFloor()).add(0, 0.2, 0);

		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, AABB.of(layout.reliquary()), e -> layout.isInHall(e.position()))) {
			// API-CHECK: Entity#teleportTo(double, double, double) on an ItemEntity changes only its position (owner and pickup delay kept).
			item.teleportTo(floor.x, floor.y, floor.z);
			item.setDeltaMovement(Vec3.ZERO);
		}
	}

	private void discardDebris() {
		for (ArchiveDebris debris : level.getEntitiesOfClass(ArchiveDebris.class, AABB.of(layout.reliquary()), e -> true)) {
			debris.discard();
		}
	}

	/** Ends the session: Vasch, foci and sentinels go, the bar is cleared, the door opens. BossArenas drops it. */
	public void end() {
		if (stage == Stage.ENDED) {
			return;
		}

		for (Participant participant : participants.values()) {
			ServerPlayer player = player(participant.id);

			if (player != null) {
				if (participant.music) {
					sendPhase(player, BossEffects.PHASE_MUSIC_STOP, 1f);
				}

				if (Holds.isHeld(player) && isFighting()) {
					Holds.release(player, "reset");
				}
			}
		}

		ServerPlayer ownerPlayer = owner == null ? null : player(owner);

		if (ownerPlayer != null && stage == Stage.MEETING) {
			closeIfOpen(ownerPlayer, MEET_DIALOGUE);
		}

		removeBar();
		discardSentinels();
		discardDebris();

		for (Pylon pylon : pylons) {
			discardFocus(pylon);
		}

		if (isFighting() || stage == Stage.MEETING || stage == Stage.FORMING) {
			resetPylons();

			if (!echo) {
				step(HeartRules.Event.RESET);
			}
		}

		OrrenVasch v = vasch;
		vasch = null;
		stage = Stage.ENDED;

		if (v != null && !v.isRemoved()) {
			v.discard();
		}

		setDoor(true);
	}

	private void removeBar() {
		if (bar != null) {
			bar.removeAllPlayers();
			bar = null;
		}
	}

	// ---------------------------------------------------------------- seals

	private void tickDoorSeal(CollectorBalance b) {
		boolean fight = stage == Stage.MEETING || stage == Stage.FORMING || stage == Stage.FIGHTING || stage == Stage.TRANSITION;

		if (!fight) {
			setDoor(true);
			return;
		}

		if (doorOpen) {
			for (Participant participant : participants.values()) {
				ServerPlayer player = player(participant.id);

				// Someone listed is still coming back through: keep the clock from closing on them.
				if (player != null && player.isAlive() && layout.isInAntechamber(player.position()) && nearThreshold(player)) {
					crossedAt = now;
				}
			}

			if (crossedAt != NEVER && HeartRules.sealCloses(now - crossedAt, doorwayOccupied(), b)) {
				setDoor(false);
			}

			return;
		}

		// A listed participant coming back from the antechamber reopens it (and only for them).
		for (Participant participant : participants.values()) {
			ServerPlayer player = player(participant.id);

			if (player != null && player.isAlive() && layout.isInAntechamber(player.position()) && nearThreshold(player)) {
				letThrough(player);
				return;
			}
		}
	}

	/** A listed participant at the closed doorway seal: it opens and closes behind them (8.3). */
	boolean letThrough(ServerPlayer player) {
		if (!participants.containsKey(player.getUUID()) || !(isFighting() || stage == Stage.FORMING)) {
			return false;
		}

		crossedAt = now;
		setDoor(true);
		return true;
	}

	private boolean nearThreshold(ServerPlayer player) {
		return player.position().distanceToSqr(Vec3.atBottomCenterOf(layout.threshold())) <= RETURN_REACH * RETURN_REACH;
	}

	private boolean doorwayOccupied() {
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && layout.isInDoorway(player.position())) {
				return true;
			}
		}

		return false;
	}

	/** Whether the doorway seal is open now. */
	public boolean isDoorOpen() {
		return doorOpen;
	}

	private void setDoor(boolean open) {
		if (doorOpen == open && stage != Stage.ENDED) {
			return;
		}

		doorOpen = open;
		writeDoor(open);
	}

	/** Writes every doorway seal block in a loaded chunk to {@code open}, whatever this session last set. */
	private void writeDoor(boolean open) {
		for (BlockPos pos : layout.doorSeals()) {
			if (!level.isLoaded(pos)) {
				continue;
			}

			BlockState state = level.getBlockState(pos);

			if (state.getBlock() instanceof ReliquarySealBlock && state.getValue(ReliquarySealBlock.OPEN) != open) {
				level.setBlock(pos, state.setValue(ReliquarySealBlock.OPEN, open), Block.UPDATE_ALL);
			}
		}
	}

	/**
	 * A new session starts from a quiet hall, whatever the blocks were saved as (a crash, a kill or an
	 * unload mid-fight whose writes were lost): every doorway seal open and every crystal dormant.
	 */
	private void syncBlocks() {
		doorOpen = true;
		writeDoor(true);
		resetPylons();
	}

	// ---------------------------------------------------------------- interactions

	/**
	 * A player used Vasch (from {@link OrrenVasch#mobInteract}): a later attempt starts ("Face me."); the
	 * owner reopens the yield choice, a credited helper their stance. Returns whether it was handled.
	 */
	boolean onVaschUsed(ServerPlayer player) {
		switch (stage) {
			case WAITING -> {
				if (attempts > 0 && eligible(player) && layout.isInHall(player)) {
					startFight("face_me");
					return true;
				}

				return false;
			}
			case YIELDED -> {
				if (player.getUUID().equals(owner)) {
					Dialogues.open(player, OrrenVasch.YIELD_DIALOGUE, speakerFor(player));
				} else if (stanceCredited.contains(player.getUUID())) {
					Dialogues.open(player, STANCE_DIALOGUE, speakerFor(player));
				} else {
					player.sendOverlayMessage(Component.translatable("boss.oldrepublic.collector.line.kneeling"));
				}

				return true;
			}
			default -> {
				return false;
			}
		}
	}

	/** A player used the lectern in the hall: a later attempt starts ("Face me."). */
	boolean onLecternUsed(ServerPlayer player) {
		if (stage == Stage.WAITING && attempts > 0 && eligible(player) && layout.isInHall(player)) {
			startFight("face_me");
			return true;
		}

		return false;
	}

	/** A helper credited with the stance dialogue at this yield (8.9). */
	public boolean isStanceCredited(UUID id) {
		return stanceCredited.contains(id);
	}

	// ---------------------------------------------------------------- spawning and helpers

	/** Vasch at the lectern, reading, blade unlit (READY). */
	@Nullable OrrenVasch spawnAtLectern() {
		OrrenVasch v = spawnVasch(layout.lecternFeet(), facing(layout.lecternFeet(), Vec3.atBottomCenterOf(layout.threshold())));

		if (v != null) {
			v.setAction(OrrenVasch.Action.READING);
		}

		return v;
	}

	/** After a restart with the heart YIELDED: he kneels at the dais until the owner chooses (8.6). */
	void spawnKneeling() {
		syncBlocks();
		vasch = spawnVasch(layout.daisFeet(), facingFromDais());

		if (owner != null) {
			participants.computeIfAbsent(owner, Participant::new);
		}

		kneel();
		setStage(Stage.YIELDED);
	}

	/** Spawns the session's Vasch at the lectern (a fresh READY session). */
	void spawnReady() {
		syncBlocks();
		vasch = spawnAtLectern();
		setStage(Stage.WAITING);
	}

	private @Nullable OrrenVasch spawnVasch(Vec3 at, float yaw) {
		OrrenVasch v = BossEntities.ORREN_VASCH.create(level, EntitySpawnReason.EVENT);

		if (v == null) {
			return null;
		}

		v.snapTo(at.x, at.y, at.z, yaw, 0f);
		v.setYHeadRot(yaw);
		v.setYBodyRot(yaw);
		v.bind(this);
		v.setEcho(echo);
		v.setMaxHealth(CollectorRules.maxHealth(1, unchained, CollectorBalance.get()), false);
		// Added before anything can mark him yielded, so the identity feature never tracks him (plan 5).
		level.addFreshEntity(v);
		return v;
	}

	private float facingFromDais() {
		return facing(layout.daisFeet(), Vec3.atBottomCenterOf(layout.threshold()));
	}

	private static float facing(Vec3 from, Vec3 to) {
		double dx = to.x - from.x;
		double dz = to.z - from.z;
		return (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
	}

	/** Awakened, alive, not a spectator: who may take part (8.3). */
	private static boolean eligible(ServerPlayer player) {
		return player.isAlive() && !player.isSpectator() && Progression.get(player).isAwakened();
	}

	private @Nullable ServerPlayer player(UUID id) {
		for (ServerPlayer player : level.players()) {
			if (player.getUUID().equals(id)) {
				return player;
			}
		}

		return null;
	}

	/** Vasch speaks to {@code player}'s dialogue when close enough to be faced, else the dialogue has no speaker. */
	private @Nullable Entity speakerFor(ServerPlayer player) {
		OrrenVasch v = vasch;
		return v != null && v.distanceTo(player) <= Identity.balance().dialogueEndDistance - 0.5 ? v : null;
	}

	private static void closeIfOpen(ServerPlayer player, Identifier dialogue) {
		DialogueSession open = Dialogues.session(player);

		if (open != null && dialogue.equals(open.dialogue())) {
			Dialogues.close(player);
		}
	}

	/** One of his lines, as overlay text to everyone in the Reliquary (8.1: subtitled). */
	void say(String line) {
		Component text = Component.translatable("boss.oldrepublic.collector.line." + line);

		for (ServerPlayer player : level.players()) {
			if (layout.isInReliquary(player.position())) {
				player.sendOverlayMessage(text);
			}
		}
	}

	private void sendPhase(ServerPlayer player, int variant, float intensity) {
		OrrenVasch v = vasch;
		Vec3 at = v != null ? v.position() : layout.daisFeet();
		EffectEvents.toPlayer(player, BossEffects.PHASE, at, null, v != null ? v.getId() : -1, intensity, variant);
		// The music (spec MILESTONE_5.md 12.5), mapped as the M3 client did: one loop per phase, and the
		// yield, reset, victory and music stop fade it out.
		int track = switch (variant) {
			case BossEffects.PHASE_START -> 1;
			case BossEffects.PHASE_TRANSITION_ONE -> 2;
			case BossEffects.PHASE_TRANSITION_TWO -> 3;
			default -> BossMusicPayload.STOP;
		};
		BossMusicPayload.send(player, BossMusicPayload.COLLECTOR, track);
	}

	/** The {@code boss_phase} variant that names the current phase's music (0, 1, 2). */
	private int phaseVariant() {
		return switch (phase) {
			case CollectorRules.PHASE_TWO -> BossEffects.PHASE_TRANSITION_ONE;
			case CollectorRules.PHASE_THREE -> BossEffects.PHASE_TRANSITION_TWO;
			default -> BossEffects.PHASE_START;
		};
	}

	private Component barName(int forPhase) {
		if (echo) {
			return Component.translatable(unchained ? "boss.oldrepublic.collector.bar.echo_unchained" : "boss.oldrepublic.collector.bar.echo");
		}

		return Component.translatable(switch (forPhase) {
			case CollectorRules.PHASE_TWO -> "boss.oldrepublic.collector.bar.singing_wall";
			case CollectorRules.PHASE_THREE -> "boss.oldrepublic.collector.bar.unkept";
			default -> "boss.oldrepublic.collector.bar.archivist";
		});
	}

	private void setStage(Stage next) {
		stage = next;
		stageStart = now;
	}

	/** Moves the heart's state by {@code event} and writes it through to the block entity. */
	private void step(HeartRules.Event event) {
		heartState = HeartRules.step(heartState, event);
		ReliquaryHeartBlockEntity entity = heartEntity();

		if (entity != null) {
			entity.setState(heartState);
		}
	}

	@Nullable ReliquaryHeartBlockEntity heartEntity() {
		return level.isLoaded(heart) && level.getBlockEntity(heart) instanceof ReliquaryHeartBlockEntity entity ? entity : null;
	}
}
