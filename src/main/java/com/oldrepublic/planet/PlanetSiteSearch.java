package com.oldrepublic.planet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.quest.story.MeetingSites;
import com.oldrepublic.world.site.RuntimeSites;
import com.oldrepublic.world.site.SiteGround;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;

/**
 * The planet sites' second search, and the report of why a site found no spot (spec MILESTONE_4.md
 * 3.4).
 *
 * <p>{@link PlanetSites} first asks {@link RuntimeSites#placeSurface} for a stand on the site's
 * rings around the hub: a bare square of {@code #story_ground} with at most 3 blocks of slope (the
 * M3 rules), 16 candidates a ring. Only the few candidates whose chunks are loaded around the owner
 * count, and in Selvaren's highwood (a trunk in nearly every square) or on the planets' hills none
 * of them may pass. Then {@link #placeClearing} looks again around the ring point the way the hub's
 * second search does: a grid of stands every {@link #STEP} blocks within {@link #REACH} of it and
 * at most {@link #RING_SLACK} inside the inner ring or past the outer one. Each footprint must be
 * natural ground below its trees and plants ({@link SiteGround#belowGrowth}) with at most
 * {@link #MAX_SPREAD} between its lowest and highest ground, no water and no built block; the box
 * and the margin the clearing touches loaded, no worldgen piece, recorded site or built block in
 * the box ({@link RuntimeSites#fits}) and the stand far enough from recorded sites and in the site's
 * own biome when it has one (the site rules' {@code forbidden}). The flattest stand wins, then the one nearest the ring point; its trees and
 * bumps are cleared, the ground filled, and the template placed facing the hub.
 *
 * <p>{@link #explain} reports both searches: why each loaded ring candidate was refused (mirroring
 * {@link MeetingSites#evaluate} and the checks of {@link RuntimeSites#findSurface}), and the
 * clearing search's refusals counted by reason with the first example of each.
 */
public final class PlanetSiteSearch {
	/** Grid step of the clearing search around the ring point. */
	private static final int STEP = 8;
	/** How far from the ring point the clearing search looks. */
	private static final int REACH = 96;
	/** How far inside a site's inner ring, or past its outer ring, the clearing search may stand. */
	private static final int RING_SLACK = 16;
	/** The most the ground under a cleared site may vary: the depth {@link RuntimeSites#adaptTerrain} fills. */
	private static final int MAX_SPREAD = 8;
	/** Columns sampled per side of a footprint (every other column), as the hub's search. */
	private static final int SAMPLE_STEP = 2;
	/** Candidates per ring of {@link RuntimeSites#findSurface}: the report walks the same ones. */
	private static final int RING_ANGLES = 16;
	/** Free blocks {@link MeetingSites#evaluate} wants above a stand. */
	private static final int HEADROOM = 3;
	/** The band around a column's ground where {@link MeetingSites#evaluate} looks for built blocks. */
	private static final int BUILT_BELOW = 2;
	private static final int BUILT_ABOVE = 5;

	/** The clearing search's answer: the chosen stand and its turn (null when none fits), the stands looked at and why the rest were refused. */
	private record Search(@Nullable BlockPos stand, Rotation rotation, int looked, Tally refused) {
	}

	/** A stand whose ground passed: the centre column at its mean ground, its spread and its distance from the ring point. */
	private record Flat(BlockPos stand, int spread, double distance) {
	}

	/** Refusals counted by reason, with the first example of each, in the order they were met. */
	private static final class Tally {
		private final Map<String, Integer> counts = new LinkedHashMap<>();
		private final Map<String, String> examples = new HashMap<>();

		void add(String reason, String example) {
			counts.merge(reason, 1, Integer::sum);
			examples.putIfAbsent(reason, example);
		}

		void appendTo(StringBuilder out) {
			counts.forEach((reason, count) -> out.append("    ").append(count).append(" x ").append(reason).append(" (first: ").append(examples.get(reason)).append(")\n"));
		}
	}

