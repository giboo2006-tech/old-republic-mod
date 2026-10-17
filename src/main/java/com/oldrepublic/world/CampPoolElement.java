package com.oldrepublic.world;

import java.util.Optional;
import java.util.function.IntBinaryOperator;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.oldrepublic.OldRepublicMod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElementType;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * Pool element of the poacher camp ({@code oldrepublic:camp_pool_element}): places the template
 * exactly like a single pool element, then adapts the terrain to it so a camp on a slope neither
 * hangs over air nor sits under an overhang.
 *
 * <ul>
 * <li><b>Support:</b> under every foundation column, air, fluids, plants and leaves are filled
 * with stone down to the first solid block (at most {@link #SUPPORT_DEPTH} blocks).</li>
 * <li><b>Edge ramp:</b> where the ground just outside the camp is two or more blocks below the
 * yard, the outer two rings of the yard step down one block each, so the camp can be walked
 * into from the downhill side instead of being ringed by a ledge.</li>
 * <li><b>Headroom:</b> above the cleared yard (the template clears local y 5 to 12, inset
 * {@link #CLEAR_INSET} from the edges), terrain left above the template is removed up to
 * {@link #CLEAR_HEIGHT} blocks, so hillsides do not overhang the camp.</li>
 * </ul>
 *
 * <p>Runs during world generation, once per chunk the piece touches, and only writes inside that
 * chunk's writable box and inside the piece's own columns. The edge ramp reads the world-gen
 * height of the column just outside the camp (read only). The yard layout is symmetric, so
 * rotation does not matter.
 */
public class CampPoolElement extends SinglePoolElement {
	public static final MapCodec<CampPoolElement> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			templateCodec(), processorsCodec(), projectionCodec(), overrideLiquidSettingsCodec()).apply(i, CampPoolElement::new));
	/** Registered as {@code oldrepublic:camp_pool_element}; used by {@code template_pool/crystal_cave/camp.json}. */
	// API-CHECK: StructurePoolElementType is a functional interface with a single codec() method,
	// registered in BuiltInRegistries.STRUCTURE_POOL_ELEMENT (as in vanilla's own element types).
	public static final StructurePoolElementType<CampPoolElement> TYPE = () -> CODEC;

	/** Deepest support column under the foundation. */
	public static final int SUPPORT_DEPTH = 24;
	/** Distance from the template's edges to the cleared yard. */
	public static final int CLEAR_INSET = 2;
	/** First template-local height above the cleared yard volume (the template clears y 5 to 12). */
	public static final int CLEAR_FROM = 13;
	/** How far above {@link #CLEAR_FROM} leftover terrain is removed. */
	public static final int CLEAR_HEIGHT = 24;
	/** Template-local height a player stands at on the yard (the grass is at y 4). */
	public static final int YARD_STAND = 5;
	/** Width of the stepped ring at the yard's edge (nothing but ground is placed there). */
	public static final int RAMP_RINGS = 2;

	/**
	 * The numbers of one terrain adaptation, in template-local heights (W1's
	 * {@code adapted_pool_element} reads them from its pool JSON).
	 *
	 * @param supportDepth deepest support column under the foundation
	 * @param clearInset   distance from the piece's edges to the cleared volume
	 * @param clearFrom    first template-local height above which leftover terrain is removed
	 * @param clearHeight  how far above {@code clearFrom} it is removed
	 * @param yardStand    template-local height a player stands at on the yard's edge ring
	 * @param rampRings    width of the stepped ring at the yard's edge (0: no ramp)
	 */
	public record Adaptation(int supportDepth, int clearInset, int clearFrom, int clearHeight, int yardStand, int rampRings) {
		/** The poacher camp's numbers (the constants above). */
		public static final Adaptation CAMP = new Adaptation(SUPPORT_DEPTH, CLEAR_INSET, CLEAR_FROM, CLEAR_HEIGHT, YARD_STAND, RAMP_RINGS);
	}

	protected CampPoolElement(Either<Identifier, StructureTemplate> template, Holder<StructureProcessorList> processors,
			StructureTemplatePool.Projection projection, Optional<LiquidSettings> overrideLiquidSettings) {
		super(template, processors, projection, overrideLiquidSettings);
	}

	static void init() {
		Registry.register(BuiltInRegistries.STRUCTURE_POOL_ELEMENT, OldRepublicMod.id("camp_pool_element"), TYPE);
	}

	@Override
	public boolean place(StructureTemplateManager templates, WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
			BlockPos position, BlockPos reference, Rotation rotation, BoundingBox box, RandomSource random, LiquidSettings liquidSettings,
			boolean keepJigsaws) {
		if (!super.place(templates, level, structures, generator, position, reference, rotation, box, random, liquidSettings, keepJigsaws)) {
			return false;
		}

		adaptTerrain(level, getBoundingBox(templates, position, rotation), box);
		return true;
	}

	@Override
	public StructurePoolElementType<?> getType() {
		return TYPE;
	}

	/**
	 * Supports the foundation of a placed camp piece, steps its yard edge down to low ground and
	 * clears the overhang above its yard, for the columns of {@code piece} inside {@code writable}.
	 */
	public static void adaptTerrain(WorldGenLevel level, BoundingBox piece, BoundingBox writable) {
		// API-CHECK: WorldGenLevel (LevelReader) #getHeight(Heightmap.Types, int, int) for a column in a neighbouring chunk during structure placement.
		adaptTerrain(level, piece, writable, (x, z) -> level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z));
	}

	/**
	 * As {@link #adaptTerrain(WorldGenLevel, BoundingBox, BoundingBox)}, with {@code groundStand}
	 * giving the height a player would stand at on the natural ground of a column outside the camp.
	 */
	public static void adaptTerrain(WorldGenLevel level, BoundingBox piece, BoundingBox writable, IntBinaryOperator groundStand) {
		adaptTerrain(level, piece, writable, groundStand, Adaptation.CAMP);
	}

	/**
	 * As {@link #adaptTerrain(WorldGenLevel, BoundingBox, BoundingBox, IntBinaryOperator)}, with the
	 * numbers of {@code adaptation} instead of the camp's.
	 */
	public static void adaptTerrain(WorldGenLevel level, BoundingBox piece, BoundingBox writable, IntBinaryOperator groundStand, Adaptation adaptation) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int minX = Math.max(piece.minX(), writable.minX());
		int maxX = Math.min(piece.maxX(), writable.maxX());
		int minZ = Math.max(piece.minZ(), writable.minZ());
		int maxZ = Math.min(piece.maxZ(), writable.maxZ());

		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				support(level, pos, x, z, piece.minY(), writable.minY(), adaptation.supportDepth());
				ramp(level, pos, piece, x, z, groundStand, adaptation.yardStand(), adaptation.rampRings());

				boolean yard = x >= piece.minX() + adaptation.clearInset() && x <= piece.maxX() - adaptation.clearInset()
						&& z >= piece.minZ() + adaptation.clearInset() && z <= piece.maxZ() - adaptation.clearInset();

				if (yard) {
					int from = piece.minY() + adaptation.clearFrom();
					clearAbove(level, pos, x, z, from, Math.min(writable.maxY(), from + adaptation.clearHeight() - 1));
				}
			}
		}
	}

	/** Fills the column under a foundation block down to solid ground. Columns open at the bottom (the shaft) are left alone. */
	private static void support(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int bottom, int floorLimit, int supportDepth) {
		BlockState foundation = level.getBlockState(pos.set(x, bottom, z));

		if (foundation.isAir() || !foundation.getFluidState().isEmpty()) {
			return;
		}

		int lowest = Math.max(floorLimit, bottom - supportDepth);

		for (int y = bottom - 1; y >= lowest; y--) {
			if (!needsSupport(level.getBlockState(pos.set(x, y, z)))) {
				return;
			}

			level.setBlock(pos, supportBlock(x, y, z), Block.UPDATE_CLIENTS);
		}
	}

	/**
	 * Steps an outer-ring yard column down toward low ground outside: the outermost ring ends two
	 * blocks below the yard, the next one one block below, never lower than the ground outside.
	 */
	private static void ramp(WorldGenLevel level, BlockPos.MutableBlockPos pos, BoundingBox piece, int x, int z, IntBinaryOperator groundStand, int localYardStand,
			int rampRings) {
		int west = x - piece.minX();
		int east = piece.maxX() - x;
		int north = z - piece.minZ();
		int south = piece.maxZ() - z;
		int ring = Math.min(Math.min(west, east), Math.min(north, south));

		if (ring >= rampRings) {
			return;
		}

		int yardStand = piece.minY() + localYardStand;

		if (level.getBlockState(pos.set(x, yardStand - 1, z)).isAir() || !level.getBlockState(pos.set(x, yardStand, z)).isAir()) {
			return; // not plain yard ground (the shaft, or something stands on it)
		}

		int ground = Integer.MAX_VALUE;

		if (west == ring) {
			ground = Math.min(ground, groundStand.applyAsInt(piece.minX() - 1, z));
		}

		if (east == ring) {
			ground = Math.min(ground, groundStand.applyAsInt(piece.maxX() + 1, z));
		}

		if (north == ring) {
			ground = Math.min(ground, groundStand.applyAsInt(x, piece.minZ() - 1));
		}

		if (south == ring) {
			ground = Math.min(ground, groundStand.applyAsInt(x, piece.maxZ() + 1));
		}

		if (yardStand - ground < 2) {
			return;
		}

		int stand = Math.max(ground, yardStand - (rampRings - ring));

		for (int y = stand; y < yardStand; y++) {
			level.setBlock(pos.set(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		}

		level.setBlock(pos.set(x, stand - 1, z), Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
	}

	private static boolean needsSupport(BlockState state) {
		return state.isAir() || state.canBeReplaced() || !state.getFluidState().isEmpty() || state.is(BlockTags.LEAVES);
	}

	/** Mostly stone with a little andesite, chosen from the position so every run builds the same column. */
	private static BlockState supportBlock(int x, int y, int z) {
		long h = x * 3129871L ^ z * 116129781L ^ y * 9301L;
		h = h * h * 42317861L + h * 11L;
		return ((h >>> 16) & 15) < 3 ? Blocks.ANDESITE.defaultBlockState() : Blocks.STONE.defaultBlockState();
	}

	/** Removes whatever terrain is left above the cleared yard volume in one column. */
	private static void clearAbove(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int fromY, int toY) {
		for (int y = fromY; y <= toY; y++) {
			if (!level.getBlockState(pos.set(x, y, z)).isAir()) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}
	}
}
