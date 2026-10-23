package com.oldrepublic.planet.worldgen;

import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.planet.PlanetBlocks;
import com.oldrepublic.world.block.CrystalFormationBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.material.Fluids;

/**
 * The planets' hand-written decorations (spec MILESTONE_4.md 3.2, 3.3; MILESTONE_5.md 3.2, 3.3),
 * one feature type per {@link Kind}, each registered as {@code oldrepublic:<kind>} and used by the
 * configured feature of the same name. The vanilla feature types cover trees, plants and ores; these
 * cover what they cannot: shade-loving plants, stones, spires, carved walls, crystal walls, the dune
 * sea's bones, the salt pillars, Tyrvann's giant trees, moss, roots, pools and stormglass.
 *
 * <p>Every kind writes within 7 blocks of its origin horizontally (inside the decoration region),
 * reads the world-gen heightmaps, and draws all randomness from the context's random, so a seed
 * decorates the same way every time. No kind lights anything, and none draws a pattern on the ground.
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
		DUSKGLASS_WALL,
		/** Ghessar dunes: the bleached ribcage of a giant beast, half buried in the sand. */
		BLEACHED_RIBS,
		/** Ghessar salt pans: a crusted pillar of salt on a wider foot. */
		SALT_PILLAR,
		/** Tyrvann stormwood: a giant dark tree, a 2 by 2 trunk on buttress roots under a jungle crown. */
		STORMWOOD_GIANT,
		/** Tyrvann: strands of moss hanging from the canopy. */
		HANGING_MOSS,
		/** Tyrvann mire: mangrove roots arching out of the mud and water. */
		MIRE_ROOTS,
		/** Tyrvann mire: a black pool sunk in the mud. */
		MIRE_POOL,
		/** Tyrvann basalt: stormglass clusters on exposed ridge tops. */
		STORMGLASS_TOPS;

		/** The registry path ({@code highwood_floor}, ...). */
		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	private static final List<Direction> SIDES = List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);
	/** Stormglass grows only on ridge tops at least this high (Tyrvann's lowlands sit near y 63). */
	private static final int STORMGLASS_MIN_Y = 96;

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
			case BLEACHED_RIBS -> bleachedRibs(level, origin, random);
			case SALT_PILLAR -> saltPillar(level, origin, random);
			case STORMWOOD_GIANT -> stormwoodGiant(level, origin, random);
			case HANGING_MOSS -> hangingMoss(level, origin, random);
			case MIRE_ROOTS -> mireRoots(level, origin, random);
			case MIRE_POOL -> mirePool(level, origin, random);
			case STORMGLASS_TOPS -> stormglassTops(level, origin, random);
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

	private static final List<BlockState> BANDS = List.of(Blocks.TERRACOTTA.defaultBlockState(), vanilla("orange_terracotta"),
			Blocks.RED_SANDSTONE.defaultBlockState(), vanilla("red_terracotta"), vanilla("brown_terracotta"),
			Blocks.RED_SANDSTONE.defaultBlockState());

	/** A vanilla block by id: 26.2 keeps the dyed terracottas in {@code Blocks.DYED_TERRACOTTA}, not as fields. */
	private static BlockState vanilla(String path) {
		return BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(path)).defaultBlockState();
	}

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

	// ------------------------------------------------------------------------------------------- Ghessar

	private static boolean bleachedRibs(WorldGenLevel level, BlockPos origin, RandomSource random) {
		BlockPos stand = surface(level, origin);

		if (!level.getBlockState(stand.below()).is(BlockTags.SAND) || !level.getFluidState(stand).isEmpty()) {
			return false;
		}

		// The spine lies along one axis a block deep in the sand; a pair of ribs every other block
		// curls up and in over it, the far end of the cage sunk deeper than the near one.
		Direction along = random.nextBoolean() ? Direction.EAST : Direction.SOUTH;
		Direction across = along.getClockWise();
		int length = 5 + random.nextInt(5);
		int start = -length / 2;
		BlockState spine = Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, along.getAxis());
		BlockState upright = Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
		BlockState inward = Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, across.getAxis());

		for (int i = 0; i < length; i++) {
			int sink = i * 2 >= length ? 1 + random.nextInt(2) : 1;
			BlockPos vertebra = stand.relative(along, start + i).below(sink);
			set(level, vertebra, spine);

			if (i % 2 != 0 || i == length - 1) {
				continue;
			}

			int height = 2 + random.nextInt(3) - (sink - 1);

			for (int side = -1; side <= 1; side += 2) {
				if (random.nextInt(5) == 0) {
					// A rib long since broken off and carried away.
					continue;
				}

				BlockPos rib = vertebra.relative(across, side * 2);

				for (int y = 0; y < height; y++) {
					set(level, rib.above(y), upright);
				}

				BlockPos tip = rib.above(height).relative(across, -side);

				if (level.getBlockState(tip).isAir()) {
					set(level, tip, inward);
				}
			}
		}

		return true;
	}

	private static boolean saltPillar(WorldGenLevel level, BlockPos origin, RandomSource random) {
		BlockPos stand = surface(level, origin);
		BlockState ground = level.getBlockState(stand.below());

		if (!ground.is(PlanetBlocks.SALT_CRUST) && !ground.is(Blocks.CALCITE) && !ground.is(Blocks.SANDSTONE) || !level.getFluidState(stand).isEmpty()) {
			return false;
		}

		int height = 3 + random.nextInt(5);

		for (int y = -2; y < height; y++) {
			set(level, stand.above(y), random.nextInt(5) == 0 ? PlanetBlocks.SALT_CRUST.defaultBlockState() : Blocks.CALCITE.defaultBlockState());
		}

		// A crusted foot that thins as it rises, and a lump or two of salt on the shoulders.
		for (Direction side : SIDES) {
			int foot = 1 + random.nextInt(Math.max(1, height / 2));

			for (int y = -1; y < foot; y++) {
				set(level, stand.relative(side).above(y), y == foot - 1 ? PlanetBlocks.SALT_CRUST.defaultBlockState() : Blocks.CALCITE.defaultBlockState());
			}

			if (random.nextInt(3) == 0) {
				BlockPos lump = stand.relative(side).relative(side.getClockWise()).below();

				if (level.getBlockState(lump.above()).isAir()) {
					set(level, lump, PlanetBlocks.SALT_CRUST.defaultBlockState());
				}
			}
		}

		return true;
	}

	// ------------------------------------------------------------------------------------------- Tyrvann

	private static boolean stormwoodGiant(WorldGenLevel level, BlockPos origin, RandomSource random) {
		BlockPos stand = surface(level, origin);

		for (int dx = 0; dx <= 1; dx++) {
			for (int dz = 0; dz <= 1; dz++) {
				BlockPos foot = stand.offset(dx, 0, dz);

				if (!level.getBlockState(foot.below()).is(BlockTags.DIRT) || !level.getFluidState(foot).isEmpty()) {
					return false;
				}
			}
		}

		int height = 14 + random.nextInt(9);
		BlockState log = Blocks.DARK_OAK_LOG.defaultBlockState();
		BlockState wood = Blocks.DARK_OAK_WOOD.defaultBlockState();

		for (int y = -1; y < height; y++) {
			for (int dx = 0; dx <= 1; dx++) {
				for (int dz = 0; dz <= 1; dz++) {
					set(level, stand.offset(dx, y, dz), log);
				}
			}
		}

		// Buttress roots: a flared wedge on each side, down into the ground.
		for (Direction side : SIDES) {
			BlockPos root = stand.offset(side.getStepX() == 1 ? 2 : side.getStepX() == -1 ? -1 : random.nextInt(2), 0,
					side.getStepZ() == 1 ? 2 : side.getStepZ() == -1 ? -1 : random.nextInt(2));
			int rise = 1 + random.nextInt(3);

			for (int y = -1; y < rise; y++) {
				set(level, root.above(y), wood);
			}

			if (random.nextBoolean()) {
				set(level, root.relative(side).below(), wood);
				set(level, root.relative(side), random.nextBoolean() ? wood : Blocks.MOSS_BLOCK.defaultBlockState());
			}
		}

		// Two or three crooked side branches high on the trunk, each ending in its own knot of leaves.
		for (int i = 0; i < 2 + random.nextInt(2); i++) {
			Direction side = SIDES.get(random.nextInt(SIDES.size()));
			BlockPos branch = stand.offset(side.getStepX() == 1 ? 2 : side.getStepX() == -1 ? -1 : 0, height - 6 - random.nextInt(4),
					side.getStepZ() == 1 ? 2 : side.getStepZ() == -1 ? -1 : 0);
			BlockState limb = log.setValue(RotatedPillarBlock.AXIS, side.getAxis());

			for (int j = 0; j < 1 + random.nextInt(2); j++) {
				set(level, branch, limb);
				branch = branch.relative(side).above(random.nextInt(2));
			}

			crown(level, branch, 2, random);
		}

		crown(level, stand.offset(0, height, 0), 4 + random.nextInt(2), random);
		vines(level, stand, height, random);
		return true;
	}

	/** A ragged dome of persistent jungle leaves around {@code centre} (radius at most 5). */
	private static void crown(WorldGenLevel level, BlockPos centre, int radius, RandomSource random) {
		BlockState leaves = Blocks.JUNGLE_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true);

		for (int dy = -2; dy <= 2; dy++) {
			int r = dy > 0 ? radius - dy : radius;

			for (int dx = -r; dx <= r + 1; dx++) {
				for (int dz = -r; dz <= r + 1; dz++) {
					double d = Math.hypot(dx - 0.5, dz - 0.5);

					if (d > r + 0.5 || d > r - 0.5 && random.nextInt(3) == 0) {
						continue;
					}

					BlockPos at = centre.offset(dx, dy, dz);

					if (level.getBlockState(at).canBeReplaced()) {
						set(level, at, leaves);
					}
				}
			}
		}
	}

	/** Vines on the trunk's faces, from the crown down part of the way. */
	private static void vines(WorldGenLevel level, BlockPos stand, int height, RandomSource random) {
		for (Direction side : SIDES) {
			for (int along = 0; along <= 1; along++) {
				if (random.nextInt(3) == 0) {
					continue;
				}

				BlockPos face = stand.offset(side.getStepX() == 1 ? 2 : side.getStepX() == -1 ? -1 : along, 0,
						side.getStepZ() == 1 ? 2 : side.getStepZ() == -1 ? -1 : along);
				BlockState vine = Blocks.VINE.defaultBlockState().setValue(vineFace(side.getOpposite()), true);
				int bottom = height / 3 + random.nextInt(4);

				for (int y = height - 1; y >= bottom; y--) {
					if (level.getBlockState(face.above(y)).isAir()) {
						set(level, face.above(y), vine);
					}
				}
			}
		}
	}

	/** The vine property for a vine attached to the block on its {@code side}. */
	private static BooleanProperty vineFace(Direction side) {
		return switch (side) {
			case NORTH -> BlockStateProperties.NORTH;
			case SOUTH -> BlockStateProperties.SOUTH;
			case EAST -> BlockStateProperties.EAST;
			case WEST -> BlockStateProperties.WEST;
			default -> BlockStateProperties.UP;
		};
	}

	private static boolean hangingMoss(WorldGenLevel level, BlockPos origin, RandomSource random) {
		BlockState moss = Blocks.PALE_HANGING_MOSS.defaultBlockState();
		boolean placed = false;

		for (int i = 0; i < 10; i++) {
			int x = origin.getX() + random.nextInt(13) - 6;
			int z = origin.getZ() + random.nextInt(13) - 6;
			int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
			int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);

			// The lowest leaf of the column's canopy with open air under it, at least 4 above the ground.
			for (int y = ground + 4; y < top; y++) {
				BlockPos leaf = new BlockPos(x, y, z);

				if (!level.getBlockState(leaf).is(BlockTags.LEAVES) || !level.getBlockState(leaf.below()).isAir()) {
					continue;
				}

				int length = 1 + random.nextInt(Math.min(5, y - ground - 2));

				for (int d = 1; d <= length; d++) {
					BlockPos at = leaf.below(d);

					if (!level.getBlockState(at).isAir()) {
						break;
					}

					boolean tip = d == length || !level.getBlockState(at.below()).isAir();
					set(level, at, moss.hasProperty(BlockStateProperties.TIP) ? moss.setValue(BlockStateProperties.TIP, tip) : moss);

					if (tip) {
						break;
					}
				}

				placed = true;
				break;
			}
		}

		return placed;
	}

	private static boolean mireRoots(WorldGenLevel level, BlockPos origin, RandomSource random) {
		BlockPos centre = surface(level, origin);
		BlockState ground = level.getBlockState(centre.below());

		if (!ground.is(Blocks.MUD) && !ground.is(PlanetBlocks.PEAT) && !level.getFluidState(centre).is(Fluids.WATER)) {
			return false;
		}

		set(level, centre.below(), Blocks.MUDDY_MANGROVE_ROOTS.defaultBlockState());

		for (int i = 0; i < 3 + random.nextInt(4); i++) {
			Direction side = SIDES.get(random.nextInt(SIDES.size()));
			Direction drift = random.nextBoolean() ? side.getClockWise() : side.getCounterClockWise();
			int reach = 2 + random.nextInt(3);
			int rise = 1 + random.nextInt(2);
			BlockPos at = centre;

			// An arch: up out of the mud, over, and back down into it.
			for (int step = 0; step <= reach; step++) {
				int y = step == 0 || step == reach ? 0 : rise;
				BlockPos cell = at.above(y);
				root(level, cell);

				if (step == 0 || step == reach) {
					root(level, cell.below());
				} else if (y > 0) {
					root(level, cell.below());
				}

				at = at.relative(side);

				if (random.nextInt(3) == 0) {
					at = at.relative(drift);
				}
			}
		}

		return true;
	}

	/** One mangrove root cell, waterlogged where it replaces water; ground other than mud, peat and clay is left alone. */
	private static void root(WorldGenLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		boolean water = level.getFluidState(pos).is(Fluids.WATER);

		if (state.isAir() || water || state.canBeReplaced()) {
			set(level, pos, Blocks.MANGROVE_ROOTS.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, water));
		} else if (state.is(Blocks.MUD) || state.is(PlanetBlocks.PEAT) || state.is(Blocks.CLAY)) {
			set(level, pos, Blocks.MUDDY_MANGROVE_ROOTS.defaultBlockState());
		}
	}

	private static boolean mirePool(WorldGenLevel level, BlockPos origin, RandomSource random) {
		BlockPos centre = surface(level, origin);
		int y = centre.getY() - 1;
		int radius = 2 + random.nextInt(3);

		// Only on mud-and-peat flats: every rim cell is solid ground at the pool's level, so the water stays put.
		for (int dx = -radius - 1; dx <= radius + 1; dx++) {
			for (int dz = -radius - 1; dz <= radius + 1; dz++) {
				BlockPos rim = new BlockPos(centre.getX() + dx, y, centre.getZ() + dz);
				BlockState state = level.getBlockState(rim);

				if (!state.is(Blocks.MUD) && !state.is(PlanetBlocks.PEAT) && !state.is(BlockTags.DIRT) && !state.is(Blocks.CLAY)) {
					return false;
				}
			}
		}

		BlockState water = Blocks.WATER.defaultBlockState();

		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				double d = Math.hypot(dx, dz) + (random.nextDouble() - 0.5) * 0.9;

				if (d > radius) {
					continue;
				}

				int depth = d < radius * 0.5 ? 2 : 1;
				BlockPos column = new BlockPos(centre.getX() + dx, y, centre.getZ() + dz);

				if (!level.getBlockState(column.above()).canBeReplaced()) {
					// A trunk or root stands here: the pool goes round it.
					continue;
				}

				for (int k = 0; k < depth; k++) {
					set(level, column.below(k), water);
				}

				set(level, column.above(), Blocks.AIR.defaultBlockState());
				set(level, column.below(depth), random.nextInt(3) == 0 ? Blocks.CLAY.defaultBlockState() : Blocks.MUD.defaultBlockState());
			}
		}

		return true;
	}

	private static boolean stormglassTops(WorldGenLevel level, BlockPos origin, RandomSource random) {
		boolean placed = false;

		for (int i = 0; i < 6; i++) {
			BlockPos stand = surface(level, origin.offset(random.nextInt(9) - 4, 0, random.nextInt(9) - 4));
			BlockState ground = level.getBlockState(stand.below());

			if (stand.getY() < STORMGLASS_MIN_Y || !level.getBlockState(stand).isAir() || !level.canSeeSky(stand)
					|| !ground.is(Blocks.BASALT) && !ground.is(Blocks.SMOOTH_BASALT) && !ground.is(Blocks.BLACKSTONE)) {
				continue;
			}

			set(level, stand, PlanetBlocks.STORMGLASS_CLUSTER.defaultBlockState().setValue(CrystalFormationBlock.FACING, Direction.UP));
			placed = true;
		}

		return placed;
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
