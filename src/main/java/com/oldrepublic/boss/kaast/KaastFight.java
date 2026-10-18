package com.oldrepublic.boss.kaast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.planet.Clocks;
import com.oldrepublic.quest.BossOutcome;
import com.oldrepublic.quest.QuestHooks;
import com.oldrepublic.quest.story.ChapterFourRules;
import com.oldrepublic.quest.story.ChapterFourStage;
import com.oldrepublic.quest.story.KaastOutcome;
import com.oldrepublic.quest.story.Story;
import com.oldrepublic.quest.story.StoryData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.phys.Vec3;

/**
 * The Kaast fights (spec MILESTONE_4.md 8.6, 10.5, 10.6): one {@link KaastSession} per owner, in
 * memory only. The owner in the ring of their camp during the {@code DUEL} stage starts the fight
 * (W2 moves the stage when the ring is entered); a yielded duel after a restart brings him back
 * knelt; after the chapter the owner calls the daily hunter's echo at the camp gate. W2's
 * {@code kaast:resolve} ends the duel through {@link #resolve}; W2 pays every reward through the
 * boss hooks the sessions fire.
 */
public final class KaastFight {
	/** The boss id the quest hooks carry (W2 routes ids starting with {@code kaast}). */
	public static final String BOSS_ID = "kaast";
	/** {@code StoryData.Chapter4.Duel.boss}: none, yielded, resolved (W2's 12.1 values). */
	private static final int DUEL_NONE = 0;
	private static final int DUEL_YIELDED = 1;
	/** Ticks between the checks for owners entering their camps. */
	private static final int START_CHECK_TICKS = 20;

	private static final Map<UUID, KaastSession> SESSIONS = new HashMap<>();

	/**
	 * Resolves the owner's yielded duel with {@code outcome} (W2's {@code kaast:resolve} dialogue
	 * action calls it): the scene's staging and the boss defeat hooks. W2 grants the rewards.
	 */
	public static void resolve(ServerPlayer owner, KaastOutcome outcome) {
		KaastSession session = SESSIONS.get(owner.getUUID());

		if (session != null && !session.isEcho() && !session.isOver()) {
			session.resolve(outcome);
			return;
		}

		// No fight running (Peaceful, or the camp unloaded): the owner's first clear still goes through the hook.
		QuestHooks.onBossDefeated(owner, BOSS_ID, outcome == KaastOutcome.ENDED ? BossOutcome.EXECUTED : outcome == KaastOutcome.STRIPPED
				? BossOutcome.RELEASED : BossOutcome.SPARED, owner.getUUID(), true);
	}

	/** The running fight of {@code owner}, or null. */
	public static @Nullable KaastSession session(UUID owner) {
		KaastSession session = SESSIONS.get(owner);
		return session == null || session.isOver() ? null : session;
	}

	/** The running fight whose ring holds {@code pos} in {@code level}, or null. */
	public static @Nullable KaastSession sessionAt(ServerLevel level, BlockPos pos) {
		for (KaastSession session : SESSIONS.values()) {
			if (!session.isOver() && session.level() == level && session.layout().inRing(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5)) {
				return session;
			}
		}

		return null;
	}

	/**
	 * Starts a fight for {@code owner} in the camp laid out by {@code layout} (the server's own
	 * start, the gate's echo, the tests). {@code knelt} brings Kaast back already yielded.
	 *
	 * @return the session, or null when Kaast could not spawn or a fight already runs
	 */
	public static @Nullable KaastSession begin(UUID owner, ServerLevel level, CampLayout layout, boolean echo, boolean knelt) {
		KaastSession running = session(owner);

		if (running != null) {
			return null;
		}

		HuntingCamps.ensureGate(level, layout);
		KaastSession session = new KaastSession(owner, level, layout, echo);

		if (!session.start(knelt)) {
			return null;
		}

		SESSIONS.put(owner, session);
		return session;
	}