	/**
	 * Searches around {@code ringPoint} for a stand with trees allowed and places {@code surface}
	 * there facing the hub, clearing trees and bumps first. Null when no stand fits.
	 *
	 * @param forbidden stands that may not be used (near a recorded site)
	 */
	static RuntimeSites.@Nullable Placement placeClearing(ServerLevel level, PlanetSites.Site site, PlanetSites.Piece surface, int footprint, BlockPos hub,
			BlockPos ringPoint, Predicate<BlockPos> forbidden, @Nullable UUID owner) {
		Search search = clearing(level, surface, footprint, hub, ringPoint, PlanetSites.rings(site), forbidden);
		BlockPos stand = search.stand();
		return stand == null ? null : PlanetHubs.placeClearing(level, surface.template(), stand, search.rotation(), surface.layers(), owner);
	}

	/**
	 * Why {@code owner}'s {@code site} on {@code planet} is or is not placed, for the log and for test
	 * failures: the recorded position, the hub, ring point, owner distance and next attempt, then every
	 * loaded candidate of the ring search with the check that refused it, then the clearing search's
	 * refusals by reason. Searches without placing anything.
	 */
	public static String explain(ServerPlayer owner, PlanetSites.Site site, Planets.Planet planet) {
		MinecraftServer server = owner.level().getServer();
		StringBuilder out = new StringBuilder();
		out.append(site).append(" for ").append(owner.getName().getString()).append(" on ").append(planet.level().identifier()).append('\n');
		GlobalPos found = PlanetSites.find(owner, site);

		if (found != null) {
			out.append("  recorded at ").append(found.pos().toShortString()).append(" in ").append(found.dimension().identifier()).append('\n');
		}

		ServerLevel level = Planets.level(server, planet);
		BlockPos hub = PlanetHubs.hub(server, planet);
		GlobalPos ring = PlanetSites.seededPoint(server, owner.getUUID(), site, planet);
		List<PlanetSites.Piece> pieces = PlanetSites.pieces(site);

		if (level == null || hub == null || ring == null || pieces.isEmpty()) {
			out.append("  nothing to search: level ").append(level != null ? "loaded" : "missing").append(", hub ").append(hub == null ? "none" : hub.toShortString())
					.append(", ring point ").append(ring == null ? "none" : ring.pos().toShortString()).append(", pieces ").append(pieces.size()).append('\n');
			return out.toString();
		}

		BlockPos point = ring.pos();
		Long next = PlanetSites.nextTry(owner.getUUID(), site);
		double distance = Math.sqrt(Math.pow(owner.getX() - (point.getX() + 0.5), 2) + Math.pow(owner.getZ() - (point.getZ() + 0.5), 2));
		out.append(String.format(Locale.ROOT, "  hub %s, ring point %s; owner at %s in %s, %.0f blocks from the ring point (range %d); now %d, next attempt %s%n",
				hub.toShortString(), point.toShortString(), owner.blockPosition().toShortString(), owner.level().dimension().identifier(), distance,
				PlanetBalance.get().siteEnsureRange, Clocks.time(server), next == null ? "any time" : next.toString()));
		out.append(ringReport(level, site, hub, point));
		PlanetSites.Piece surface = pieces.getFirst();
		int footprint = PlanetSites.footprint(level, surface.template());
		Search search = clearing(level, surface, footprint, hub, point, PlanetSites.rings(site), PlanetSites.rules(level, site, footprint).forbidden());
		BlockPos chosen = search.stand();
		out.append(String.format(Locale.ROOT, "  clearing search: %d stands within %d of the ring point every %d blocks and near the rings, spread at most %d: %s%n",
				search.looked(), REACH, STEP, MAX_SPREAD, chosen == null ? "none fits" : "would take " + chosen.toShortString()));
		search.refused().appendTo(out);
		return out.toString();
	}

