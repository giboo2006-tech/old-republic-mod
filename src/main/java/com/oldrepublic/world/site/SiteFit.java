package com.oldrepublic.world.site;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.planet.TerrainPalette;
import com.oldrepublic.quest.story.MeetingSites;
import com.oldrepublic.world.CampPoolElement;

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
 * Fits the ground to a runtime template by its site meta (docs/MILESTONE_6.md 3.1, 3.4;
 * docs/STRUCTURES.md 7), the way the worldgen pool element fits its pieces.
 *
 * <p>{@link #before} the stamp: trees are cut in the box's columns and a {@value #CLEARING_MARGIN}-block
 * margin around it, and in the box's own columns the terrain too, from the ground up to
 * {@code clear_above} over the box's top; holes under the stand are filled with the biome's own
 * ground layers ({@link TerrainPalette}: top, fill, base) at most the fill depth down.
 * {@link #after} the stamp: footings under the foundation ({@code support_depth}), the outer
 * {@code ramp_rings} rings stepped down to lower ground ({@link CampPoolElement#adaptTerrain}), an
 * apron and a cut: around the box, ground lower than a one-to-one slope down from the stand is
 * raised to it (the same ground layers), and ground higher than a one-to-one slope up from it is cut
 * back to it, so a site on a rise stands on a mound instead of a ledge, and a site on a hillside
 * on a terrace instead of in a pit.
 *
 * <p>Nothing built is ever filled over or cut ({@code #built_blocks}), and no column outside the
 * loaded chunks is touched.
 */
public final class SiteFit {
	/** Trees are cut this far around a fitted template's box. */
	public static final int CLEARING_MARGIN = 2;
	/** The widest apron (a hub's); a site's apron is as wide as its {@code support_depth}, at most this. */
	public static final int APRON_MAX = 24;
	/** The fill depth of a site that must reach the ground however far it is (a hub: no stilts). */
	public static final int DEEP_FILL = 64;

	/**
	 * Clears and fills the ground for a template about to be stamped in {@code box} with its stand
	 * (feet) at {@code standY}: see the class comment.
	 *
	 * @param fillDepth how far below the ground holes are filled ({@code support_depth}, or {@link #DEEP_FILL})
	 */
	public static void before(ServerLevel level, BoundingBox box, int standY, SiteMeta meta, int fillDepth) {
		int groundY = standY - 1;
		int top = box.maxY() + meta.clearAbove();
		BoundingBox margin = new BoundingBox(box.minX() - CLEARING_MARGIN, groundY + 1, box.minZ() - CLEARING_MARGIN, box.maxX() + CLEARING_MARGIN, top,
				box.maxZ() + CLEARING_MARGIN);
		SiteGround.clear(level, margin, groundY + 1, top, false);
		SiteGround.clear(level, box, groundY + 1, top, true);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				TerrainPalette.Layers layers = TerrainPalette.at(level, pos.set(x, groundY, z));
				fillDown(level, pos, x, z, groundY, groundY - fillDepth, layers);

				// A lowered bump shows its fill; give it its top again.
				if (level.getBlockState(pos.set(x, groundY, z)).is(layers.fill().getBlock())) {
					level.setBlock(pos, layers.top(), Block.UPDATE_CLIENTS);
				}
			}
		}
	}

	/**
	 * Footings, ramps, the apron and the cut for a template just stamped in {@code box} with its stand
	 * at {@code standY}: see the class comment.
	 *
	 * @param apronReach how far the apron and the cut reach out from the box (0: neither)
	 */
	public static void after(ServerLevel level, BoundingBox box, int standY, SiteMeta meta, int apronReach) {
		BoundingBox writable = new BoundingBox(box.minX(), level.getMinY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
		CampPoolElement.adaptTerrain(level, box, writable, (x, z) -> level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z),
				new CampPoolElement.Adaptation(meta.supportDepth(), 0, 0, 0, standY - box.minY(), meta.rampRings()));
		apron(level, box, standY, apronReach);
		cut(level, box, standY, apronReach);
	}

	/**
	 * Raises the ground around {@code box} to a one-to-one slope down from {@code standY}: a column
	 * {@code d} blocks out has its feet at {@code standY - d} at the least, up to {@code reach} out.
	 * Columns whose ground is higher, that hold a built block, or that are not loaded stay as they are.
	 */
	private static void apron(ServerLevel level, BoundingBox box, int standY, int reach) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		for (int x = box.minX() - reach; x <= box.maxX() + reach; x++) {
			for (int z = box.minZ() - reach; z <= box.maxZ() + reach; z++) {
				int d = Math.max(Math.max(box.minX() - x, x - box.maxX()), Math.max(box.minZ() - z, z - box.maxZ()));

				if (d <= 0 || !level.hasChunkAt(x, z)) {
					continue;
				}

				int top = standY - 1 - d;
				int floor = floorBelow(level, pos, x, z);

				if (floor == Integer.MIN_VALUE || floor >= top) {
					continue;
				}

				TerrainPalette.Layers layers = TerrainPalette.at(level, pos.set(x, top, z));

				for (int y = top; y > floor; y--) {
					BlockState state = level.getBlockState(pos.set(x, y, z));

					if (!(needsSupport(state) || state.is(BlockTags.LOGS)) || touchesBuilt(level, pos, box)) {
						break;
					}

					level.setBlock(pos, y == top ? layers.top() : y >= top - 2 ? layers.fill() : layers.base(), Block.UPDATE_CLIENTS);
				}
			}
		}
	}

	/**
	 * Cuts the ground around {@code box} back to a one-to-one slope up from {@code standY}: a column
	 * {@code d} blocks out keeps its ground at most at {@code standY - 1 + d}, up to {@code reach} out.
	 * What stands higher is removed when all of it is natural ground or growth ({@link SiteGround}),
	 * and the new top takes the biome's top block. A column with a built block or a fluid in the way,
	 * or that is not loaded, stays as it is.
	 */
	private static void cut(ServerLevel level, BoundingBox box, int standY, int reach) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		for (int x = box.minX() - reach; x <= box.maxX() + reach; x++) {
			for (int z = box.minZ() - reach; z <= box.maxZ() + reach; z++) {
				int d = Math.max(Math.max(box.minX() - x, x - box.maxX()), Math.max(box.minZ() - z, z - box.maxZ()));

				if (d <= 0 || !level.hasChunkAt(x, z)) {
					continue;
				}

				int keep = standY - 1 + d;
				int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;

				if (top <= keep || !cuttable(level, pos, box, x, z, keep + 1, top)) {
					continue;
				}

				for (int y = keep + 1; y <= top; y++) {
					level.setBlock(pos.set(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
				}

				if (SiteGround.naturalGround(level.getBlockState(pos.set(x, keep, z)))) {
					level.setBlock(pos, TerrainPalette.at(level, pos).top(), Block.UPDATE_CLIENTS);
				}
			}
		}
	}

	/**
	 * Whether every cell of a column from {@code fromY} to {@code toY} is air, growth or natural
	 * ground, with no fluid and nothing built in it or beside it (outside the site's own {@code box}).
	 */
	private static boolean cuttable(ServerLevel level, BlockPos.MutableBlockPos pos, BoundingBox box, int x, int z, int fromY, int toY) {
		for (int y = fromY; y <= toY; y++) {
			BlockState state = level.getBlockState(pos.set(x, y, z));

			if (state.isAir()) {
				continue;
			}

			if (!state.getFluidState().isEmpty() || state.is(MeetingSites.BUILT_BLOCKS) || !(SiteGround.removable(state) || SiteGround.naturalGround(state))
					|| touchesBuilt(level, pos, box)) {
				return false;
			}
		}

		return true;
	}

	/**
	 * Fills a column from {@code fromY} down to {@code toY} with the ground layers while it is air,
	 * plants, leaves or fluid; stops at the ground or next to a built block.
	 */
	static void fillDown(ServerLevel level, BlockPos.MutableBlockPos pos, int x, int z, int fromY, int toY, TerrainPalette.Layers layers) {
		for (int y = fromY; y >= Math.max(toY, level.getMinY()); y--) {
			if (!needsSupport(level.getBlockState(pos.set(x, y, z))) || touchesBuilt(level, pos)) {
				return;
			}

			level.setBlock(pos, y == fromY ? layers.top() : y >= fromY - 2 ? layers.fill() : layers.base(), Block.UPDATE_CLIENTS);
		}
	}

	/** Whether a cell is a hole a fit fills: air, a replaceable plant, leaves or a fluid. */
	private static boolean needsSupport(BlockState state) {
		return state.isAir() || state.canBeReplaced() || !state.getFluidState().isEmpty() || state.is(BlockTags.LEAVES);
	}

	/** Whether a cell touches a built block (a fit never fills against a player's or another site's wall). */
	private static boolean touchesBuilt(ServerLevel level, BlockPos pos) {
		return touchesBuilt(level, pos, null);
	}

	/** {@link #touchesBuilt(ServerLevel, BlockPos)}, the site's own blocks in {@code own} aside (its apron and cut meet its walls). */
	private static boolean touchesBuilt(ServerLevel level, BlockPos pos, @Nullable BoundingBox own) {
		for (Direction direction : Direction.values()) {
			BlockPos next = pos.relative(direction);

			if ((own == null || !own.isInside(next)) && level.getBlockState(next).is(MeetingSites.BUILT_BLOCKS)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * The height of a column's solid ground under its growth and any fluid, or {@link Integer#MIN_VALUE}
	 * when a built block comes first or there is no ground within reach.
	 */
	private static int floorBelow(ServerLevel level, BlockPos.MutableBlockPos pos, int x, int z) {
		BlockPos below = SiteGround.belowGrowth(level, x, z);

		if (below == null || level.getBlockState(below).is(MeetingSites.BUILT_BLOCKS)) {
			return Integer.MIN_VALUE;
		}

		pos.set(below);

		for (int i = 0; i < DEEP_FILL && pos.getY() > level.getMinY() && !level.getBlockState(pos).getFluidState().isEmpty(); i++) {
			pos.move(Direction.DOWN);
		}

		BlockState state = level.getBlockState(pos);
		return state.getFluidState().isEmpty() && !state.is(MeetingSites.BUILT_BLOCKS) ? pos.getY() : Integer.MIN_VALUE;
	}

	private SiteFit() {
	}
}
