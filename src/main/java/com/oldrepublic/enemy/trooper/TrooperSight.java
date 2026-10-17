package com.oldrepublic.enemy.trooper;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Whether players can see a spot: troopers that walk off (escorts, spared or tended troopers) are
 * removed only out of every player's sight, and reinforcements arrive where nobody sees them appear
 * (spec 7.1, 7.3). A block ray from each player's eyes; facing is ignored, so a player who turns
 * away still "sees" what is in line with them.
 */
public final class TrooperSight {
	/** Players farther than this never see a trooper appear or vanish. */
	public static final double SIGHT_RANGE = 96;

	/** True when no player in {@code level} within {@link #SIGHT_RANGE} has a clear line to {@code point}. */
	public static boolean unseen(ServerLevel level, Vec3 point) {
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || player.distanceToSqr(point) > SIGHT_RANGE * SIGHT_RANGE) {
				continue;
			}

			HitResult hit = level.clip(new ClipContext(player.getEyePosition(), point, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));

			if (hit.getType() == HitResult.Type.MISS) {
				return false;
			}
		}

		return true;
	}

	/** True when neither the head nor the feet of {@code entity} can be seen by any player. */
	public static boolean unseen(ServerLevel level, Entity entity) {
		return unseen(level, entity.getEyePosition()) && unseen(level, entity.position().add(0, 0.2, 0));
	}

	private TrooperSight() {
	}
}
