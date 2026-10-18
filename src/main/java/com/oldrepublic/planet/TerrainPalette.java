package com.oldrepublic.planet;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The ground a runtime site is fitted with (spec MILESTONE_4.md 3.6): the top, fill and base blocks
 * of the biome under a column. Overworld biomes keep grass, dirt and stone; the planets' biomes
 * register their own rows (W1).
 */
public final class TerrainPalette {
	/**
	 * @param top the surface block (grass on the home world)
	 * @param fill the two layers below the top (dirt)
	 * @param base everything deeper (stone)
	 */
	public record Layers(BlockState top, BlockState fill, BlockState base) {
		/** The M1 to M3 ground: grass, dirt, stone. */
		public static final Layers DEFAULT = new Layers(Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.DIRT.defaultBlockState(), Blocks.STONE.defaultBlockState());
	}

	private static final Map<ResourceKey<Biome>, Layers> ROWS = new ConcurrentHashMap<>();

	/** Adds or replaces a biome's row. Call during mod initialisation. */
	public static void register(ResourceKey<Biome> biome, Layers layers) {
		ROWS.put(biome, layers);
	}

	/** The row for the biome at {@code pos}; {@link Layers#DEFAULT} for every biome without one. */
	public static Layers at(ServerLevel level, BlockPos pos) {
		Holder<Biome> biome = level.getBiome(pos);
		return biome.unwrapKey().map(key -> ROWS.getOrDefault(key, Layers.DEFAULT)).orElse(Layers.DEFAULT);
	}

	private TerrainPalette() {
	}
}