	/** The clearing search, without placing anything. */
	private static Search clearing(ServerLevel level, PlanetSites.Piece surface, int footprint, BlockPos hub, BlockPos ringPoint, int[] rings,
			Predicate<BlockPos> forbidden) {
		Tally refused = new Tally();
		StructureTemplate template = level.getStructureManager().get(surface.template()).orElse(null);

		if (template == null) {
			refused.add("missing template", surface.template().toString());
			return new Search(null, Rotation.NONE, 0, refused);
		}

		List<Flat> flats = new ArrayList<>();
		int inner = Arrays.stream(rings).min().orElse(0) - RING_SLACK;
		int outer = Arrays.stream(rings).max().orElse(0) + RING_SLACK;
		int looked = 0;

		for (int dx = -REACH; dx <= REACH; dx += STEP) {
			for (int dz = -REACH; dz <= REACH; dz += STEP) {
				int cx = ringPoint.getX() + dx;
				int cz = ringPoint.getZ() + dz;
				double fromHub = Math.sqrt(Math.pow(cx - hub.getX(), 2) + Math.pow(cz - hub.getZ(), 2));

				if (dx * dx + dz * dz > REACH * REACH || fromHub < inner || fromHub > outer) {
					continue;
				}

				looked++;
				int[] spread = ground(level, cx, cz, footprint, refused);

				if (spread == null) {
					continue;
				}

				if (spread[1] - spread[0] > MAX_SPREAD) {
					refused.add("ground varies by more than " + MAX_SPREAD, cx + ", " + cz + " varies by " + (spread[1] - spread[0]));
					continue;
				}

				flats.add(new Flat(new BlockPos(cx, spread[2], cz), spread[1] - spread[0], Math.sqrt(dx * dx + dz * dz)));
			}
		}

		flats.sort(Comparator.comparingInt(Flat::spread).thenComparingDouble(Flat::distance));
		Vec3 origin = Vec3.atBottomCenterOf(hub);

		for (Flat flat : flats) {
			Rotation rotation = facing(flat.stand(), origin, Vec3.atCenterOf(ringPoint).subtract(origin));
			String why = boxRefusal(level, template, surface, flat.stand(), rotation, forbidden);

			if (why == null) {
				return new Search(flat.stand(), rotation, looked, refused);
			}

			refused.add(why, flat.stand().toShortString());
		}

		return new Search(null, Rotation.NONE, looked, refused);
	}

	/**
	 * {min, max, mean} of the ground stands under a footprint centred on ({@code cx}, {@code cz}),
	 * every other column, below trees and plants; null (counted in {@code refused}) when a column is
	 * not loaded, wet, built on or not natural ground.
	 */
	private static int @Nullable [] ground(ServerLevel level, int cx, int cz, int footprint, Tally refused) {
		int half = footprint / 2;
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		long sum = 0;
		int columns = 0;

		for (int dx = -half; dx <= half; dx += SAMPLE_STEP) {
			for (int dz = -half; dz <= half; dz += SAMPLE_STEP) {
				int x = cx + dx;
				int z = cz + dz;

				if (!level.hasChunkAt(x, z)) {
					refused.add("not loaded", x + ", " + z);
					return null;
				}

				BlockPos below = SiteGround.belowGrowth(level, x, z);

				if (below == null) {
					refused.add("no ground within 40 blocks below the growth", x + ", " + z);
					return null;
				}

				BlockState state = level.getBlockState(below);

				if (!state.getFluidState().isEmpty()) {
					refused.add("water", below.toShortString());
					return null;
				} else if (state.is(MeetingSites.BUILT_BLOCKS)) {
					refused.add("built block " + id(state), below.toShortString());
					return null;
				} else if (!SiteGround.naturalGround(state)) {
					refused.add("not natural ground: " + id(state), below.toShortString());
					return null;
				}

				int stand = below.getY() + 1;
				min = Math.min(min, stand);
				max = Math.max(max, stand);
				sum += stand;
				columns++;
			}
		}

		return columns == 0 ? null : new int[] {min, max, (int) Math.round(sum / (double) columns)};
	}

	/** Why a cleared template on {@code stand} may not go there, or null when it may. */
	private static @Nullable String boxRefusal(ServerLevel level, StructureTemplate template, PlanetSites.Piece surface, BlockPos stand, Rotation rotation,
			Predicate<BlockPos> forbidden) {
		if (forbidden.test(stand)) {
			return "too near a recorded site, or outside the site's biome";
		}

		BlockPos templateOrigin = RuntimeSites.templateOrigin(template, stand, rotation, surface.layers());
		BoundingBox box = template.getBoundingBox(new StructurePlaceSettings().setRotation(rotation), templateOrigin);

		if (!loaded(level, box.inflatedBy(PlanetHubs.CLEARING_MARGIN))) {
			return "box or clearing margin not loaded";
		} else if (RuntimeSites.touchesWorldgenPiece(level, box)) {
			return "worldgen piece in the box";
		} else if (!RuntimeSites.fits(level, surface.template(), stand, rotation, surface.layers())) {
			return "recorded site or built block in the box";
		}

		return null;
	}

