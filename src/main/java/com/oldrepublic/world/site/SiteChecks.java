package com.oldrepublic.world.site;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/**
 * Shared checks of the site blocks that spawn entities ({@code site_spawner}, {@code bounty_terminal}):
 * whether the entities around a position are really loaded, and who is near. Players are read from
 * {@code level.players()}, so game tests use listed mock players.
 */
public final class SiteChecks {
	/**
	 * The frozen loading rule (spec 3.3): the entities of the chunk holding {@code pos} and of every
	 * chunk within {@code chunkRadius} of it are loaded ({@code areEntitiesLoaded}), and {@code pos}
	 * itself is entity ticking. Until it holds, a block must not decide that anything is missing.
	 */
	public static boolean entitiesReady(ServerLevel level, BlockPos pos, int chunkRadius) {
		ChunkPos centre = ChunkPos.containing(pos);

		for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
			for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
				if (!level.areEntitiesLoaded(new ChunkPos(centre.x() + dx, centre.z() + dz).pack())) {
					return false;
				}
			}
		}

		return level.isPositionEntityTicking(pos);
	}

	/** Whether a living, non-spectator player is within {@code radius} blocks of {@code centre}. */
	public static boolean playerWithin(ServerLevel level, Vec3 centre, double radius) {
		return nearestPlayer(level, centre, radius) != null;
	}

	/** The nearest living, non-spectator player within {@code radius} blocks of {@code centre}, or null. */
	public static @Nullable ServerPlayer nearestPlayer(ServerLevel level, Vec3 centre, double radius) {
		ServerPlayer best = null;
		double bestSqr = radius * radius;

		for (ServerPlayer player : level.players()) {
			if (!player.isAlive() || player.isSpectator()) {
				continue;
			}

			double distSqr = player.position().distanceToSqr(centre);

			if (distSqr <= bestSqr) {
				best = player;
				bestSqr = distSqr;
			}
		}

		return best;
	}

	private SiteChecks() {
	}
}
