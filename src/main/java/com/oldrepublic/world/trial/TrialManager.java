package com.oldrepublic.world.trial;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.QuestHooks;
import com.oldrepublic.quest.QuestStage;
import com.oldrepublic.world.WorldBalance;
import com.oldrepublic.world.WorldBlocks;
import com.oldrepublic.world.WorldEffects;
import com.oldrepublic.world.WorldEntities;
import com.oldrepublic.world.block.ResonantSealBlock;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side Trial of Reflection. A player starts it at a meditation stone; the chamber dims,
 * their Shadow Echo forms and fights them. Leaving the chamber, dying, logging out, changing
 * dimension or running out of time fails the trial cleanly (the echo fades, nothing is lost, the
 * stone can be used again). Defeating the echo completes it and forms the attuned crystal
 * ({@link CrystalGrant}). Sessions are in memory only; the echo is never saved.
 */
public final class TrialManager {
	private static final Map<UUID, TrialSession> SESSIONS = new HashMap<>();
	private static final int PICKUP_POLL_INTERVAL = 20;
	private static int ticks;

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(TrialManager::tick);
		ServerPlayerEvents.LEAVE.register(player -> {
			TrialSession session = SESSIONS.get(player.getUUID());

			if (session != null) {
				session.player = player;
				end(session, true);
			}

			ResonantSealBlock.forget(player.getUUID());
		});
		ServerPlayerEvents.JOIN.register(CrystalGrant::trySpawnPending);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				TrialSession session = SESSIONS.get(player.getUUID());

