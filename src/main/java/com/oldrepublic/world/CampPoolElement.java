package com.oldrepublic.world;

import java.util.Optional;

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
 * <li><b>Headroom:</b> above the cleared yard (the template clears local y 5 to 12, inset
 * {@link #CLEAR_INSET} from the edges), terrain left above the template is removed up to
 * {@link #CLEAR_HEIGHT} blocks, so hillsides do not overhang the camp.</li>
 * </ul>
 *
 * <p>Runs during world generation, once per chunk the piece touches, and only writes inside that
 * chunk's writable box and inside the piece's own columns, so the result is deterministic and
 * independent of chunk order. The yard layout is symmetric, so rotation does not matter.
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
	 * Supports the foundation of a placed camp piece and clears the overhang above its yard, for
	 * the columns of {@code piece} inside {@code writable}.
	 */
	public static void adaptTerrain(WorldGenLevel level, BoundingBox piece, BoundingBox writable) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int minX = Math.max(piece.minX(), writable.minX());
		int maxX = Math.min(piece.maxX(), writable.maxX());
		int minZ = Math.max(piece.minZ(), writable.minZ());
		int maxZ = Math.min(piece.maxZ(), writable.maxZ());

		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				support(level, pos, x, z, piece.minY(), writable.minY());

				boolean yard = x >= piece.minX() + CLEAR_INSET && x <= piece.maxX() - CLEAR_INSET
						&& z >= piece.minZ() + CLEAR_INSET && z <= piece.maxZ() - CLEAR_INSET;

				if (yard) {
					clearAbove(level, pos, x, z, piece.minY() + CLEAR_FROM, Math.min(writable.maxY(), piece.minY() + CLEAR_FROM + CLEAR_HEIGHT - 1));
				}
			}
		}
	}

	/** Fills the column under a foundation block down to solid ground. Columns open at the bottom (the shaft) are left alone. */
	private static void support(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int bottom, int floorLimit) {
		BlockState foundation = level.getBlockState(pos.set(x, bottom, z));

		if (foundation.isAir() || !foundation.getFluidState().isEmpty()) {
			return;
		}

		int lowest = Math.max(floorLimit, bottom - SUPPORT_DEPTH);

		for (int y = bottom - 1; y >= lowest; y--) {
			if (!needsSupport(level.getBlockState(pos.set(x, y, z)))) {
				return;
			}

			level.setBlock(pos, supportBlock(x, y, z), Block.UPDATE_CLIENTS);
		}
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
