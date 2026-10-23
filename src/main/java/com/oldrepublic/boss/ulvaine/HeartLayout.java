package com.oldrepublic.boss.ulvaine;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.world.site.SiteAnchors;
import com.oldrepublic.world.site.SiteAnchors.Anchor;
import com.oldrepublic.world.site.SitePiece;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Where things are in the Hymnal's Heart (spec MILESTONE_5.md 3.3.3, 12.2): its centre ({@code HEART}),
 * the four rods ({@code ROD_1..4}), the two pylons where the chanters stand ({@code PYLON_1..2}), the
 * four resonator spires ({@code RESONATOR_1..4}) and the echo's lectern ({@code LECTERN}), from W3's
 * template anchors. A missing anchor falls back to a ring around the centre (a test fixture, a template
 * not yet built): rods 9 blocks out at the compass points, pylons 12 out east and west, resonators 8
 * out at the diagonals, the lectern 4 north.
 *
 * @param heart      the Heart's centre (floor level)
 * @param radius     the round hall's radius
 * @param height     how far above and below the centre the hall reaches
 * @param rods       the four rods, in order
 * @param pylons     the two pylons, in order
 * @param resonators the four resonator spots, in order
 * @param lectern    the echo's lectern
 */
public record HeartLayout(BlockPos heart, double radius, double height, List<BlockPos> rods, List<BlockPos> pylons, List<BlockPos> resonators,
		BlockPos lectern) {
	private static final Anchor[] RODS = {Anchor.ROD_1, Anchor.ROD_2, Anchor.ROD_3, Anchor.ROD_4};
	private static final Anchor[] PYLONS = {Anchor.PYLON_1, Anchor.PYLON_2};
	private static final Anchor[] RESONATORS = {Anchor.RESONATOR_1, Anchor.RESONATOR_2, Anchor.RESONATOR_3, Anchor.RESONATOR_4};

	/** The Heart of the Hymnal around {@code pos}, or null when {@code pos} is not in the Hymnal's halls. */
	public static @Nullable HeartLayout at(ServerLevel level, BlockPos pos) {
		SitePiece piece = HymnalSites.hallsAt(level, pos);
		return piece == null ? null : of(piece);
	}

	/** The Heart laid out by the halls piece's anchors. */
	public static HeartLayout of(SitePiece piece) {
		BlockPos heart = HymnalSites.anchorOr(piece, Anchor.HEART, piece.box().getCenter());
		UlvaineBalance b = UlvaineBalance.get();
		List<BlockPos> rods = anchors(piece, RODS);
		List<BlockPos> pylons = anchors(piece, PYLONS);
		List<BlockPos> resonators = anchors(piece, RESONATORS);
		BlockPos lectern = HymnalSites.anchorOr(piece, Anchor.LECTERN, heart.north(4));
		return fallbacks(heart, b.heartRadius, b.heartHeight, rods, pylons, resonators, lectern);
	}

	/** A Heart around {@code heart} with every spot on its fallback ring (the tests' fixture). */
	public static HeartLayout around(BlockPos heart, double radius) {
		return fallbacks(heart, radius, UlvaineBalance.get().heartHeight, List.of(), List.of(), List.of(), heart.north(4));
	}

	private static HeartLayout fallbacks(BlockPos heart, double radius, double height, List<BlockPos> rods, List<BlockPos> pylons,
			List<BlockPos> resonators, BlockPos lectern) {
		List<BlockPos> r = rods.size() >= RODS.length ? rods : List.of(heart.north(9), heart.east(9), heart.south(9), heart.west(9));
		List<BlockPos> p = pylons.size() >= PYLONS.length ? pylons : List.of(heart.east(12), heart.west(12));
		List<BlockPos> s = resonators.size() >= RESONATORS.length ? resonators
				: List.of(heart.offset(6, 0, 6), heart.offset(-6, 0, 6), heart.offset(-6, 0, -6), heart.offset(6, 0, -6));
		return new HeartLayout(heart.immutable(), radius, height, List.copyOf(r), List.copyOf(p), List.copyOf(s), lectern.immutable());
	}

	private static List<BlockPos> anchors(SitePiece piece, Anchor[] names) {
		List<BlockPos> out = new ArrayList<>(names.length);

		for (Anchor name : names) {
			BlockPos at = SiteAnchors.find(piece, name);

			if (at != null) {
				out.add(at);
			}
		}

		return out;
	}

	/** Whether {@code (x, y, z)} lies in the Heart: within {@code radius} across and {@code height} up or down. */
	public boolean inside(double x, double y, double z) {
		double dx = x - (heart.getX() + 0.5);
		double dz = z - (heart.getZ() + 0.5);
		return dx * dx + dz * dz <= radius * radius && Math.abs(y - heart.getY()) <= height;
	}

	public boolean inside(Vec3 pos) {
		return inside(pos.x, pos.y, pos.z);
	}

	/** The centre of the Heart's floor. */
	public Vec3 centre() {
		return Vec3.atBottomCenterOf(heart);
	}
}
