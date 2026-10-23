package com.oldrepublic.space;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.planet.Planets;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * The orbits' asteroid fields (spec MILESTONE_5.md 5.1), feature {@code oldrepublic:asteroid}: a lumpy
 * cluster 3 to 12 blocks across, built from a main body and a few smaller lumps, each a noisy
 * ellipsoid, in the orbit's rock. The placed feature tries one chunk in two between y 80 and 280; this
 * feature keeps a share of those tries so each orbit gets one cluster per
 * {@code space.asteroidDensity} chunks (the Ash Ring over Varkhast is the densest). It writes only air,
 * within 12 blocks of its origin, and draws all randomness from the context's random, so a seed fills
 * the same fields every time. Nothing is placed over the arrival column at (0, 0) or the Silent
 * Spindle's site.
 */
public class AsteroidFeature extends Feature<NoneFeatureConfiguration> {
	/** The placed feature's rarity filter: one chunk in this many is tried. */
	static final int TRY_EVERY = 2;
	/** The largest cluster, in blocks across; lumps stay inside {@link #REACH} of the origin. */
	private static final int MAX_SIZE = 12;
	private static final int MIN_SIZE = 3;
	private static final int REACH = 12;
	/** Kept clear: the arrival column (horizontal radius) and the station's site (radius around its centre). */
	private static final int ARRIVAL_CLEAR = 40;
	private static final int SPINDLE_CLEAR = 56;

	public AsteroidFeature() {
		super(NoneFeatureConfiguration.CODEC);
	}

	@Override
	public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
		WorldGenLevel level = context.level();
		RandomSource random = context.random();
		BlockPos origin = context.origin();
		Planets.Planet system = Planets.orbitOf(level.getLevel().dimension());

		if (system == null) {
			return false;
		}

		SpaceBalance b = SpaceBalance.get();

		if (!keeps(b.asteroidDensity(system), random.nextInt(Math.max(1, b.asteroidDensity(system)))) || !clearOfSites(system, origin, b)) {
			return false;
		}

		List<BlockState> rock = palette(system);
		int size = MIN_SIZE + random.nextInt(MAX_SIZE - MIN_SIZE + 1);
		float main = size / 2f;
		boolean placed = lump(level, random, origin, main, rock);
		int extra = size <= 4 ? random.nextInt(2) : 1 + random.nextInt(3);

		for (int i = 0; i < extra; i++) {
			float radius = Math.max(1.2f, main * (0.35f + random.nextFloat() * 0.35f));
			double reach = Math.min(REACH - radius, main * 0.9);
			BlockPos at = origin.offset((int) Math.round((random.nextDouble() * 2 - 1) * reach), (int) Math.round((random.nextDouble() * 2 - 1) * reach * 0.6),
					(int) Math.round((random.nextDouble() * 2 - 1) * reach));
			placed |= lump(level, random, at, radius, rock);
		}