				if (session != null && session.phase != TrialSession.Phase.VICTORY) {
					fail(session, "message.oldrepublic.trial.failed_death");
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			// Echoes are not saved; a pending crystal is spawned on the owner's next visit.
			SESSIONS.clear();
		});
	}

	/** The meditation stone was used. Checks every requirement and gives feedback on refusal. */
	public static void tryStart(ServerPlayer player, BlockPos stone) {
		String refusal = refusal(player, stone);

		if (refusal != null) {
			ResonantSealBlock.refuse(player, stone, refusal);
			return;
		}

		start(player, stone, false);
	}

	private static @Nullable String refusal(ServerPlayer player, BlockPos stone) {
		if (player.isSpectator()) {
			return "message.oldrepublic.trial.refuse_spectator";
		}

		if (SESSIONS.containsKey(player.getUUID())) {
			return "message.oldrepublic.trial.already_running";
		}

		if (stoneBusy(player.level(), stone)) {
			return "message.oldrepublic.trial.busy";
		}

		return requirementRefusal(Progression.get(player), player.isCreative());
	}

	/**
	 * The progression rules for starting a trial: not yet completed, and (outside creative)
	 * awakened, Force Push learned and called to the cave. Null when allowed, else the
	 * translation key of the reason. Pure, so the game tests can check it.
	 */
	public static @Nullable String requirementRefusal(ForceData data, boolean creative) {
		if (data.hasFlag(CrystalGrant.TRIAL_COMPLETE)) {
			return "message.oldrepublic.trial.already_done";
		}

		if (creative) {
			return null;
		}

		if (!data.isAwakened()) {
			return "message.oldrepublic.trial.refuse_unawakened";
		}

		if (!data.hasAbility("force_push")) {
			return "message.oldrepublic.trial.refuse_no_push";
		}

		if (data.questStage < QuestStage.CALLING.ordinal()) {
			return "message.oldrepublic.trial.refuse_not_called";
		}

		return null;
	}

	/**
	 * Starts a trial without checking requirements (also used by {@code /oldrepublic trial start}).
	 * Returns false if the player is already in one or the stone is busy.
	 */
	public static boolean start(ServerPlayer player, BlockPos stone, boolean forced) {
		ServerLevel level = player.level();

		if (SESSIONS.containsKey(player.getUUID()) || stoneBusy(level, stone)) {
			return false;
		}

		TrialSession session = new TrialSession(player, level, stone, echoSpawn(level, stone, player.position()), forced);
		SESSIONS.put(player.getUUID(), session);
		WorldBalance b = WorldBalance.get();

		EffectEvents.around(level, WorldEffects.TRIAL_BEGIN, session.stoneTop(), null, player.getId(), (float) b.chamberRadius, 0);
		EffectEvents.around(level, WorldEffects.TRIAL_ECHO_FORM, session.echoSpawn, null, -1, Math.max(1, b.trialFormTicks) / 20f, 0);
		player.sendOverlayMessage(Component.translatable("message.oldrepublic.trial.begin"));
		QuestHooks.onTrialStarted(player);
		return true;
	}

	/** Fails the player's running trial (operator command). Returns false if none runs. */
	public static boolean stop(ServerPlayer player) {
		TrialSession session = SESSIONS.get(player.getUUID());

		if (session == null) {
			return false;
		}

		if (session.phase == TrialSession.Phase.VICTORY) {
			end(session, true);
		} else {
			fail(session, "message.oldrepublic.trial.failed_stopped");
		}

		return true;
	}

	public static boolean isInTrial(ServerPlayer player) {
		return SESSIONS.containsKey(player.getUUID());
	}

	/** Whether a running trial still claims this echo. Echoes that are not claimed discard themselves. */
	public static boolean isLiveEcho(ShadowEchoEntity echo) {
		UUID owner = echo.ownerId();
		TrialSession session = owner == null ? null : SESSIONS.get(owner);
		return session != null && echo.getUUID().equals(session.echoId) && session.level == echo.level();
	}

	static void onEchoDefeated(ShadowEchoEntity echo) {
		UUID owner = echo.ownerId();
		TrialSession session = owner == null ? null : SESSIONS.get(owner);

		if (session == null || session.phase != TrialSession.Phase.FIGHT || !echo.getUUID().equals(session.echoId)) {
			return;
		}

		ServerPlayer player = session.player;
		session.setPhase(TrialSession.Phase.VICTORY);
		boolean crystal = CrystalGrant.markPending(player, session.level, session.stone);

		EffectEvents.around(session.level, WorldEffects.TRIAL_ECHO_DISSOLVE, echo.position(), null, echo.getId(), 1f, WorldEffects.DISSOLVE_DEFEATED);
		EffectEvents.around(session.level, WorldEffects.TRIAL_COMPLETE, session.stoneTop(), null, player.getId(), 1f, WorldEffects.COMPLETE_SUCCESS);
		player.sendOverlayMessage(Component.translatable(crystal ? "message.oldrepublic.trial.complete" : "message.oldrepublic.trial.complete_again"));
		QuestHooks.onTrialCompleted(player);

		if (!crystal) {
			SESSIONS.remove(session.owner);
		}
	}

	private static void tick(MinecraftServer server) {
		if (!SESSIONS.isEmpty()) {
			for (TrialSession session : new ArrayList<>(SESSIONS.values())) {
				tickSession(session);
			}
		}

		if (++ticks % PICKUP_POLL_INTERVAL == 0) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				ForceData data = Progression.get(player);

				if (data.hasFlag(CrystalGrant.PENDING) && !SESSIONS.containsKey(player.getUUID())) {
					CrystalGrant.trySpawnPending(player);
				}

				if (data.hasFlag(CrystalGrant.AWAITING_PICKUP)) {
					CrystalGrant.checkPickup(player);
				}
			}
		}
	}

	private static void tickSession(TrialSession session) {
		WorldBalance b = WorldBalance.get();
		ServerPlayer player = session.player;
		session.age++;
		session.phaseTicks++;

		if (session.phase == TrialSession.Phase.VICTORY) {
			if (session.phaseTicks >= b.crystalFormDelayTicks) {
				end(session, true);
			}

			return;
		}

		if (player.isRemoved() || player.hasDisconnected() || !player.isAlive() || player.level() != session.level) {
			fail(session, "message.oldrepublic.trial.failed");
			return;
		}

		if (!insideChamber(session, player.position(), b)) {
			fail(session, "message.oldrepublic.trial.failed_left");
			return;
		}

		if (session.age > b.trialMaxTicks) {
			fail(session, "message.oldrepublic.trial.failed_time");
			return;
		}

		if (session.phase == TrialSession.Phase.FORMING) {
			if (session.phaseTicks >= b.trialFormTicks) {
				spawnEcho(session, b);
			}

			return;
		}

		Entity entity = session.echoId == null ? null : session.level.getEntity(session.echoId);

		if (!(entity instanceof ShadowEchoEntity echo) || !echo.isAlive()) {
			// Removed by something other than a defeat (void, /kill without death, unload).
			fail(session, "message.oldrepublic.trial.failed");
			return;
		}

		if (echo.getTarget() != player) {
			echo.setTarget(player);
		}

		if (!insideChamber(session, echo.position(), b)) {
			echo.teleportTo(session.echoSpawn.x, session.echoSpawn.y, session.echoSpawn.z);
			echo.getNavigation().stop();
		}
	}

	private static void spawnEcho(TrialSession session, WorldBalance b) {
		ServerPlayer player = session.player;
		ShadowEchoEntity echo = new ShadowEchoEntity(WorldEntities.SHADOW_ECHO, session.level);
		Vec3 at = session.echoSpawn;
		Vec3 look = player.position().subtract(at);
		float yaw = (float) (Mth.atan2(look.z, look.x) * Mth.RAD_TO_DEG) - 90f;
		echo.setPos(at.x, at.y, at.z);
		echo.setYRot(yaw);
		echo.setYHeadRot(yaw);
		echo.setYBodyRot(yaw);
		echo.setOwner(player.getUUID());
		echo.applyBalance(b);
		echo.setTarget(player);
		session.echoId = echo.getUUID();
		session.level.addFreshEntity(echo);
		session.setPhase(TrialSession.Phase.FIGHT);
		EffectEvents.around(session.level, WorldEffects.TRIAL_ECHO_FORM, at, null, echo.getId(), 0f, 1);
	}

	private static void fail(TrialSession session, String messageKey) {
		SESSIONS.remove(session.owner);
		discardEcho(session);
		EffectEvents.around(session.level, WorldEffects.TRIAL_COMPLETE, session.stoneTop(), null, -1, 1f, WorldEffects.COMPLETE_FAILED);
		ServerPlayer player = session.player;

		if (!player.hasDisconnected()) {
			player.sendOverlayMessage(Component.translatable(messageKey));
		}

		QuestHooks.onTrialFailed(player);
	}

	/**
	 * Ends a session. A won trial forms its crystal now; any other phase counts as a failure
	 * (logout or operator stop).
	 */
	private static void end(TrialSession session, boolean spawnCrystal) {
		if (session.phase != TrialSession.Phase.VICTORY) {
			fail(session, "message.oldrepublic.trial.failed");
			return;
		}

		SESSIONS.remove(session.owner);

		if (spawnCrystal) {
			CrystalGrant.spawn(session.player, session.level, session.stone);
		}
	}

	private static void discardEcho(TrialSession session) {
		if (session.echoId == null) {
			return;
		}

		Entity entity = session.level.getEntity(session.echoId);

		if (entity instanceof ShadowEchoEntity echo && !echo.isRemoved()) {
			EffectEvents.around(session.level, WorldEffects.TRIAL_ECHO_DISSOLVE, echo.position(), null, echo.getId(), 1f, WorldEffects.DISSOLVE_FADED);
			echo.discard();
		}
	}

	private static boolean stoneBusy(ServerLevel level, BlockPos stone) {
		for (TrialSession session : SESSIONS.values()) {
			if (session.level == level && session.stone.equals(stone)) {
				return true;
			}
		}

		return false;
	}

	static boolean insideChamber(TrialSession session, Vec3 pos, WorldBalance b) {
		Vec3 center = Vec3.atBottomCenterOf(session.stone);
		double dx = pos.x - center.x;
		double dz = pos.z - center.z;
		return dx * dx + dz * dz <= b.chamberRadius * b.chamberRadius && Math.abs(pos.y - center.y) <= b.chamberHeight;
	}

	/** A free spot about 3.5 blocks beyond the stone, on the side away from the player. */
	private static Vec3 echoSpawn(ServerLevel level, BlockPos stone, Vec3 playerPos) {
		Vec3 center = Vec3.atBottomCenterOf(stone);
		Vec3 away = new Vec3(center.x - playerPos.x, 0, center.z - playerPos.z);
		away = away.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : away.normalize();

		for (double distance : new double[] { 3.5, 2.5, 4.5, 1.5 }) {
			for (int turn = 0; turn < 4; turn++) {
				Vec3 dir = away.yRot((float) (turn * Math.PI / 2));
				Vec3 spot = center.add(dir.scale(distance));
				BlockPos feet = BlockPos.containing(spot);

				if (free(level, feet) && free(level, feet.above()) && !free(level, feet.below())) {
					return new Vec3(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
				}
			}
		}

		return center.add(0, 1, 0);
	}

	private static boolean free(ServerLevel level, BlockPos pos) {
		return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
	}

	/** The nearest meditation stone within {@code radius} blocks, or null (used by the command). */
	public static @Nullable BlockPos findStone(ServerLevel level, BlockPos around, int radius) {
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;

		for (BlockPos pos : BlockPos.betweenClosed(around.offset(-radius, -radius / 2, -radius), around.offset(radius, radius / 2, radius))) {
			if (level.getBlockState(pos).is(WorldBlocks.MEDITATION_STONE)) {
				double distance = pos.distSqr(around);

				if (distance < bestDistance) {
					bestDistance = distance;
					best = pos.immutable();
				}
			}
		}

		return best;
	}

	/** Snapshot of running sessions for debugging. */
	public static List<String> describe() {
		List<String> lines = new ArrayList<>();

		for (TrialSession session : SESSIONS.values()) {
			lines.add(session.player.getPlainTextName() + " at " + session.stone.toShortString() + " " + session.phase + " " + session.age + "t");
		}

		return lines;
	}

	private TrialManager() {
	}
}
