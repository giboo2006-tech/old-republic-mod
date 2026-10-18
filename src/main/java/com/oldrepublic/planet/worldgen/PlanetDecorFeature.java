package com.oldrepublic.planet.worldgen;

import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.planet.PlanetBlocks;
import com.oldrepublic.world.block.CrystalFormationBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.material.Fluids;

/**
 * The planets' hand-written decorations (spec MILESTONE_4.md 3.2, 3.3), one feature type per
 * {@link Kind}, each registered as {@code oldrepublic:<kind>} and used by the configured feature of
 * the same name. The vanilla feature types cover trees, plants and ores; these cover what they
 * cannot: shade-loving plants, stones, spires, carved walls and crystal walls.
 *
 * <p>Every kind writes within 7 blocks of its origin (inside the decoration region), reads the
 * world-gen heightmaps, and draws all randomness from the context's random, so a seed decorates the
 * same way every time. No kind lights anything, and none draws a pattern on the ground.
 */
public class PlanetDecorFeature extends Feature<NoneFeatureConfiguration> {
	/** The decorations, by configured feature id. */
	public enum Kind {
		/** Selvaren highwood: moss carpets and podzol under the trees. */
		HIGHWOOD_FLOOR,
		/** Selvaren meadow: a weathered standing stone in a low outcrop. */
		MEADOW_STONES,
		/** Silverleaf in the shade of the highwood (ground with a canopy above). */
		SILVERLEAF_PATCH,
		/** Tarn shores: lily pads on shallow still water, mossy shore rocks. */
		TARN_SHORE,
		/** Aurelite veins in exposed stone faces. */
		AURELITE_CLIFF,
		/** Varkhast redrock: a banded hoodoo with a wider cap. */
		HOODOO,
		/** Varkhast ashfall: a basalt spire on a tuff foot. */
		BASALT_SPIRE,
		/** Tomb valley: a blackstone obelisk on a sunken plinth. */
		OBELISK,
		/** Tomb valley: a carved panel cut into a cliff face. */
		CARVED_WALL,
		/** Varkhast: a dead, leafless tree. */
		DEAD_TREE,
		/** Ashroot in shaded gullies. */
		ASHROOT_PATCH,
		/** Duskglass growing on shaded walls: a budding block with clusters around it. */
		DUSKGLASS_WALL;

		/** The registry path ({@code highwood_floor}, ...). */
		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	private static final List<Direction> SIDES = List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);

	private final Kind kind;

	public PlanetDecorFeature(Kind kind) {
		super(NoneFeatureConfiguration.CODEC);
		this.kind = kind;
	}

	public Kind kind() {
		return kind;
	}

	@Override
	public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
		WorldGenLevel level = context.level();
		BlockPos origin = context.origin();
		RandomSource random = context.random();

