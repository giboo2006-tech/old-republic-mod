package com.oldrepublic.world.puzzle;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import com.oldrepublic.world.site.SiteBalance;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * World helpers shared by the puzzle blocks: who is near, whether a cell is occupied, connected
 * groups and the resettable blocks' idle tick. Server side.
 */
final class PuzzleWorld {
	/**
	 * Whether a non-spectator player is within {@code range} blocks of the centre of {@code pos}.
	 * Creative players count: a builder standing in a site keeps it as it is.
	 */
	static boolean playerNear(ServerLevel level, BlockPos pos, double range) {
		return !playersNear(level, pos, range).isEmpty();
	}

	/** Every living, non-spectator player within {@code range} blocks of the centre of {@code pos}. */
	static List<ServerPlayer> playersNear(ServerLevel level, BlockPos pos, double range) {
		Vec3 centre = Vec3.atCenterOf(pos);
		double rangeSq = range * range;
		List<ServerPlayer> near = new ArrayList<>();

		for (ServerPlayer player : level.players()) {
			if (player.isAlive() && !player.isSpectator() && player.position().distanceToSqr(centre) <= rangeSq) {
				near.add(player);
			}
		}

		return near;
	}

	/** Whether any live entity overlaps one of {@code cells}: an invisible block never turns solid around it. */
	static boolean occupied(ServerLevel level, Collection<BlockPos> cells) {
		for (BlockPos cell : cells) {
			if (!level.getEntities((Entity) null, new AABB(cell), Entity::isAlive).isEmpty()) {
				return true;
			}
		}

		return false;
	}

	/**
	 * The connected group of {@code start} (6-neighbour flood fill over blocks of the same block
	 * whose state passes {@code member}), at most {@code max} blocks, {@code start} first.
	 */
	static Set<BlockPos> group(Level level, BlockPos start, Block block, Predicate<BlockState> member, int max) {
		Set<BlockPos> found = new LinkedHashSet<>();
		Set<BlockPos> seen = new LinkedHashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		queue.add(start.immutable());

		while (!queue.isEmpty() && found.size() < Math.max(1, max)) {
			BlockPos next = queue.poll();

			if (!seen.add(next)) {
				continue;
			}

			BlockState state = level.getBlockState(next);

			if (!state.is(block) || !member.test(state)) {
				continue;
			}

			found.add(next);

			for (Direction direction : Direction.values()) {
				BlockPos neighbour = next.relative(direction);

				if (!seen.contains(neighbour)) {
					queue.add(neighbour);
				}
			}
		}

		return found;
	}

	/** Schedules the resettable block's next idle step ({@link PuzzleRules#resetInterval}). */
	static void scheduleReset(ServerLevel level, BlockPos pos, Block block, SiteBalance b) {
		level.scheduleTick(pos, block, PuzzleRules.resetInterval(b));
	}

	/** Whether {@code block} already has a scheduled tick pending at {@code pos} (the level keeps one per position and block). */
	static boolean tickPending(ServerLevel level, BlockPos pos, Block block) {
		// API-CHECK: LevelTicks#hasScheduledTick(BlockPos, T) (used by Fabric's VanillaStorageTests; probe net.minecraft.world.ticks.LevelTicks)
		return level.getBlockTicks().hasScheduledTick(pos, block);
	}

	/** Whether any block of {@code cells} receives redstone power. */
	static boolean powered(Level level, Collection<BlockPos> cells) {
		for (BlockPos cell : cells) {
			// API-CHECK: Level#hasNeighborSignal(BlockPos) (SignalGetter default method; probe net.minecraft.world.level.SignalGetter)
			if (level.hasNeighborSignal(cell)) {
				return true;
			}
		}

		return false;
	}

	private PuzzleWorld() {
	}
}