	/**
	 * The ring search of {@code site} around {@code hub}, candidate by candidate: its rules, one line
	 * per loaded candidate in {@link RuntimeSites#findSurface}'s order with the check that refused it
	 * (or that it passes), and how many were not loaded. Places nothing.
	 */
	static String ringReport(ServerLevel level, PlanetSites.Site site, BlockPos hub, BlockPos ringPoint) {
		List<PlanetSites.Piece> pieces = PlanetSites.pieces(site);
		StructureTemplate template = pieces.isEmpty() ? null : level.getStructureManager().get(pieces.getFirst().template()).orElse(null);

		if (template == null) {
			return "  ring search: " + site + " has no surface template\n";
		}

		PlanetSites.Piece surface = pieces.getFirst();
		int footprint = PlanetSites.footprint(level, surface.template());
		MeetingSites.Rules rules = PlanetSites.rules(level, site, footprint);
		int[] rings = PlanetSites.rings(site);
		StringBuilder out = new StringBuilder();
		out.append(String.format(Locale.ROOT, "  ring search: %s, buried %d, footprint %d, clearance %d, slope at most %d, rings %s%n", surface.template(),
				surface.layers(), footprint, rules.clearance(), rules.maxSlope(), Arrays.toString(rings)));
		Vec3 origin = Vec3.atBottomCenterOf(hub);
		Vec3 forward = Vec3.atCenterOf(ringPoint).subtract(Vec3.atCenterOf(hub)).horizontal();
		double phase = forward.lengthSqr() < 1.0e-4 ? 0 : Math.atan2(forward.z, forward.x);
		int unloaded = 0;

		for (int ring : rings) {
			for (int i = 0; i < RING_ANGLES; i++) {
				double angle = phase + i * Math.PI * 2 / RING_ANGLES;
				int cx = (int) Math.floor(origin.x + Math.cos(angle) * ring);
				int cz = (int) Math.floor(origin.z + Math.sin(angle) * ring);

				if (!level.hasChunkAt(cx, cz)) {
					unloaded++;
					continue;
				}

				MeetingSites.Candidate candidate = MeetingSites.evaluate(level, cx, cz, rules);
				String why = candidate == null ? groundRefusal(level, cx, cz, rules)
						: spotRefusal(level, template, candidate.stand(), origin, forward, rules, surface.layers());
				out.append("    ring ").append(ring).append(" #").append(i).append(" at ").append(cx).append(", ").append(cz).append(": ").append(why).append('\n');
			}
		}

		out.append("    ").append(unloaded).append(" more ring candidates are not loaded\n");
		return out.toString();
	}

	/** The first check of {@link MeetingSites#evaluate} that refuses the square centred on ({@code cx}, {@code cz}). */
	private static String groundRefusal(ServerLevel level, int cx, int cz, MeetingSites.Rules rules) {
		int half = rules.footprint() / 2;
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		for (int dx = -half; dx <= half; dx++) {
			for (int dz = -half; dz <= half; dz++) {
				int x = cx + dx;
				int z = cz + dz;

				if (!level.hasChunkAt(x, z)) {
					return "footprint not loaded at " + x + ", " + z;
				}

				int stand = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				BlockState ground = level.getBlockState(pos.set(x, stand - 1, z));

				if (!ground.is(MeetingSites.STORY_GROUND)) {
					return "ground " + id(ground) + " at " + pos.toShortString() + " is not #story_ground";
				} else if (!ground.getFluidState().isEmpty()) {
					return "wet ground at " + pos.toShortString();
				}

				for (int y = stand; y < stand + HEADROOM; y++) {
					BlockState above = level.getBlockState(pos.set(x, y, z));

					if (!(above.isAir() || above.canBeReplaced() || above.is(BlockTags.LEAVES)) || !above.getFluidState().isEmpty() || above.is(MeetingSites.BUILT_BLOCKS)) {
						return id(above) + " over the ground at " + pos.toShortString();
					}
				}

				min = Math.min(min, stand);
				max = Math.max(max, stand);
			}
		}

		if (max - min > rules.maxSlope()) {
			return "ground varies by " + (max - min) + ", more than " + rules.maxSlope();
		}

		BlockPos centre = new BlockPos(cx, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz), cz);

