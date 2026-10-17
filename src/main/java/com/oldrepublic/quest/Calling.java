package com.oldrepublic.quest;

import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.world.CaveLocator;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * The Calling: while a player in the Calling stage holds the journal, periodically tells their
 * client which way the nearest crystal cave lies. Only a direction and a closeness value are sent,
 * never the cave's position. {@link Guidance} sends the later chapters' Callings through
 * {@link #send}.
 */
final class Calling {
	static void tick(ServerPlayer player) {
		QuestBalance b = QuestFeature.balance();

		if (player.tickCount % Math.max(1, b.callingIntervalTicks) != 0 || !player.isAlive() || player.isSpectator()) {
			return;
		}

		if (QuestRules.stage(Progression.get(player)) != QuestStage.CALLING || !QuestItems.holdsJournal(player)) {
			return;
		}

		BlockPos cave = CaveLocator.findNearestCave(player);

		if (cave == null) {
			Vec3 eye = player.getEyePosition();
			EffectEvents.toPlayer(player, QuestEffects.CALLING, eye, null, player.getId(), 0, QuestEffects.CALLING_NONE);
			return;
		}

		send(player, Vec3.atCenterOf(cave), QuestEffects.CALLING_FOUND, player.getId());
	}

	/**
	 * Sends one Calling pulse toward {@code target}: the horizontal direction from the player's eyes
	 * and a closeness of 0 (at {@code callingFarDistance} or beyond) to 1 (there).
	 */
	static void send(ServerPlayer player, Vec3 target, int variant, int entityId) {
		Vec3 eye = player.getEyePosition();
		Vec3 toTarget = target.subtract(eye);
		Vec3 flat = toTarget.horizontal();
		Vec3 dir = flat.lengthSqr() < 1.0e-4 ? new Vec3(0, -1, 0) : flat.normalize();
		float closeness = (float) (1.0 - Math.min(1.0, toTarget.length() / QuestFeature.balance().callingFarDistance));
		EffectEvents.toPlayer(player, QuestEffects.CALLING, eye, dir, entityId, closeness, variant);
	}

	private Calling() {
	}
}
