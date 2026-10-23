package com.oldrepublic.quest.story;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Finds open high ground for story sites: the Crossing's meeting point, the mentor camp (the same
 * place) and Smoke on the Ridge's raider camp (spec section 5.1). Only chunks that are already
 * loaded are looked at, so a search never generates terrain.
 *
 * <p>A candidate passes when its footprint is natural ground ({@code #oldrepublic:story_ground})
 * with no fluid, varies in height by at most the allowed slope, has nothing but air or natural
 * growth in the few blocks above, is not protected, keeps its distance from other players' sites,
 * and a wider clearance square holds no player-made block ({@code #oldrepublic:built_blocks}). The
 * best candidate stands highest above its ring's average and sees the most sky.
 */
public final class MeetingSites {
	public static final TagKey<Block> STORY_GROUND = TagKey.create(Registries.BLOCK, OldRepublicMod.id("story_ground"));
	public static final TagKey<Block> BUILT_BLOCKS = TagKey.create(Registries.BLOCK, OldRepublicMod.id("built_blocks"));

	/** Score weight of a footprint that sees the sky everywhere, against one block of height. */
	private static final double OPENNESS_WEIGHT = 4.0;
	/** Blocks above the stand height that must be free (air or natural growth). */
	private static final int HEADROOM = 3;
	/** Built blocks are looked for from this far below to this far above each column's ground. */
	private static final int BUILT_BELOW = 2;
	private static final int BUILT_ABOVE = 5;
	private static final int FALLBACK_STEP = 8;

	/**
	 * What a site needs.
	 *
	 * @param footprint     side of the square that must be flat natural ground
	 * @param clearance     side of the square that must hold no built blocks
	 * @param maxSlope      allowed height difference across the footprint
	 * @param forbidden     true for stand positions that may not be used (spawn protection)
	 * @param avoid         other sites to keep away from
	 * @param minSeparation minimum horizontal distance to every {@code avoid} position
	 */
	public record Rules(int footprint, int clearance, int maxSlope, Predicate<BlockPos> forbidden, List<BlockPos> avoid, int minSeparation) {
	}

	/** A site that passed: the centre's stand position (feet), the mean stand height and the sky fraction. */
	public record Candidate(BlockPos stand, double meanHeight, double openness) {
	}

	/**
	 * Searches rings around {@code origin} (all of {@code rings} together), then the fallback range
	 * in steps, then gives a spot {@code forwardFallback} blocks along {@code forward}. Never returns
	 * null, so the story can always continue.
	 */
	public static BlockPos find(Level level, Vec3 origin, Vec3 forward, int[] rings, int[] fallback, int angles, int forwardFallback, Rules rules) {
		double phase = (Math.floorMod((long) origin.x * 31 + (long) origin.z * 17, 360)) * Math.PI / 180.0;
		Candidate best = bestOnRings(level, origin, rings, angles, phase, rules);

		if (best == null && fallback.length >= 2) {
			List<Integer> steps = new ArrayList<>();

			for (int r = fallback[0]; r <= fallback[1]; r += FALLBACK_STEP) {
				steps.add(r);
			}

			best = bestOnRings(level, origin, steps.stream().mapToInt(Integer::intValue).toArray(), angles, phase, rules);
		}

		if (best != null) {
			return best.stand();
		}

		Vec3 flat = forward.horizontal();
		Vec3 dir = flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
		int x = (int) Math.floor(origin.x + dir.x * forwardFallback);
		int z = (int) Math.floor(origin.z + dir.z * forwardFallback);
		return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
	}

	private static @Nullable Candidate bestOnRings(Level level, Vec3 origin, int[] rings, int angles, double phase, Rules rules) {
		Candidate best = null;
		double bestScore = Double.NEGATIVE_INFINITY;

		for (int ring : rings) {
			List<Candidate> passed = new ArrayList<>();

			for (int i = 0; i < Math.max(1, angles); i++) {
				double angle = phase + i * Math.PI * 2 / Math.max(1, angles);
				int cx = (int) Math.floor(origin.x + Math.cos(angle) * ring);
				int cz = (int) Math.floor(origin.z + Math.sin(angle) * ring);
				Candidate candidate = evaluate(level, cx, cz, rules);

				if (candidate != null) {
					passed.add(candidate);
				}
			}

			double average = passed.stream().mapToDouble(Candidate::meanHeight).average().orElse(0);

			for (Candidate candidate : passed) {
				double score = candidate.meanHeight() - average + candidate.openness() * OPENNESS_WEIGHT;

				if (score > bestScore) {
					bestScore = score;
					best = candidate;
				}
			}
		}

		return best;
	}

	/** Checks one candidate centred on column ({@code cx}, {@code cz}); null if it fails. */
	public static @Nullable Candidate evaluate(Level level, int cx, int cz, Rules rules) {
		int half = rules.footprint() / 2;
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		long sum = 0;
		int open = 0;
		int columns = 0;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		for (int dx = -half; dx <= half; dx++) {
			for (int dz = -half; dz <= half; dz++) {
				int x = cx + dx;
				int z = cz + dz;

				if (!level.hasChunkAt(x, z)) {
					return null;
				}

				int stand = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				BlockState ground = level.getBlockState(pos.set(x, stand - 1, z));

				if (!ground.is(STORY_GROUND) || !ground.getFluidState().isEmpty()) {
					return null;
				}

				for (int y = stand; y < stand + HEADROOM; y++) {
					if (!isFreeAbove(level.getBlockState(pos.set(x, y, z)))) {
						return null;
					}
				}

				if (level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) <= stand) {
					open++;
				}

				min = Math.min(min, stand);
				max = Math.max(max, stand);
				sum += stand;
				columns++;
			}
		}

		if (columns == 0 || max - min > rules.maxSlope()) {
			return null;
		}

		int centreStand = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz);
		BlockPos centre = new BlockPos(cx, centreStand, cz);

		if (rules.forbidden().test(centre)) {
			return null;
		}

		for (BlockPos other : rules.avoid()) {
			double ddx = other.getX() - cx;
			double ddz = other.getZ() - cz;

			if (ddx * ddx + ddz * ddz < (double) rules.minSeparation() * rules.minSeparation()) {
				return null;
			}
		}

		if (hasBuiltBlocks(level, cx, cz, rules.clearance() / 2, pos)) {
			return null;
		}

		return new Candidate(centre, sum / (double) columns, open / (double) columns);
	}

	/** Natural things a site may replace: air, plants, snow layers, leaves. Logs and anything built are not. */
	static boolean isFreeAbove(BlockState state) {
		return (state.isAir() || state.canBeReplaced() || state.is(BlockTags.LEAVES)) && state.getFluidState().isEmpty() && !state.is(BUILT_BLOCKS);
	}

	private static boolean hasBuiltBlocks(Level level, int cx, int cz, int half, BlockPos.MutableBlockPos pos) {
		for (int dx = -half; dx <= half; dx++) {
			for (int dz = -half; dz <= half; dz++) {
				int x = cx + dx;
				int z = cz + dz;

				if (!level.hasChunkAt(x, z)) {
					return true;
				}

				int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);

				for (int y = ground - 1 - BUILT_BELOW; y <= ground + BUILT_ABOVE; y++) {
					if (level.getBlockState(pos.set(x, y, z)).is(BUILT_BLOCKS)) {
						return true;
					}
				}
			}
		}

		return false;
	}

	private MeetingSites() {
	}
}
