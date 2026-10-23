package com.oldrepublic.world.site;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.planet.PlanetBlocks;
import com.oldrepublic.quest.story.MeetingSites;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The ground under trees (spec MILESTONE_4.md 3.5, docs/MILESTONE_6.md 3.1): which blocks are
 * growth a site may cut away, which are natural ground, and where a column's ground lies below its
 * trees. Shared by the hubs' and planet sites' clearing searches and by far placement.
 */
public final class SiteGround {
	/** How far below a column's top the ground is looked for under trees. */
	private static final int GROWTH_DEPTH = 40;

	/**
	 * The first block of a column, from its top down, that is not a tree or plant a site may clear: a
	 * fluid, a built block or the ground. Null when there is none within {@value #GROWTH_DEPTH} blocks.
	 */
	public static @Nullable BlockPos belowGrowth(ServerLevel level, int x, int z) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);

		for (int i = 0; i < GROWTH_DEPTH && pos.getY() > level.getMinY(); i++, pos.move(Direction.DOWN)) {
			BlockState state = level.getBlockState(pos);

			if (!state.getFluidState().isEmpty() || state.is(MeetingSites.BUILT_BLOCKS) || !removable(state)) {
				return pos.immutable();
			}
		}

		return null;
	}

	/** The feet height on a column's natural ground below trees and plants, or {@link Integer#MIN_VALUE} for water or anything built. */
	public static int groundStand(ServerLevel level, int x, int z) {
		BlockPos below = belowGrowth(level, x, z);

		if (below == null) {
			return Integer.MIN_VALUE;
		}

		BlockState state = level.getBlockState(below);
		return state.getFluidState().isEmpty() && !state.is(MeetingSites.BUILT_BLOCKS) && naturalGround(state) ? below.getY() + 1 : Integer.MIN_VALUE;
	}

	/**
	 * Trees, plants and decoration a hub or site may clear: on Ghessar and Tyrvann also mangrove roots,
	 * hanging moss, the bleached ribs' bone and stormglass.
	 */
	public static boolean removable(BlockState state) {
		return state.isAir() || state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES) || (state.canBeReplaced() && state.getFluidState().isEmpty())
				|| state.is(Blocks.MOSS_CARPET) || state.is(Blocks.MUSHROOM_STEM) || state.is(Blocks.BROWN_MUSHROOM_BLOCK) || state.is(Blocks.RED_MUSHROOM_BLOCK)
				|| state.is(Blocks.MANGROVE_ROOTS) || state.is(Blocks.PALE_HANGING_MOSS) || state.is(Blocks.BONE_BLOCK) || state.is(PlanetBlocks.STORMGLASS_CLUSTER);
	}

	/**
	 * The worlds' natural ground (wider than {@code #story_ground}: it also takes moss, tuff, basalt
	 * and clay, and Ghessar's and Tyrvann's salt crust, calcite, peat, mud and blackstone).
	 */
	public static boolean naturalGround(BlockState state) {
		return state.is(MeetingSites.STORY_GROUND) || state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(BlockTags.TERRACOTTA)
				|| state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY) || state.is(Blocks.SMOOTH_BASALT)
				|| state.is(Blocks.BASALT) || state.is(Blocks.SMOOTH_SANDSTONE) || state.is(Blocks.RED_SANDSTONE) || state.is(PlanetBlocks.SALT_CRUST)
				|| state.is(PlanetBlocks.PEAT) || state.is(Blocks.CALCITE) || state.is(Blocks.BLACKSTONE) || state.is(Blocks.MUD)
				|| state.is(Blocks.MUDDY_MANGROVE_ROOTS) || state.is(Blocks.SANDSTONE);
	}

	/**
	 * Removes trees and plants from {@code fromY} to {@code toY} in every column of {@code box}, and
	 * natural ground too where {@code ground} is true (a site's own columns: the bumps and the
	 * overhang over it). Built blocks and fluids stay.
	 */
	public static void clear(ServerLevel level, BoundingBox box, int fromY, int toY, boolean ground) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				for (int y = fromY; y <= toY; y++) {
					BlockState state = level.getBlockState(pos.set(x, y, z));

					if (!state.isAir() && !state.is(MeetingSites.BUILT_BLOCKS) && state.getFluidState().isEmpty() && (removable(state) || ground && naturalGround(state))) {
						level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
				}
			}
		}
	}

	private SiteGround() {
	}
}
