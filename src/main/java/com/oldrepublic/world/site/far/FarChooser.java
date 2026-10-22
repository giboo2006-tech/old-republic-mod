package com.oldrepublic.world.site.far;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * The CHOOSING phase of far placement (docs/MILESTONE_6.md 3.1, step 1): candidates on rings in a
 * distance band, each scored from the world generator's own ground ({@link Ground}) without loading
 * a chunk. Deterministic (seeded by {@link #seed}) and time-sliced: {@link #step} spends at most
 * about the budget it is given, in height samples, and the chooser picks up where it stopped.
 *
 * <p>A candidate is refused when a column under its footprint is wet (fluid over the ground), when
 * it lies outside the request's biomes, too near a point it keeps away from ({@link Keep}), or when
 * its ground varies by more than {@value #MAX_SPREAD} blocks. The others are scored: the spread of the
 * ground under the footprint costs (three times as much for {@link FarSites.Terrain#FLAT}), height
 * over the ground around pays for {@link FarSites.Terrain#HIGH} and costs for
 * {@link FarSites.Terrain#LOW}, and a candidate outside the preferred bearing's spread costs. When a
 * pass ends, its candidates are tried best first against the structure check (a worldgen structure
 * that could start nearby refuses one); the first that passes is the target.
 *
 * <p>The passes: the band within the bearing's spread (when the request has one), the whole band,
 * then once more from the band's outer edge to twice as far. When none of them gives a target the
 * chooser ends without one ("no dry ground").
 */
public final class FarChooser {
	/**
	 * The world generator's ground as far placement reads it. The game reads the noise generator
	 * ({@code NoiseGround}); tests pass a synthetic field.
	 */
	public interface Ground {
		/** The first free height over a column's ground or over the fluid on it ({@code WORLD_SURFACE_WG}). */
		int surface(int x, int z);

		/** The first free height over a column's ground, under any fluid ({@code OCEAN_FLOOR_WG}): the stand. */
		int floor(int x, int z);

		/** Whether the biome at this point is one the request keeps to (always true when it keeps to none). */
		boolean biomeAllowed(int x, int y, int z);

		/** Whether a worldgen structure could start close enough to this point to touch a site there. */
		boolean structureNear(int x, int y, int z);
	}

	/** A point a target keeps at least {@code distance} blocks from (horizontally): another site, spawn, a refused target. */
	public record Keep(BlockPos pos, int distance) {
		public Keep {
			pos = pos.immutable();
		}
	}

	/**
	 * What to look for.
	 *
	 * @param from          the band's centre
	 * @param footprint     the side of the square the site stands on
	 * @param bearing       the preferred direction from {@code from} (only x and z count), or null
	 * @param spreadDegrees the width of the preferred bearings (360: no preference)
	 * @param keep          the points a target keeps away from
	 * @param seed          {@link #seed} of the request's key and the world's seed
	 * @param anglesPerRing candidates on each ring ({@code far.anglesPerRing})
	 * @param ringsPerBand  rings across the band ({@code far.ringsPerBand})
	 */
	public record Spec(BlockPos from, int minDistance, int maxDistance, int footprint, FarSites.Terrain terrain, @Nullable Vec3 bearing, float spreadDegrees,
			List<Keep> keep, long seed, int anglesPerRing, int ringsPerBand) {
		public Spec {
			from = from.immutable();
			keep = List.copyOf(keep);
			anglesPerRing = Math.max(1, anglesPerRing);
			ringsPerBand = Math.max(1, ringsPerBand);
		}
	}

	/** The chosen ground point (its y is the stand) and its score. */
	public record Choice(BlockPos target, double score) {
	}

	/** A candidate that passed its ground checks, with the order it was met in (ties go to the first). */
	private record Scored(int x, int y, int z, double score, int order) {
	}

	/** Above this spread of the ground under its footprint a candidate is a cliff, not a site. */
	public static final int MAX_SPREAD = 40;
	/** Score cost of one block of spread, and its multiple for {@link FarSites.Terrain#FLAT}. */
	private static final double SPREAD_WEIGHT = 1.5;
	private static final double FLAT_FACTOR = 3.0;
	/** How far outside the footprint the ground around is sampled for HIGH and LOW, and the most relief that counts. */
	private static final int RELIEF_REACH = 24;
	private static final double MAX_RELIEF = 24;
	/** Score cost of every 45 degrees a candidate lies outside the preferred bearings. */
	private static final double BEARING_WEIGHT = 2.0;
	/** Samples a structure check counts as (it looks at a few dozen chunks of several structure sets). */
	static final int STRUCTURE_COST = 8;
	private static final double TWO_PI = Math.PI * 2;
	/** The footprint's sample points in half-footprint units: centre, corners, edge middles. */
	private static final int[][] FOOTPRINT_POINTS = {{0, 0}, {-1, -1}, {1, -1}, {-1, 1}, {1, 1}, {0, -1}, {1, 0}, {0, 1}, {-1, 0}};
	/** The ground around a footprint for HIGH and LOW: eight directions. */
	private static final int[][] RING_POINTS = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};

	private final Spec spec;
	private final List<double[]> passes = new ArrayList<>();
	private final List<Scored> scored = new ArrayList<>();
	private int pass;
	private int index;
	private boolean sorted;
	private int checking;
	private boolean done;
	private @Nullable Choice choice;
	private double[] radii = new double[0];
	private double[] phases = new double[0];

	public FarChooser(Spec spec) {
		this.spec = spec;
		double inner = spec.minDistance();
		double outer = Math.max(inner, spec.maxDistance());

		if (spec.bearing() != null && spec.spreadDegrees() < 360f && spec.bearing().horizontalDistanceSqr() > 1.0e-6) {
			passes.add(new double[] {inner, outer, Math.toRadians(Math.max(0f, spec.spreadDegrees()))});
		}

		passes.add(new double[] {inner, outer, TWO_PI});
		passes.add(new double[] {outer, Math.max(outer * 2, outer + 64), TWO_PI});
		startPass();
	}

	/** A seed for a request: its key mixed with the world's seed (stable across runs and machines). */
	public static long seed(String key, long worldSeed) {
		return mix(mix(worldSeed) ^ key.hashCode() * 0x9E3779B97F4A7C15L);
	}

	/** Runs a chooser to its end at once (the tests, and debug commands). */
	public static @Nullable Choice choose(Spec spec, Ground ground) {
		FarChooser chooser = new FarChooser(spec);

		while (!chooser.done()) {
			chooser.step(ground, Integer.MAX_VALUE);
		}

		return chooser.choice();
	}

	/**
	 * Works on until about {@code budget} height samples are spent or the choice is made, and returns
	 * the samples spent (a candidate is never split, so the last one may overshoot the budget).
	 */
	public int step(Ground ground, int budget) {
		int used = 0;

		while (!done && used < budget) {
			if (index < candidates()) {
				used += evaluate(ground, index++);
			} else if (!sorted) {
				scored.sort(Comparator.comparingDouble(Scored::score).reversed().thenComparingInt(Scored::order));
				sorted = true;
			} else if (checking < scored.size()) {
				Scored best = scored.get(checking++);
				used += STRUCTURE_COST;

				if (!ground.structureNear(best.x(), best.y(), best.z())) {
					choice = new Choice(new BlockPos(best.x(), best.y(), best.z()), best.score());
					done = true;
				}
			} else if (++pass < passes.size()) {
				startPass();
			} else {
				done = true;
			}
		}

		return used;
	}

	/** Whether the chooser has ended (with a choice, or without one). */
	public boolean done() {
		return done;
	}

	/** The target, once {@link #done}; null when no candidate passed. */
	public @Nullable Choice choice() {
		return choice;
	}

	private int candidates() {
		return spec.ringsPerBand() * spec.anglesPerRing();
	}

	/** Lays out the rings of the current pass: radii across its band and a seeded turn for each ring. */
	private void startPass() {
		double[] band = passes.get(pass);
		Random random = new Random(mix(spec.seed() + pass * 0x632BE59BD9B4E019L));
		int rings = spec.ringsPerBand();
		radii = new double[rings];
		phases = new double[rings];

		for (int r = 0; r < rings; r++) {
			double width = (band[1] - band[0]) / rings;
			radii[r] = band[0] + width * (r + 0.25 + random.nextDouble() * 0.5);
			phases[r] = random.nextDouble();
		}

		index = 0;
		scored.clear();
		sorted = false;
		checking = 0;
	}

	/** Checks and scores one candidate, keeping it when it passes; returns the samples it took. */
	private int evaluate(Ground ground, int candidate) {
		double[] band = passes.get(pass);
		int ring = candidate / spec.anglesPerRing();
		int slot = candidate % spec.anglesPerRing();
		double width = band[2];
		double angle;

		if (width < TWO_PI) {
			angle = centre() - width / 2 + width * (slot + phases[ring]) / spec.anglesPerRing();
		} else {
			angle = TWO_PI * (slot + phases[ring]) / spec.anglesPerRing();
		}

		int cx = (int) Math.floor(spec.from().getX() + 0.5 + Math.cos(angle) * radii[ring]);
		int cz = (int) Math.floor(spec.from().getZ() + 0.5 + Math.sin(angle) * radii[ring]);

		if (tooClose(cx, cz)) {
			return 0;
		}

		int half = Math.max(2, spec.footprint() / 2);
		int samples = 0;
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		long sum = 0;
		int centreFloor = 0;

		// The centre first, then the corners, then the edges' middles: a wet or steep square shows early.
		for (int[] offset : FOOTPRINT_POINTS) {
			int x = cx + offset[0] * half;
			int z = cz + offset[1] * half;
			int floor = ground.floor(x, z);
			int surface = ground.surface(x, z);
			samples += 2;

			if (surface > floor) {
				return samples;
			}

			if (offset[0] == 0 && offset[1] == 0) {
				centreFloor = floor;
			}

			min = Math.min(min, floor);
			max = Math.max(max, floor);
			sum += floor;

			if (max - min > MAX_SPREAD) {
				return samples;
			}
		}

		samples++;

		if (!ground.biomeAllowed(cx, centreFloor, cz)) {
			return samples;
		}

		int spread = max - min;
		double mean = sum / (double) FOOTPRINT_POINTS.length;
		double score = -spread * SPREAD_WEIGHT * (spec.terrain() == FarSites.Terrain.FLAT ? FLAT_FACTOR : 1.0);

		if (spec.terrain() == FarSites.Terrain.HIGH || spec.terrain() == FarSites.Terrain.LOW) {
			long around = 0;

			for (int[] offset : RING_POINTS) {
				around += ground.floor(cx + offset[0] * (half + RELIEF_REACH), cz + offset[1] * (half + RELIEF_REACH));
				samples++;
			}

			double relief = Math.max(-MAX_RELIEF, Math.min(MAX_RELIEF, mean - around / (double) RING_POINTS.length));
			score += spec.terrain() == FarSites.Terrain.HIGH ? relief : -relief;
		}

		score -= bearingCost(angle);
		scored.add(new Scored(cx, centreFloor, cz, score, candidate));
		return samples;
	}

	/** The cost of a candidate's bearing: nothing within the preferred spread, then {@link #BEARING_WEIGHT} per 45 degrees outside. */
	private double bearingCost(double angle) {
		if (spec.bearing() == null || spec.spreadDegrees() >= 360f || spec.bearing().horizontalDistanceSqr() < 1.0e-6) {
			return 0;
		}

		double off = Math.abs(Math.IEEEremainder(angle - centre(), TWO_PI));
		double outside = Math.max(0, off - Math.toRadians(spec.spreadDegrees()) / 2);
		return BEARING_WEIGHT * outside / (Math.PI / 4);
	}

	/** The preferred bearing as an angle (the same convention as the candidates: x by cos, z by sin). */
	private double centre() {
		Vec3 bearing = spec.bearing();
		return bearing == null ? 0 : Math.atan2(bearing.z, bearing.x);
	}

	private boolean tooClose(int x, int z) {
		for (Keep keep : spec.keep()) {
			long dx = keep.pos().getX() - x;
			long dz = keep.pos().getZ() - z;

			if (dx * dx + dz * dz < (long) keep.distance() * keep.distance()) {
				return true;
			}
		}

		return false;
	}

	/** SplitMix64's finaliser: spreads a seed's bits so nearby keys give unrelated layouts. */
	private static long mix(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}
}
