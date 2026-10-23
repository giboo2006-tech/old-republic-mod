package com.oldrepublic.boss.kaast;

import java.util.ArrayList;
import java.util.List;

import com.oldrepublic.world.site.SiteAnchors;
import com.oldrepublic.world.site.SiteKind;
import com.oldrepublic.world.site.SitePiece;
import com.oldrepublic.world.site.StructureLocator;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Where things are in one hunting camp (spec MILESTONE_4.md 10.5): the pit's centre (the site's
 * {@code HEART}), the four gun nests ({@code NEST_1..4}, where Kaast stands), the mine spots
 * ({@code MINE_1..6}) and the gate's cells ({@code GATE}), from W1's template anchors. A camp
 * without them (a test arena, a template not yet built) gets nests on the ground 14 blocks out at the
 * compass points and mines on a ring 9 blocks out, and no gate.
 *
 * @param heart  the pit's centre
 * @param nests  where Kaast stands on each nest
 * @param mines  the mine spots, in order
 * @param gates  the gate's cells (may be empty)
 * @param radius the ring's radius around the heart
 */
public record CampLayout(BlockPos heart, List<BlockPos> nests, List<BlockPos> mines, List<BlockPos> gates, double radius) {
	private static final SiteAnchors.Anchor[] NESTS = {SiteAnchors.Anchor.NEST_1, SiteAnchors.Anchor.NEST_2, SiteAnchors.Anchor.NEST_3,
			SiteAnchors.Anchor.NEST_4};
	private static final SiteAnchors.Anchor[] MINES = {SiteAnchors.Anchor.MINE_1, SiteAnchors.Anchor.MINE_2, SiteAnchors.Anchor.MINE_3,
			SiteAnchors.Anchor.MINE_4, SiteAnchors.Anchor.MINE_5, SiteAnchors.Anchor.MINE_6};
	private static final int FALLBACK_NEST = 14;
	private static final int FALLBACK_MINES = 9;
	private static final int FALLBACK_MINE_COUNT = 6;
	private static final int GATE_WIDTH = 3;
	private static final int GATE_HEIGHT = 3;

	/** The layout of the camp whose pit is at {@code heart}. */
	public static CampLayout of(ServerLevel level, BlockPos heart, double radius) {
		SitePiece piece = StructureLocator.runtimePieceAt(level, heart, SiteKind.HUNTING_CAMP);
		List<BlockPos> nests = new ArrayList<>();
		List<BlockPos> mines = new ArrayList<>();
		List<BlockPos> gates = new ArrayList<>();

		if (piece != null) {
			for (SiteAnchors.Anchor anchor : NESTS) {
				BlockPos at = SiteAnchors.find(piece, anchor);

				if (at != null) {
					nests.add(at);
				}
			}

			for (SiteAnchors.Anchor anchor : MINES) {
				mines.addAll(SiteAnchors.all(piece, anchor));
			}

			for (BlockPos anchor : SiteAnchors.all(piece, SiteAnchors.Anchor.GATE)) {
				gates.addAll(gateCells(heart, anchor));
			}
		}

		if (nests.isEmpty()) {
			int[][] offsets = {{0, -FALLBACK_NEST}, {FALLBACK_NEST, 0}, {0, FALLBACK_NEST}, {-FALLBACK_NEST, 0}};

			for (int[] offset : offsets) {
				nests.add(ground(level, heart.offset(offset[0], 0, offset[1])));
			}
		}

		if (mines.isEmpty()) {
			for (int i = 0; i < FALLBACK_MINE_COUNT; i++) {
				double angle = i * Math.PI * 2 / FALLBACK_MINE_COUNT;
				mines.add(ground(level, heart.offset((int) Math.round(Math.cos(angle) * FALLBACK_MINES), 0, (int) Math.round(Math.sin(angle) * FALLBACK_MINES))));
			}
		}

		return new CampLayout(heart.immutable(), List.copyOf(nests), List.copyOf(mines), List.copyOf(gates), radius);
	}

	/**
	 * The cells of the gate whose anchor is {@code anchor} (the opening's bottom middle): 3 wide
	 * across the wall and 3 high, the wall's line being square to the way from the pit.
	 */
	public static List<BlockPos> gateCells(BlockPos heart, BlockPos anchor) {
		int dx = anchor.getX() - heart.getX();
		int dz = anchor.getZ() - heart.getZ();
		boolean alongX = Math.abs(dz) >= Math.abs(dx);
		List<BlockPos> cells = new ArrayList<>(GATE_WIDTH * GATE_HEIGHT);

		for (int across = -GATE_WIDTH / 2; across <= GATE_WIDTH / 2; across++) {
			for (int up = 0; up < GATE_HEIGHT; up++) {
				cells.add(alongX ? anchor.offset(across, up, 0) : anchor.offset(0, up, across));
			}
		}

		return cells;
	}

	/** The surface at {@code pos}'s column, near its height. */
	private static BlockPos ground(ServerLevel level, BlockPos pos) {
		if (!level.hasChunkAt(pos)) {
			return pos;
		}

		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
		return Math.abs(y - pos.getY()) <= 8 ? new BlockPos(pos.getX(), y, pos.getZ()) : pos;
	}

	/** Whether {@code pos} lies in the ring: within {@code radius} across and 12 blocks up or down of the pit. */
	public boolean inRing(double x, double y, double z) {
		double dx = x - (heart.getX() + 0.5);
		double dz = z - (heart.getZ() + 0.5);
		return dx * dx + dz * dz <= radius * radius && Math.abs(y - heart.getY()) <= 12;
	}
}
