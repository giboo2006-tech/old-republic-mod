package com.oldrepublic.enemy.construct;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Ruin Sentinels for other features (spec section 7.7): waking dormant sentinels and spawning
 * them. {@link #wakeNear} and {@link #spawn} are frozen (spec section 15.1); {@link #spawnArchive}
 * and {@link #isSentinel} are the boss feature's (W5 plan 4.6).
 */
public final class Sentinels {
	/**
	 * Wakes the dormant sentinels within {@code radius} of {@code pos}, never one dormant for
	 * {@code cause}'s side, and returns how many woke. In Peaceful none wake (dormant sentinels never
	 * wake for players there).
	 */
	public static int wakeNear(ServerLevel l, BlockPos pos, int radius, @Nullable Player cause) {
		if (radius <= 0 || l.getDifficulty() == Difficulty.PEACEFUL) {
			return 0;
		}

		Vec3 centre = Vec3.atCenterOf(pos);
		double max = (double) radius * radius;
		int woken = 0;

		for (RuinSentinel sentinel : l.getEntitiesOfClass(RuinSentinel.class, new AABB(pos).inflate(radius))) {
			if (!sentinel.isDormant() || sentinel.position().distanceToSqr(centre) > max) {
				continue;
			}

			if (cause != null && sentinel.sleepsFor(cause)) {
				continue;
			}

			sentinel.wake(cause);
			woken++;
		}

		return woken;
	}

	/**
	 * Spawns a dormant sentinel of {@code v} standing at {@code at} (feet cell), facing out of its
	 * alcove; {@code bound} marks one re-bound to guard a trail (always hostile), owned by
	 * {@code owner} when not null. Persistent; {@code at} becomes its post.
	 */
	public static LivingEntity spawn(ServerLevel l, BlockPos at, SentinelVariant v, boolean bound, @Nullable UUID owner) {
		RuinSentinel sentinel = create(l, at, v, bound, owner);
		l.addFreshEntity(sentinel);
		return sentinel;
	}

	/**
	 * A boss sentinel (W5 plan 4.6): a bound Watcher of allegiance {@code ARCHIVE} at {@code at},
	 * awake at once with its wake tell, never saved.
	 */
	public static RuinSentinel spawnArchive(ServerLevel l, BlockPos at, @Nullable UUID owner) {
		RuinSentinel sentinel = create(l, at, SentinelVariant.WATCHER, true, owner);
		sentinel.setArchive(true);
		l.addFreshEntity(sentinel);
		sentinel.wake(null);
		return sentinel;
	}

	/** Whether {@code e} is a Ruin Sentinel. */
	public static boolean isSentinel(Entity e) {
		return e instanceof RuinSentinel;
	}

	private static RuinSentinel create(ServerLevel l, BlockPos at, SentinelVariant v, boolean bound, @Nullable UUID owner) {
		RuinSentinel sentinel = SentinelEntities.RUIN_SENTINEL.create(l, EntitySpawnReason.STRUCTURE);

		if (sentinel == null) {
			throw new IllegalStateException("ruin_sentinel could not be created");
		}

		float yaw = openFacing(l, at);
		Vec3 feet = Vec3.atBottomCenterOf(at);
		sentinel.snapTo(feet.x, feet.y, feet.z, yaw, 0f);
		sentinel.setYHeadRot(yaw);
		sentinel.setYBodyRot(yaw);
		sentinel.setVariant(v);
		sentinel.setBound(bound);
		sentinel.setOwner(owner);
		sentinel.setPost(at, yaw);
		sentinel.setPersistenceRequired();
		return sentinel;
	}

	/**
	 * The yaw that faces out of an alcove: the horizontal direction with the most open cells at
	 * chest height within 3 blocks (ties keep the first of south, west, north, east).
	 */
	static float openFacing(ServerLevel l, BlockPos at) {
		// Minecraft yaw: south 0, west 90, north 180, east 270.
		Direction[] directions = {Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST};
		int best = 0;
		int bestOpen = -1;

		for (int i = 0; i < directions.length; i++) {
			int open = 0;

			for (int step = 1; step <= 3; step++) {
				if (l.getBlockState(at.above().relative(directions[i], step)).isSolid()) {
					break;
				}

				open++;
			}

			if (open > bestOpen) {
				bestOpen = open;
				best = i;
			}
		}

		return best * 90f;
	}

	private Sentinels() {
	}
}
