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
 * never the cave's position.
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
		Vec3 eye = player.getEyePosition();

		if (cave == null) {
			EffectEvents.toPlayer(player, QuestEffects.CALLING, eye, null, player.getId(), 0, QuestEffects.CALLING_NONE);
			return;
		}

		Vec3 toCave = Vec3.atCenterOf(cave).subtract(eye);
		Vec3 flat = toCave.horizontal();
		Vec3 dir = flat.lengthSqr() < 1.0e-4 ? new Vec3(0, -1, 0) : flat.normalize();
		float closeness = (float) (1.0 - Math.min(1.0, toCave.length() / b.callingFarDistance));
		EffectEvents.toPlayer(player, QuestEffects.CALLING, eye, dir, player.getId(), closeness, QuestEffects.CALLING_FOUND);
	}

	private Calling() {
	}
}
