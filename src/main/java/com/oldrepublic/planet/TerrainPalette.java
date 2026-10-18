package com.oldrepublic.planet;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The ground a runtime site is fitted with (spec MILESTONE_4.md 3.6): the top, fill and base blocks
 * of the biome under a column. Overworld biomes keep grass, dirt and stone; the planets' six biomes
 * register their own rows ({@code planet.worldgen.PlanetWorldgen}).
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
		return at((LevelReader) level, pos);
	}

	/** As {@link #at(ServerLevel, BlockPos)} for any reader, such as a world-gen region during structure placement. */
	public static Layers at(LevelReader level, BlockPos pos) {
		Holder<Biome> biome = level.getBiome(pos);
		return biome.unwrapKey().map(key -> ROWS.getOrDefault(key, Layers.DEFAULT)).orElse(Layers.DEFAULT);
	}

	/** The row registered for {@code biome}, or null when it has none (and uses {@link Layers#DEFAULT}). */
	public static @Nullable Layers row(ResourceKey<Biome> biome) {
		return ROWS.get(biome);
	}

	private TerrainPalette() {
	}
}