		return placed;
	}

	/**
	 * Whether a try that passed the rarity filter keeps its cluster: {@code draw} (0 to density - 1)
	 * under {@link #TRY_EVERY}, so one cluster per {@code density} chunks on average.
	 */
	static boolean keeps(int density, int draw) {
		return draw < TRY_EVERY || density <= TRY_EVERY;
	}

	/** Whether a cluster at {@code origin} leaves the arrival column and the Spindle's site clear. */
	static boolean clearOfSites(Planets.Planet system, BlockPos origin, SpaceBalance b) {
		long arrival = (long) origin.getX() * origin.getX() + (long) origin.getZ() * origin.getZ();

		if (arrival < (long) ARRIVAL_CLEAR * ARRIVAL_CLEAR) {
			return false;
		}

		if (system != Planets.Planet.HOME || b.spindlePos.length < 3) {
			return true;
		}

		double dx = origin.getX() - b.spindlePos[0];
		double dy = origin.getY() - b.spindlePos[1];
		double dz = origin.getZ() - b.spindlePos[2];
		return dx * dx + dy * dy + dz * dz > (double) SPINDLE_CLEAR * SPINDLE_CLEAR;
	}

	/** One noisy ellipsoid of rock around {@code centre}; true when any block was written. */
	private static boolean lump(WorldGenLevel level, RandomSource random, BlockPos centre, float radius, List<BlockState> rock) {
		float rx = radius * (0.8f + random.nextFloat() * 0.4f);
		float ry = radius * (0.6f + random.nextFloat() * 0.35f);
		float rz = radius * (0.8f + random.nextFloat() * 0.4f);
		int span = Mth.ceil(Math.max(rx, Math.max(ry, rz))) + 1;
		long salt = random.nextLong();
		boolean placed = false;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		for (int dx = -span; dx <= span; dx++) {
			for (int dy = -span; dy <= span; dy++) {
				for (int dz = -span; dz <= span; dz++) {
					double d = (dx * dx) / (double) (rx * rx) + (dy * dy) / (double) (ry * ry) + (dz * dz) / (double) (rz * rz);
					// A rough surface: each block's own threshold wobbles, more at the rim than inside.
					double wobble = 0.82 + 0.36 * hash(salt, dx >> 1, dy >> 1, dz >> 1);

					if (d > wobble) {
						continue;
					}

					pos.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);

					if (!level.getBlockState(pos).isAir()) {
						continue;
					}

					BlockState state = pick(rock, hash(salt ^ 0x5DEECE66DL, dx, dy, dz), d / wobble);

					if (state != null) {
						level.setBlock(pos, state, Block.UPDATE_CLIENTS);
						placed = true;
					}
				}
			}
		}

		return placed;
	}

	/** The rock at a point: the first entries of the palette in the core, the later (the orbit's own) more often near the rim. */
	private static @Nullable BlockState pick(List<BlockState> rock, double noise, double depth) {
		if (rock.isEmpty()) {
			return null;
		}

		double t = Mth.clamp(noise * 0.75 + depth * 0.45, 0, 0.999);
		return rock.get((int) (t * rock.size()));
	}

	/** A deterministic value in [0, 1) for a lattice point. */
	private static double hash(long salt, int x, int y, int z) {
		long h = salt ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ (z * 0x165667B19E3779F9L);
		h ^= h >>> 33;
		h *= 0xFF51AFD7ED558CCDL;
		h ^= h >>> 33;
		return (h >>> 11) * 0x1.0p-53;
	}

	/** Each orbit's rock, from the core outward (5.1). */
	static List<BlockState> palette(Planets.Planet system) {
		return switch (system) {
			case VARKHAST -> List.of(Blocks.DEEPSLATE.defaultBlockState(), Blocks.BLACKSTONE.defaultBlockState(), Blocks.BASALT.defaultBlockState(),
					Blocks.TUFF.defaultBlockState(), Blocks.BLACKSTONE.defaultBlockState(), Blocks.ANDESITE.defaultBlockState());
			case GHESSAR -> List.of(Blocks.STONE.defaultBlockState(), Blocks.ANDESITE.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState(),
					Blocks.TUFF.defaultBlockState(), Blocks.CALCITE.defaultBlockState(), Blocks.SMOOTH_SANDSTONE.defaultBlockState());
			case TYRVANN -> List.of(Blocks.DEEPSLATE.defaultBlockState(), Blocks.BASALT.defaultBlockState(), Blocks.TUFF.defaultBlockState(),
					Blocks.SMOOTH_BASALT.defaultBlockState(), Blocks.BASALT.defaultBlockState(), Blocks.STONE.defaultBlockState());
			default -> List.of(Blocks.DEEPSLATE.defaultBlockState(), Blocks.STONE.defaultBlockState(), Blocks.ANDESITE.defaultBlockState(),
					Blocks.TUFF.defaultBlockState(), Blocks.BASALT.defaultBlockState(), Blocks.STONE.defaultBlockState());
		};
	}
}