	/** Every server tick: the sessions; every second, owners entering their camps. */
	public static void tick(MinecraftServer server) {
		for (KaastSession session : new ArrayList<>(SESSIONS.values())) {
			session.tick();
		}

		SESSIONS.values().removeIf(KaastSession::isOver);

		if (server.getTickCount() % START_CHECK_TICKS == 0) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				checkStart(player);
			}
		}
	}

	/** The owner in their camp's ring during DUEL: the fight (or, already yielded, Kaast knelt). */
	private static void checkStart(ServerPlayer player) {
		if (SESSIONS.containsKey(player.getUUID()) || player.isSpectator()) {
			return;
		}

		StoryData data = Story.get(player);
		StoryData.Chapter4.Duel duel = data.chapter4.duel();
		GlobalPos camp = duel.camp();

		if (data.chapter4.stage() != ChapterFourStage.DUEL || camp == null || !player.level().dimension().equals(camp.dimension())
				|| duel.boss() != DUEL_NONE && duel.boss() != DUEL_YIELDED) {
			return;
		}

		ServerLevel level = player.level();
		double radius = KaastBalance.get().arenaRadius;

		if (player.position().distanceToSqr(Vec3.atCenterOf(camp.pos())) > radius * radius || level.getDifficulty() == Difficulty.PEACEFUL) {
			return;
		}

		CampLayout layout = CampLayout.of(level, camp.pos(), radius);

		if (layout.inRing(player.getX(), player.getY(), player.getZ())) {
			begin(player.getUUID(), level, layout, false, duel.boss() == DUEL_YIELDED);
		}
	}

	/**
	 * Someone used the camp gate. Closed: a listed participant is let through, anyone else refused.
	 * Open: the owner, after the chapter, calls the hunter's echo once per in-game day.
	 */
	static void onGateUsed(ServerLevel level, BlockPos pos, ServerPlayer player, boolean open) {
		KaastSession running = sessionAt(level, pos);

		if (!open) {
			if (running != null && running.isParticipant(player.getUUID())) {
				letThrough(running, pos, player);
			} else {
				player.sendOverlayMessage(Component.translatable("boss.oldrepublic.kaast.gate_closed"));
			}

			return;
		}

		StoryData data = Story.get(player);
		GlobalPos camp = data.chapter4.duel().camp();

		if (camp == null || !camp.dimension().equals(level.dimension()) || camp.pos().distSqr(pos) > 48 * 48) {
			player.sendOverlayMessage(Component.translatable("boss.oldrepublic.kaast.gate_not_yours"));
			return;
		}

		if (data.chapter4.stage() != ChapterFourStage.COMPLETE || running != null) {
			player.sendOverlayMessage(Component.translatable("boss.oldrepublic.kaast.gate_quiet"));
			return;
		}

		if (!ChapterFourRules.rematchDue(data, Clocks.day(level.getServer())) || level.getDifficulty() == Difficulty.PEACEFUL) {
			player.sendOverlayMessage(Component.translatable("boss.oldrepublic.kaast.echo_done_today"));
			return;
		}

		if (begin(player.getUUID(), level, CampLayout.of(level, camp.pos(), KaastBalance.get().arenaRadius), true, false) != null) {
			player.sendSystemMessage(Component.translatable("boss.oldrepublic.kaast.echo_called"));
		}
	}

	/** Moves a participant to the other side of the closed gate. */
	private static void letThrough(KaastSession session, BlockPos gate, ServerPlayer player) {
		Vec3 centre = Vec3.atBottomCenterOf(gate);
		Vec3 radial = centre.subtract(Vec3.atBottomCenterOf(session.layout().heart())).multiply(1, 0, 1);
		Vec3 dir = radial.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : radial.normalize();
		boolean inside = player.position().subtract(centre).dot(dir) < 0;
		Vec3 to = centre.add(dir.scale(inside ? 1.6 : -1.6));
		player.teleportTo(to.x, gate.getY(), to.z);
	}

	/** {@code /oldrepublic kaast phase}: jumps the owner's running fight to {@code phase}. */
	public static boolean phase(ServerPlayer owner, int phase) {
		KaastSession session = session(owner.getUUID());

		if (session == null) {
			return false;
		}

		session.forcePhase(phase);
		return true;
	}

	/** {@code /oldrepublic c4 yield}: the owner's running fight ends in his yield, the fight cleared. False when none runs. */
	public static boolean forceYield(ServerPlayer owner) {
		KaastSession session = session(owner.getUUID());

		if (session == null) {
			return false;
		}

		session.forceYield();
		return true;
	}

	/** {@code /oldrepublic kaast reset}: the owner's camp resets as the 200-tick rule does. */
	public static boolean reset(ServerPlayer owner) {
		KaastSession session = session(owner.getUUID());

		if (session == null) {
			return false;
		}

		session.reset();
		SESSIONS.remove(owner.getUUID());
		return true;
	}

	/** Server stop: every fight ends (nothing of it is saved). */
	public static void clear() {
		for (KaastSession session : SESSIONS.values()) {
			session.reset();
		}

		SESSIONS.clear();
	}

	private KaastFight() {
	}
}
