package com.oldrepublic.boss;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.world.site.SiteAnchors;
import com.oldrepublic.world.site.SiteAnchors.Anchor;
import com.oldrepublic.world.site.SitePiece;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * Where everything of one Reliquary is, in world space (spec 8.2, W5 plan 4.9): built from the hall
 * {@link SitePiece} and its anchor sidecar, or given directly by a game test on the lead's
 * {@code arena_24x7x24} fixture.
 *
 * <p>Two boxes: the <b>Reliquary box</b> ({@link #reliquary}) is the whole hall template, used for
 * {@link BossArenas#isInArena}; the <b>hall box</b> is the Reliquary box cut at the threshold plane,
 * on the dais side ({@link #isInHall}), used for participants, the leash and the damage gates. The
 * threshold plane runs through the {@code THRESHOLD} cell, square to {@link #inward} (the cardinal
 * direction from the threshold toward the dais).
 *
 * @param reliquary   the whole hall template box
 * @param heart       the heart block
 * @param threshold   feet cell in the doorway between the antechamber and the hall
 * @param inward      the cardinal direction from the threshold toward the dais
 * @param dais        centre feet cell on the dais top
 * @param lectern     Vasch's feet cell at the lectern
 * @param pylons      the four resonance pylon cells, P1 to P4 (fewer on a fixture without them)
 * @param sentinels   gallery alcove feet cells, SENTINEL_1 to SENTINEL_4
 * @param debris      the top block of each debris pile (a Hurl chunk forms one block above)
 * @param columns     the min corner base cell of each broken column
 * @param doorSeals   every doorway seal cell
 * @param vaultSeals  every vault seal cell
 * @param plinth      the hoard plinth, or null
 * @param descentTop  feet cell at the top of the spiral descent (from the surface piece), or null
 */
public record ArenaLayout(BoundingBox reliquary, BlockPos heart, BlockPos threshold, Direction inward, BlockPos dais, BlockPos lectern,
		List<BlockPos> pylons, List<BlockPos> sentinels, List<BlockPos> debris, List<BlockPos> columns, List<BlockPos> doorSeals,
		List<BlockPos> vaultSeals, @Nullable BlockPos plinth, @Nullable BlockPos descentTop) {
	/** How far past the threshold plane the hall begins (half the doorway cell). */
	private static final double PLANE_SLACK = 0.5;
	/** The doorway: within this many blocks of the threshold plane and of the threshold's line. */
	private static final double DOORWAY_DEPTH = 1.0;
	private static final double DOORWAY_HALF_WIDTH = 2.0;
	/** Antechamber floor cells from the threshold, away from the dais (plan 4.9: 3 cells are walkable). */
	private static final int ANTECHAMBER_STEPS = 3;

	public ArenaLayout {
		pylons = List.copyOf(pylons);
		sentinels = List.copyOf(sentinels);
		debris = List.copyOf(debris);
		columns = List.copyOf(columns);
		doorSeals = List.copyOf(doorSeals);
		vaultSeals = List.copyOf(vaultSeals);
	}

	/**
	 * The layout of a placed hall from its sidecar. A sidecar that failed to load leaves the anchors
	 * empty: the dais and lectern then fall back to the box centre and the threshold to the heart, so
	 * the session still runs (logged once per call).
	 */
	public static ArenaLayout fromHall(SitePiece hall, BlockPos heart, @Nullable BlockPos descentTop) {
		BoundingBox box = hall.box();
		BlockPos centre = box.getCenter();
		BlockPos dais = first(hall, Anchor.DAIS, null);

		if (dais == null) {
			OldRepublicMod.LOGGER.warn("Reliquary hall {} has no anchor sidecar; using its box centre", hall.template());
			dais = new BlockPos(centre.getX(), box.minY() + 4, centre.getZ());
		}

		BlockPos threshold = first(hall, Anchor.THRESHOLD, heart);
		BlockPos lectern = first(hall, Anchor.LECTERN, dais);
		List<BlockPos> pylons = new ArrayList<>(4);

		for (Anchor anchor : new Anchor[] {Anchor.PYLON_1, Anchor.PYLON_2, Anchor.PYLON_3, Anchor.PYLON_4}) {
			BlockPos pos = first(hall, anchor, null);

			if (pos != null) {
				pylons.add(pos);
			}
		}

		List<BlockPos> sentinels = new ArrayList<>(4);

		for (Anchor anchor : new Anchor[] {Anchor.SENTINEL_1, Anchor.SENTINEL_2, Anchor.SENTINEL_3, Anchor.SENTINEL_4}) {
			BlockPos pos = first(hall, anchor, null);

			if (pos != null) {
				sentinels.add(pos);
			}
		}

		List<BlockPos> debris = new ArrayList<>(4);

		for (Anchor anchor : new Anchor[] {Anchor.DEBRIS_1, Anchor.DEBRIS_2, Anchor.DEBRIS_3, Anchor.DEBRIS_4}) {
			BlockPos pos = first(hall, anchor, null);

			if (pos != null) {
				debris.add(pos);
			}
		}

		List<BlockPos> columns = new ArrayList<>(6);

		for (Anchor anchor : new Anchor[] {Anchor.COLUMN_1, Anchor.COLUMN_2, Anchor.COLUMN_3, Anchor.COLUMN_4, Anchor.COLUMN_5, Anchor.COLUMN_6}) {
			BlockPos pos = first(hall, anchor, null);

			if (pos != null) {
				columns.add(pos);
			}
		}

		return new ArenaLayout(copy(box), heart.immutable(), threshold, inward(threshold, dais), dais, lectern, pylons, sentinels, debris, columns,
				SiteAnchors.all(hall, Anchor.DOOR_SEAL), SiteAnchors.all(hall, Anchor.VAULT_SEAL), first(hall, Anchor.PLINTH, null), descentTop);
	}

	/**
	 * A layout given cell by cell (game tests on a fixture): no seals, plinth or descent unless the
	 * test passes them.
	 */
	public static ArenaLayout of(BoundingBox reliquary, BlockPos heart, BlockPos threshold, BlockPos dais, BlockPos lectern, List<BlockPos> pylons,
			List<BlockPos> sentinels, List<BlockPos> debris, List<BlockPos> columns) {
		return new ArenaLayout(copy(reliquary), heart.immutable(), threshold.immutable(), inward(threshold, dais), dais.immutable(), lectern.immutable(),
				pylons, sentinels, debris, columns, List.of(), List.of(), null, null);
	}

	/** The same layout with these seal cells (tests that drive the seals). */
	public ArenaLayout withSeals(List<BlockPos> door, List<BlockPos> vault) {
		return new ArenaLayout(reliquary, heart, threshold, inward, dais, lectern, pylons, sentinels, debris, columns, door, vault, plinth, descentTop);
	}

	/** The cardinal direction from the threshold toward the dais (the larger horizontal axis wins). */
	static Direction inward(BlockPos threshold, BlockPos dais) {
		int dx = dais.getX() - threshold.getX();
		int dz = dais.getZ() - threshold.getZ();

		if (Math.abs(dx) > Math.abs(dz)) {
			return dx > 0 ? Direction.EAST : Direction.WEST;
		}

		return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
	}

	/** Signed distance of {@code pos} past the threshold plane toward the dais (negative: the antechamber side). */
	public double pastThreshold(Vec3 pos) {
		double x = pos.x - (threshold.getX() + 0.5);
		double z = pos.z - (threshold.getZ() + 0.5);
		return x * inward.getStepX() + z * inward.getStepZ();
	}

	/** Whether {@code pos} lies inside the whole Reliquary box. */
	public boolean isInReliquary(Vec3 pos) {
		return reliquary.isInside(BlockPos.containing(pos));
	}

	/** Whether {@code pos} lies in the hall box: inside the Reliquary box and past the threshold plane. */
	public boolean isInHall(Vec3 pos) {
		return isInReliquary(pos) && pastThreshold(pos) > PLANE_SLACK;
	}

	/** {@link #isInHall(Vec3)} for an entity's feet. */
	public boolean isInHall(Entity entity) {
		return isInHall(entity.position());
	}

	/** Whether {@code pos} lies in the antechamber: inside the Reliquary box, before the threshold plane. */
	public boolean isInAntechamber(Vec3 pos) {
		return isInReliquary(pos) && pastThreshold(pos) < -PLANE_SLACK;
	}

	/** Whether {@code pos} stands in the doorway: close to the threshold plane and to the threshold cell. */
	public boolean isInDoorway(Vec3 pos) {
		double along = pastThreshold(pos);

		if (Math.abs(along) > DOORWAY_DEPTH) {
			return false;
		}

		Direction side = inward.getClockWise();
		double lateral = (pos.x - (threshold.getX() + 0.5)) * side.getStepX() + (pos.z - (threshold.getZ() + 0.5)) * side.getStepZ();
		return Math.abs(lateral) <= DOORWAY_HALF_WIDTH && pos.y >= threshold.getY() - 1 && pos.y <= threshold.getY() + 3;
	}

	/** The antechamber floor cell items are moved to on a reset: three cells from the threshold, away from the dais. */
	public BlockPos antechamberFloor() {
		return threshold.relative(inward.getOpposite(), ANTECHAMBER_STEPS);
	}

	/** Bottom centre of the dais cell (where he stands in phase 2, kneels and reappears). */
	public Vec3 daisFeet() {
		return Vec3.atBottomCenterOf(dais);
	}

	/** Bottom centre of the lectern cell (where he waits). */
	public Vec3 lecternFeet() {
		return Vec3.atBottomCenterOf(lectern);
	}

	/** Horizontal distance squared from {@code pos} to the dais centre. */
	public double daisDistanceSqr(Vec3 pos) {
		double dx = pos.x - (dais.getX() + 0.5);
		double dz = pos.z - (dais.getZ() + 0.5);
		return dx * dx + dz * dz;
	}

	private static @Nullable BlockPos first(SitePiece hall, Anchor anchor, @Nullable BlockPos fallback) {
		BlockPos pos = SiteAnchors.find(hall, anchor);
		return pos != null ? pos.immutable() : fallback;
	}

	private static BoundingBox copy(BoundingBox box) {
		return new BoundingBox(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
	}
}
