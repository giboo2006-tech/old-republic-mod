package com.oldrepublic.travel;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.planet.PlanetHubs;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.world.site.SitePiece;
import com.oldrepublic.world.site.StructureLocator;
import com.oldrepublic.world.site.WorldSites;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The landing check of a ship flown by hand, read from the world (spec MILESTONE_5.md 4.5, 4.6):
 * the ground under the ship's footprint (its class's {@link Pads#isClear} box), room for the hull
 * all the way down, another player's site, and a hub's footprint. The rules themselves, and the
 * order the HUD names a reason in, are {@link FlightRules#landing}.
 */
final class Landings {
	/** The scan's columns stand this far inside the footprint's edge (blocks). */
	private static final double COLUMN_INSET = 0.5;
	/** The scan looks this many blocks below the landing height, so "too high" is told from "no ground". */
	private static final int EXTRA_DEPTH = 2;
	/** The room box starts this far above the ground (a carpet or a snow layer does not count). */
	private static final double GROUND_MARGIN = 0.05;

	/**
	 * A landing check: the result, and the spot a field landing settles on (block-centred under the
	 * ship, on the highest ground found, so the record's block position is exactly where it stands).
	 */
	record Check(FlightRules.Landing result, Vec3 spot) {
	}

	/**
	 * The landing check of the spot below {@code ship} (4.6) for {@code pilot}, at the measured
	 * {@code speed}. The site lookup runs only for a spot that passes everything else.
	 */
	static Check check(ServerLevel level, ShipBody ship, UUID pilot, double speed, FlightBalance b) {
		boolean interceptor = ship.interceptor();
		double half = ShipBody.footprintHalf(interceptor);
		Vec3 pos = ship.position();
		double x = Math.floor(pos.x) + 0.5;
		double z = Math.floor(pos.z) + 0.5;
		FlightRules.Ground ground = scan(level, x, pos.y, z, half - COLUMN_INSET, (int) Math.ceil(b.landHeight) + EXTRA_DEPTH);
		double top = ground.solid() > 0 ? Math.ceil(ground.highest() - 1.0e-6) : pos.y;
		Vec3 spot = new Vec3(x, top, z);
		boolean room = ground.solid() > 0 && room(level, ship, spot, half, pos.y + ShipBody.footprintHeight(interceptor));
		FlightRules.Landing result = FlightRules.landing(speed, pos.y, ground, room, false, inHub(level, pos, b), b);

		if (result == FlightRules.Landing.OK && ownedByAnother(level, BlockPos.containing(spot), pilot)) {
			result = FlightRules.Landing.SITE;
		}

		return new Check(result, spot);
	}

	/**
	 * Nine columns under the footprint (its corners, the middles of its edges and its centre), each
	 * scanned down from the ship's bottom to the first block that is solid or holds water or lava.
	 * A column not loaded counts as no ground.
	 */
	private static FlightRules.Ground scan(ServerLevel level, double x, double y, double z, double inset, int depth) {
		int columns = 0;
		int solid = 0;
		boolean liquid = false;
		double lowest = Double.MAX_VALUE;
		double highest = -Double.MAX_VALUE;

		for (int i = -1; i <= 1; i++) {
			for (int k = -1; k <= 1; k++) {
				columns++;
				BlockPos column = BlockPos.containing(x + i * inset, y, z + k * inset);

				for (int dy = 0; dy <= depth; dy++) {
					BlockPos at = column.below(dy);

					if (!level.isLoaded(at)) {
						break;
					}

					if (!level.getFluidState(at).isEmpty()) {
						liquid = true;
						break;
					}

					BlockState state = level.getBlockState(at);
					VoxelShape shape = state.getCollisionShape(level, at);

					if (!shape.isEmpty()) {
						double groundTop = at.getY() + shape.max(Direction.Axis.Y);
						solid++;
						lowest = Math.min(lowest, groundTop);
						highest = Math.max(highest, groundTop);
						break;
					}
				}
			}
		}

		return new FlightRules.Ground(columns, solid, liquid, solid > 0 ? lowest : y, solid > 0 ? highest : y);
	}

	/**
	 * Room for the hull from the spot up to where it flies now: no block and no grounded ship in the
	 * way, and nobody standing there but the ship's own riders.
	 */
	private static boolean room(ServerLevel level, ShipBody ship, Vec3 spot, double half, double top) {
		AABB box = new AABB(spot.x - half, spot.y + GROUND_MARGIN, spot.z - half, spot.x + half, Math.max(top, spot.y + 2 * GROUND_MARGIN), spot.z + half);
		return level.noCollision(ship, box) && level.getEntities(ship, box, other -> inTheWay(other, ship)).isEmpty();
	}

	private static boolean inTheWay(Entity other, ShipBody ship) {
		return !other.isSpectator() && other.getVehicle() != ship && (other instanceof LivingEntity || other instanceof ShipBody);
	}

	/** Whether {@code pos} lies inside a hub's footprint: within {@code flight.hubBayRange} of its planet's hub (4.5). */
	static boolean inHub(ServerLevel level, Vec3 pos, FlightBalance b) {
		Planets.Planet planet = Planets.Planet.of(level.dimension());
		BlockPos hub = planet == null || planet == Planets.Planet.HOME ? null : PlanetHubs.hub(level.getServer(), planet);

		if (hub == null) {
			return false;
		}

		double dx = pos.x - (hub.getX() + 0.5);
		double dz = pos.z - (hub.getZ() + 0.5);
		return dx * dx + dz * dz <= (double) b.hubBayRange * b.hubBayRange;
	}

	/**
	 * The bay of the hub a ship landing by hand inside its footprint is guided to (4.5: the M4 bay
	 * rule, the first free bay, else a spot on the overflow rings), found clear now; null when the
	 * hub has no bays.
	 */
	static Pads.@Nullable Pad hubBay(ServerLevel level, ShipBody ship, UUID owner) {
		Planets.Planet planet = Planets.Planet.of(level.dimension());
		Pads.Pad pad = planet == null ? null : Pads.hubPad(level.getServer(), planet, owner);
		return pad == null ? null : Pads.landing(level, pad, ship);
	}

	/**
	 * Whether {@code pos} (or the ground block under it) lies inside a site another player owns:
	 * their ship cradle or any other placement recorded with an owner (4.6). Worldgen sites have no
	 * owner.
	 */
	private static boolean ownedByAnother(ServerLevel level, BlockPos pos, UUID pilot) {
		SitePiece piece = StructureLocator.pieceAt(level, pos);

		if (piece == null) {
			piece = StructureLocator.pieceAt(level, pos.below());
		}

		if (piece == null || !piece.runtime()) {
			return false;
		}

		for (WorldSites.SiteRecord site : WorldSites.all(level.getServer())) {
			if (SitePiece.runtimeKey(site.dimension(), site.origin()).equals(piece.siteKey())) {
				return site.owner() != null && !site.owner().equals(pilot);
			}
		}

		return false;
	}

	private Landings() {
	}
}