		return switch (kind) {
			case HIGHWOOD_FLOOR -> highwoodFloor(level, origin, random);
			case MEADOW_STONES -> meadowStone(level, origin, random);
			case SILVERLEAF_PATCH -> plants(level, origin, random, PlanetBlocks.SILVERLEAF.defaultBlockState(), 10, true);
			case TARN_SHORE -> tarnShore(level, origin, random);
			case AURELITE_CLIFF -> aureliteCliff(level, origin, random);
			case HOODOO -> hoodoo(level, origin, random);
			case BASALT_SPIRE -> basaltSpire(level, origin, random);
			case OBELISK -> obelisk(level, origin, random);
			case CARVED_WALL -> carvedWall(level, origin, random);
			case DEAD_TREE -> deadTree(level, origin, random);
			case ASHROOT_PATCH -> plants(level, origin, random, PlanetBlocks.ASHROOT.defaultBlockState(), 8, false);
			case DUSKGLASS_WALL -> duskglassWall(level, origin, random);
		};
	}

	// ------------------------------------------------------------------------------------------- Selvaren

	private static boolean highwoodFloor(WorldGenLevel level, BlockPos origin, RandomSource random) {
		boolean placed = false;

		for (int i = 0; i < 14; i++) {
			BlockPos stand = surface(level, origin.offset(random.nextInt(9) - 4, 0, random.nextInt(9) - 4));
			BlockState ground = level.getBlockState(stand.below());

			if (!ground.is(Blocks.GRASS_BLOCK) || !level.getBlockState(stand).isAir()) {
				continue;
			}

			if (random.nextInt(4) == 0) {
				set(level, stand.below(), random.nextBoolean() ? Blocks.PODZOL.defaultBlockState() : Blocks.MOSS_BLOCK.defaultBlockState());
			} else {
				set(level, stand, Blocks.MOSS_CARPET.defaultBlockState());
			}

			placed = true;
		}

		return placed;
	}

	private static boolean meadowStone(WorldGenLevel level, BlockPos origin, RandomSource random) {
		BlockPos stand = surface(level, origin);

		if (!level.getBlockState(stand.below()).is(BlockTags.DIRT) || !level.getFluidState(stand).isEmpty()) {
			return false;
		}

		int height = 2 + random.nextInt(3);

		for (int y = -1; y < height; y++) {
			BlockState stone = y == height - 1 && random.nextBoolean() ? Blocks.MOSSY_COBBLESTONE.defaultBlockState()
					: random.nextInt(4) == 0 ? Blocks.ANDESITE.defaultBlockState() : Blocks.STONE.defaultBlockState();
			set(level, stand.above(y), stone);
		}

		for (int i = 0; i < 5 + random.nextInt(4); i++) {
			BlockPos rock = surface(level, stand.offset(random.nextInt(5) - 2, 0, random.nextInt(5) - 2));

			if (level.getBlockState(rock.below()).is(BlockTags.DIRT) && level.getFluidState(rock).isEmpty()) {
				set(level, rock.below(), random.nextBoolean() ? Blocks.STONE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState());

				if (random.nextInt(3) == 0 && level.getBlockState(rock).canBeReplaced()) {
					set(level, rock, Blocks.MOSSY_COBBLESTONE.defaultBlockState());
				}
			}
		}

		return true;
	}

	private static boolean tarnShore(WorldGenLevel level, BlockPos origin, RandomSource random) {
		boolean placed = false;

		for (int i = 0; i < 12; i++) {
			int x = origin.getX() + random.nextInt(13) - 6;
			int z = origin.getZ() + random.nextInt(13) - 6;
			BlockPos top = new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z), z);
			BlockPos water = top.below();

			if (level.getFluidState(water).is(Fluids.WATER) && level.getFluidState(water).isSource() && level.getBlockState(top).isAir()) {
				if (random.nextInt(3) == 0) {
					set(level, top, Blocks.LILY_PAD.defaultBlockState());
					placed = true;
				}
			} else if (level.getBlockState(water).is(Blocks.GRAVEL) && level.getBlockState(top).isAir() && random.nextInt(4) == 0) {
				set(level, top, random.nextBoolean() ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState());
				placed = true;
			}
		}

		return placed;
	}

	private static boolean aureliteCliff(WorldGenLevel level, BlockPos origin, RandomSource random) {
		for (int i = 0; i < 16; i++) {
			BlockPos pos = origin.offset(random.nextInt(9) - 4, random.nextInt(9) - 4, random.nextInt(9) - 4);
			Direction open = openSide(level, pos, Blocks.STONE.defaultBlockState(), random);

			if (open == null) {
				continue;
			}

			// A short vein along the face: the exposed block and up to two neighbours in the wall plane.
			set(level, pos, PlanetBlocks.AURELITE_ORE.defaultBlockState());
			Direction along = open.getClockWise();

			for (int j = 0; j < random.nextInt(3); j++) {
				BlockPos next = pos.relative(random.nextBoolean() ? along : Direction.UP, j + 1);

				if (level.getBlockState(next).is(Blocks.STONE)) {
					set(level, next, PlanetBlocks.AURELITE_ORE.defaultBlockState());
				}
			}

			return true;
		}

		return false;
	}

	// ------------------------------------------------------------------------------------------- Varkhast

	private static final List<BlockState> BANDS = List.of(Blocks.TERRACOTTA.defaultBlockState(), Blocks.ORANGE_TERRACOTTA.defaultBlockState(),
			Blocks.RED_SANDSTONE.defaultBlockState(), Blocks.RED_TERRACOTTA.defaultBlockState(), Blocks.BROWN_TERRACOTTA.defaultBlockState(),
			Blocks.RED_SANDSTONE.defaultBlockState());

	private static boolean hoodoo(WorldGenLevel level, BlockPos origin, RandomSource random) {
		BlockPos stand = surface(level, origin);

		if (!solidGround(level, stand.below())) {
			return false;
		}

		int height = 4 + random.nextInt(6);
		boolean wide = random.nextInt(3) == 0;
		int band = random.nextInt(BANDS.size());

		for (int y = -2; y < height; y++) {
			BlockState state = BANDS.get(Math.floorMod(band + y / 2, BANDS.size()));
			set(level, stand.above(y), state);

			if (wide && y < height / 2) {
				set(level, stand.above(y).east(), state);
				set(level, stand.above(y).south(), state);
			}
		}

		BlockPos cap = stand.above(height);

		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (Math.abs(dx) + Math.abs(dz) < 2 || random.nextBoolean()) {
					set(level, cap.offset(dx, 0, dz), Blocks.SMOOTH_RED_SANDSTONE.defaultBlockState());
				}
			}
		}

		return true;
	}

	private static boolean basaltSpire(WorldGenLevel level, BlockPos origin, RandomSource random) {
		BlockPos stand = surface(level, origin);

		if (!solidGround(level, stand.below())) {
			return false;
		}

		int height = 3 + random.nextInt(8);
		BlockState basalt = Blocks.BASALT.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);

		for (int y = -2; y < height; y++) {
			set(level, stand.above(y), basalt);
		}

		for (Direction side : SIDES) {
			int foot = random.nextInt(Math.max(1, height / 2));

			for (int y = -2; y < foot; y++) {
				set(level, stand.relative(side).above(y), y < 0 || random.nextBoolean() ? Blocks.TUFF.defaultBlockState() : Blocks.SMOOTH_BASALT.defaultBlockState());
			}
		}

		return true;
	}

	private static boolean obelisk(WorldGenLevel level, BlockPos origin, RandomSource random) {
		BlockPos stand = surface(level, origin);

		if (!solidGround(level, stand.below())) {
			return false;
		}

		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				for (int y = -3; y < 0; y++) {
					set(level, stand.offset(dx, y, dz), Blocks.POLISHED_BLACKSTONE.defaultBlockState());
				}

				// The plinth shows only as a broken step around the shaft.
				if ((dx != 0 || dz != 0) && random.nextInt(3) > 0) {
					set(level, stand.offset(dx, 0, dz), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
				}
			}
		}

		int height = 6 + random.nextInt(4);

		for (int y = 0; y < height; y++) {
			BlockState state = y == height - 2 ? Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState()
					: random.nextInt(6) == 0 ? Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState() : Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
			set(level, stand.above(y), state);
		}

		set(level, stand.above(height), Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState());
		return true;
	}

	private static boolean carvedWall(WorldGenLevel level, BlockPos origin, RandomSource random) {
		for (int i = 0; i < 16; i++) {
			BlockPos pos = origin.offset(random.nextInt(9) - 4, random.nextInt(9) - 4, random.nextInt(9) - 4);
			Direction open = openSide(level, pos, null, random);

			if (open == null || !carvable(level.getBlockState(pos))) {
				continue;
			}

			// A panel 3 wide and 4 high cut into the face: cut stone with chiselled columns, a dark
			// niche at its heart. Only blocks that are part of the cliff are replaced.
			Direction along = open.getClockWise();
			BlockPos base = pos.relative(along, -1);

			for (int a = 0; a < 3; a++) {
				for (int y = 0; y < 4; y++) {
					BlockPos cell = base.relative(along, a).above(y);

					if (!carvable(level.getBlockState(cell))) {
						continue;
					}

					BlockState state;

					if (a == 1 && (y == 1 || y == 2)) {
						state = Blocks.AIR.defaultBlockState();
						set(level, cell.relative(open.getOpposite()), Blocks.POLISHED_BLACKSTONE.defaultBlockState());
					} else if (a == 1) {
						state = Blocks.SMOOTH_SANDSTONE.defaultBlockState();
					} else {
						state = y == 3 ? Blocks.CHISELED_RED_SANDSTONE.defaultBlockState() : Blocks.CUT_RED_SANDSTONE.defaultBlockState();
					}

					set(level, cell, state);
				}
			}

			return true;
		}

		return false;
	}

	private static boolean deadTree(WorldGenLevel level, BlockPos origin, RandomSource random) {
		BlockPos stand = surface(level, origin);

		if (!solidGround(level, stand.below()) || !level.getFluidState(stand).isEmpty()) {
			return false;
		}

		int height = 3 + random.nextInt(4);
		BlockState trunk = Blocks.PALE_OAK_LOG.defaultBlockState();

		for (int y = 0; y < height; y++) {
			set(level, stand.above(y), trunk);
		}

		for (int i = 0; i < 1 + random.nextInt(3); i++) {
			Direction side = SIDES.get(random.nextInt(SIDES.size()));
			BlockPos branch = stand.above(height - 1 - random.nextInt(2)).relative(side);
			BlockState log = trunk.setValue(RotatedPillarBlock.AXIS, side.getAxis());

			for (int j = 0; j < 1 + random.nextInt(2); j++) {
				if (level.getBlockState(branch).isAir()) {
					set(level, branch, log);
				}

				branch = branch.relative(side).above(random.nextInt(2));
			}
		}

		return true;
	}

	private static boolean duskglassWall(WorldGenLevel level, BlockPos origin, RandomSource random) {
		for (int i = 0; i < 16; i++) {
			BlockPos pos = origin.offset(random.nextInt(9) - 4, random.nextInt(9) - 4, random.nextInt(9) - 4);
			Direction open = openSide(level, pos, null, random);

			if (open == null || !carvable(level.getBlockState(pos)) || !shaded(level, pos.relative(open))) {
				continue;
			}

			set(level, pos, PlanetBlocks.BUDDING_DUSKGLASS.defaultBlockState());
			grow(level, pos, open);
			Direction along = open.getClockWise();

			// A few more clusters on the wall around the budding block.
			for (int j = 0; j < 2 + random.nextInt(4); j++) {
				BlockPos wall = pos.relative(along, random.nextInt(5) - 2).above(random.nextInt(5) - 2);

				if (carvable(level.getBlockState(wall)) && level.getBlockState(wall.relative(open)).isAir()) {
					grow(level, wall, open);
				}
			}

			return true;
		}

		return false;
	}

	/** A cluster on {@code wall}'s {@code open} face. */
	private static void grow(WorldGenLevel level, BlockPos wall, Direction open) {
		BlockPos at = wall.relative(open);

		if (level.getBlockState(at).isAir()) {
			set(level, at, PlanetBlocks.DUSKGLASS_CLUSTER.defaultBlockState().setValue(CrystalFormationBlock.FACING, open));
		}
	}

	// ------------------------------------------------------------------------------------------- shared

	/** Silverleaf (in canopy shade) or ashroot (in gully shade): {@code tries} attempts around the origin. */
	private static boolean plants(WorldGenLevel level, BlockPos origin, RandomSource random, BlockState plant, int tries, boolean canopy) {
		boolean placed = false;

		for (int i = 0; i < tries; i++) {
			BlockPos stand = surface(level, origin.offset(random.nextInt(11) - 5, 0, random.nextInt(11) - 5));

			if (!level.getBlockState(stand).isAir() || !plant.canSurvive(level, stand)) {
				continue;
			}

			if (canopy ? underCanopy(level, stand) : inGully(level, stand)) {
				set(level, stand, plant);
				placed = true;
			}
		}

		return placed;
	}

	/** The feet position on the natural ground of a column (the first cell above the top solid block, leaves ignored). */
	private static BlockPos surface(WorldGenLevel level, BlockPos column) {
		return new BlockPos(column.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ()), column.getZ());
	}

	/** Something stands over this cell (leaves or a trunk): the canopy heightmap is above it. */
	private static boolean underCanopy(WorldGenLevel level, BlockPos stand) {
		return level.getHeight(Heightmap.Types.MOTION_BLOCKING, stand.getX(), stand.getZ()) > stand.getY() + 1;
	}

	/** A cell in a gully: ground at least 3 blocks higher on two sides within 3 blocks. */
	private static boolean inGully(WorldGenLevel level, BlockPos stand) {
		int walls = 0;

		for (Direction side : SIDES) {
			for (int d = 1; d <= 3; d++) {
				BlockPos column = stand.relative(side, d);

				if (level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ()) >= stand.getY() + 3) {
					walls++;
					break;
				}
			}
		}

		return walls >= 2;
	}

	/** An air cell with a roof or a high wall over it: more than 4 solid blocks above within 12, or a cliff 6 high beside it. */
	private static boolean shaded(WorldGenLevel level, BlockPos air) {
		if (level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, air.getX(), air.getZ()) > air.getY() + 1) {
			return true;
		}

		for (Direction side : SIDES) {
			BlockPos column = air.relative(side);

			if (level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ()) >= air.getY() + 6) {
				return true;
			}
		}

		return false;
	}

	/**
	 * A horizontal side of {@code pos} that faces open air, when {@code pos} is wall material
	 * ({@code only}, or any carvable rock when null) and within reach of the origin; else null.
	 */
	private static @Nullable Direction openSide(WorldGenLevel level, BlockPos pos, @Nullable BlockState only, RandomSource random) {
		BlockState state = level.getBlockState(pos);

		if (only != null ? !state.is(only.getBlock()) : !carvable(state)) {
			return null;
		}

		int start = random.nextInt(SIDES.size());

		for (int i = 0; i < SIDES.size(); i++) {
			Direction side = SIDES.get((start + i) % SIDES.size());
			BlockPos front = pos.relative(side);

			if (level.getBlockState(front).isAir() && level.getBlockState(front.above()).isAir()) {
				return side;
			}
		}

		return null;
	}

	private static boolean carvable(BlockState state) {
		return state.is(BlockTags.TERRACOTTA) || state.is(Blocks.RED_SANDSTONE) || state.is(Blocks.SANDSTONE) || state.is(Blocks.SMOOTH_SANDSTONE)
				|| state.is(Blocks.STONE) || state.is(Blocks.BLACKSTONE) || state.is(Blocks.TUFF) || state.is(Blocks.DEEPSLATE);
	}

	private static boolean solidGround(WorldGenLevel level, BlockPos ground) {
		BlockState state = level.getBlockState(ground);
		return !state.isAir() && state.getFluidState().isEmpty() && !state.is(BlockTags.LEAVES) && state.isSolid();
	}

	/** Writes a block without neighbour updates (every kind stays within 7 blocks of its origin). */
	private static void set(WorldGenLevel level, BlockPos pos, BlockState state) {
		level.setBlock(pos, state, Block.UPDATE_CLIENTS);
	}
}