		if (rules.forbidden().test(centre)) {
			return "too near a recorded site, or outside the site's biome";
		}

		for (BlockPos other : rules.avoid()) {
			if (Math.pow(other.getX() - cx, 2) + Math.pow(other.getZ() - cz, 2) < Math.pow(rules.minSeparation(), 2)) {
				return "too near " + other.toShortString();
			}
		}

		int clear = rules.clearance() / 2;

		for (int dx = -clear; dx <= clear; dx++) {
			for (int dz = -clear; dz <= clear; dz++) {
				int x = cx + dx;
				int z = cz + dz;

				if (!level.hasChunkAt(x, z)) {
					return "clearance square not loaded at " + x + ", " + z;
				}

				int stand = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);

				for (int y = stand - 1 - BUILT_BELOW; y <= stand + BUILT_ABOVE; y++) {
					BlockState state = level.getBlockState(pos.set(x, y, z));

					if (state.is(MeetingSites.BUILT_BLOCKS)) {
						return "built block " + id(state) + " in the clearance square at " + pos.toShortString();
					}
				}
			}
		}

		return "refused by MeetingSites.evaluate for a reason this report does not mirror";
	}

	/** The first check of {@link RuntimeSites#findSurface} after the ground rules that refuses a template on {@code stand}. */
	private static String spotRefusal(ServerLevel level, StructureTemplate template, BlockPos stand, Vec3 origin, Vec3 forward, MeetingSites.Rules rules,
			int buried) {
		Rotation rotation = facing(stand, origin, forward);
		BlockPos templateOrigin = RuntimeSites.templateOrigin(template, stand, rotation, buried);
		BoundingBox box = template.getBoundingBox(new StructurePlaceSettings().setRotation(rotation), templateOrigin);
		int half = rules.clearance() / 2;
		BoundingBox clearance = BoundingBox.encapsulating(box,
				new BoundingBox(stand.getX() - half, box.minY(), stand.getZ() - half, stand.getX() + half, box.maxY(), stand.getZ() + half));
		String ground = "ground passes at " + stand.toShortString();

		if (!loaded(level, clearance.inflatedBy(1))) {
			return ground + ", but the clearance box reaches a chunk that is not loaded";
		} else if (RuntimeSites.touchesWorldgenPiece(level, clearance)) {
			return ground + ", but a worldgen piece is in the clearance box";
		}

		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				for (int y = box.minY(); y <= box.maxY(); y++) {
					BlockState state = level.getBlockState(pos.set(x, y, z));

					if (state.is(MeetingSites.BUILT_BLOCKS)) {
						return ground + ", but built block " + id(state) + " is in the box at " + pos.toShortString();
					}
				}
			}
		}

		return ground + " and passes every check";
	}

	/** The turn that faces a template's entrance toward {@code origin} (or back along {@code forward} on top of it), as {@link RuntimeSites#findSurface}. */
	private static Rotation facing(BlockPos stand, Vec3 origin, Vec3 forward) {
		double dx = origin.x - (stand.getX() + 0.5);
		double dz = origin.z - (stand.getZ() + 0.5);

		if (dx * dx + dz * dz < 1) {
			dx = -forward.x;
			dz = -forward.z;
		}

		Direction toOrigin = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
		return RuntimeSites.facing(toOrigin);
	}

	private static boolean loaded(ServerLevel level, BoundingBox box) {
		return box.intersectingChunks().allMatch(chunk -> level.hasChunk(chunk.x(), chunk.z()));
	}

	private static String id(BlockState state) {
		return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
	}

	private PlanetSiteSearch() {
	}
}
