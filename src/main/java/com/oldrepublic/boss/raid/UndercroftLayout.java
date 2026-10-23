package com.oldrepublic.boss.raid;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.world.site.SiteAnchors;
import com.oldrepublic.world.site.SiteAnchors.Anchor;
import com.oldrepublic.world.site.SiteKind;
import com.oldrepublic.world.site.SitePiece;
import com.oldrepublic.world.site.StructureLocator;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Where things are in the song-engine's undercroft under the Heart (spec MILESTONE_5.md 3.3.3, 12.3):
 * the box ({@code UNDERCROFT_MIN}, {@code UNDERCROFT_MAX}), the First Cantor's rise ({@code CANTOR}),
 * the keystone ({@code KEYSTONE}), the door ({@code UNDERCROFT_DOOR}), the choir's four spots
 * ({@code CHOIR_1..4}), the three silencing points ({@code SILENCE_1..3}) and the three floor rings
 * ({@code RING_1..3}), from W3's template anchors. Missing spots fall back to rings around the
 * Cantor's spot (a test fixture, a template not yet built).
 *
 * @param min      the box's lowest corner
 * @param max      the box's highest corner
 * @param cantor   where the First Cantor rises (floor level)
 * @param keystone the engine's keystone
 * @param door     the undercroft door's anchor, or null
 * @param choir    the four choir spots
 * @param silence  the three silencing points
 * @param rings    the three floor rings' centres
 */
public record UndercroftLayout(BlockPos min, BlockPos max, BlockPos cantor, BlockPos keystone, @Nullable BlockPos door, List<BlockPos> choir,
		List<BlockPos> silence, List<BlockPos> rings) {
	private static final Anchor[] CHOIR = {Anchor.CHOIR_1, Anchor.CHOIR_2, Anchor.CHOIR_3, Anchor.CHOIR_4};
	private static final Anchor[] SILENCE = {Anchor.SILENCE_1, Anchor.SILENCE_2, Anchor.SILENCE_3};
	private static final Anchor[] RINGS = {Anchor.RING_1, Anchor.RING_2, Anchor.RING_3};

	/** The undercroft of the Hymnal around {@code pos}, or null when {@code pos} is not in a Hymnal piece with an undercroft. */
	public static @Nullable UndercroftLayout at(ServerLevel level, BlockPos pos) {
		SitePiece piece = StructureLocator.runtimePieceAt(level, pos, SiteKind.HYMNAL);

		if (piece == null || SiteAnchors.find(piece, Anchor.CANTOR) == null) {
			return null;
		}

		return of(piece);
	}

	/** The undercroft laid out by the halls piece's anchors. */
	public static UndercroftLayout of(SitePiece piece) {
		BlockPos cantor = anchorOr(piece, Anchor.CANTOR, piece.box().getCenter());
		BlockPos min = anchorOr(piece, Anchor.UNDERCROFT_MIN, cantor.offset(-14, -2, -14));
		BlockPos max = anchorOr(piece, Anchor.UNDERCROFT_MAX, cantor.offset(14, 10, 14));
		BlockPos keystone = anchorOr(piece, Anchor.KEYSTONE, cantor.south(3));
		BlockPos door = SiteAnchors.find(piece, Anchor.UNDERCROFT_DOOR);
		return fallbacks(min, max, cantor, keystone, door, anchors(piece, CHOIR), anchors(piece, SILENCE), anchors(piece, RINGS));
	}

	/** An undercroft {@code half} blocks across either way around {@code cantor}, every spot on its fallback ring (the tests' fixture). */
	public static UndercroftLayout around(BlockPos cantor, int half) {
		return fallbacks(cantor.offset(-half, -1, -half), cantor.offset(half, 6, half), cantor, cantor.south(Math.max(2, half / 3)), null,
				List.of(), List.of(), List.of());
	}

	private static UndercroftLayout fallbacks(BlockPos min, BlockPos max, BlockPos cantor, BlockPos keystone, @Nullable BlockPos door,
			List<BlockPos> choir, List<BlockPos> silence, List<BlockPos> rings) {
		List<BlockPos> c = choir.size() >= CHOIR.length ? choir
				: List.of(cantor.offset(7, 0, 7), cantor.offset(-7, 0, 7), cantor.offset(-7, 0, -7), cantor.offset(7, 0, -7));
		List<BlockPos> s = silence.size() >= SILENCE.length ? silence : List.of(cantor.north(9), cantor.offset(8, 0, 5), cantor.offset(-8, 0, 5));
		List<BlockPos> r = rings.size() >= RINGS.length ? rings : List.of(cantor.north(5), cantor.offset(5, 0, 3), cantor.offset(-5, 0, 3));
		BlockPos lo = new BlockPos(Math.min(min.getX(), max.getX()), Math.min(min.getY(), max.getY()), Math.min(min.getZ(), max.getZ()));
		BlockPos hi = new BlockPos(Math.max(min.getX(), max.getX()), Math.max(min.getY(), max.getY()), Math.max(min.getZ(), max.getZ()));
		return new UndercroftLayout(lo, hi, cantor.immutable(), keystone.immutable(), door == null ? null : door.immutable(), List.copyOf(c),
				List.copyOf(s), List.copyOf(r));
	}

	private static BlockPos anchorOr(SitePiece piece, Anchor anchor, BlockPos fallback) {
		BlockPos at = SiteAnchors.find(piece, anchor);
		return at != null ? at : fallback;
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

	/** The undercroft's volume (whole blocks from {@code min} to {@code max}). */
	public AABB box() {
		return new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1, max.getY() + 1, max.getZ() + 1);
	}

	/** Whether {@code pos} lies in the undercroft. */
	public boolean inside(Vec3 pos) {
		return box().contains(pos);
	}

	/** Whether {@code pos} is the keystone (or a block touching it: the anchor marks the stone's base). */
	public boolean isKeystone(BlockPos pos) {
		return pos.distSqr(keystone) <= 2.0;
	}
}
