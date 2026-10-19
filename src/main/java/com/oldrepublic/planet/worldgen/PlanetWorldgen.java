package com.oldrepublic.planet.worldgen;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.planet.TerrainPalette;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;

/**
 * The planets' world generation hooks (spec MILESTONE_4.md 3.1 to 3.3, 3.6): the six biome keys and
 * tags, the decoration feature types ({@link PlanetDecorFeature}) and the biomes' terrain palette
 * rows. Dimensions, noise, biomes and placed features are data ({@code gen_structures_m4.py}).
 *
 * <p>Milestone 5 (MILESTONE_5.md 3.1) reads the biomes and the biome tag per planet ({@link #biomes},
 * {@link #biomeTag}). Ghessar's and Tyrvann's tags are new and empty until W3 adds their six biomes
 * (and their rows here); their placeholder dimensions grow one vanilla biome each meanwhile.
 */
public final class PlanetWorldgen {
	public static final ResourceKey<Biome> SELVAREN_HIGHWOOD = biome("selvaren_highwood");
	public static final ResourceKey<Biome> SELVAREN_MEADOW = biome("selvaren_meadow");
	public static final ResourceKey<Biome> SELVAREN_TARN = biome("selvaren_tarn");
	public static final ResourceKey<Biome> VARKHAST_REDROCK = biome("varkhast_redrock");
	public static final ResourceKey<Biome> VARKHAST_ASHFALL = biome("varkhast_ashfall");
	public static final ResourceKey<Biome> VARKHAST_TOMB_VALLEY = biome("varkhast_tomb_valley");
	/** Every planet biome, Selvaren's first. */
	public static final List<ResourceKey<Biome>> BIOMES = List.of(SELVAREN_HIGHWOOD, SELVAREN_MEADOW, SELVAREN_TARN, VARKHAST_REDROCK,
			VARKHAST_ASHFALL, VARKHAST_TOMB_VALLEY);

	public static final TagKey<Biome> IS_SELVAREN = TagKey.create(Registries.BIOME, OldRepublicMod.id("is_selvaren"));
	public static final TagKey<Biome> IS_VARKHAST = TagKey.create(Registries.BIOME, OldRepublicMod.id("is_varkhast"));
	public static final TagKey<Biome> IS_PLANET = TagKey.create(Registries.BIOME, OldRepublicMod.id("is_planet"));
	/** Ghessar's biomes (MILESTONE_5.md 3.2; empty until W3 adds them). */
	public static final TagKey<Biome> IS_GHESSAR = TagKey.create(Registries.BIOME, OldRepublicMod.id("is_ghessar"));
	/** Tyrvann's biomes (MILESTONE_5.md 3.3; empty until W3 adds them). */
	public static final TagKey<Biome> IS_TYRVANN = TagKey.create(Registries.BIOME, OldRepublicMod.id("is_tyrvann"));

	/** The biome tag of a planet's own biomes; null for the home world and any world without a row. */
	public static @Nullable TagKey<Biome> biomeTag(Planets.Planet planet) {
		return switch (planet) {
			case SELVAREN -> IS_SELVAREN;
			case VARKHAST -> IS_VARKHAST;
			case GHESSAR -> IS_GHESSAR;
			case TYRVANN -> IS_TYRVANN;
			default -> null;
		};
	}

	/** A planet's own biomes, in {@link #BIOMES} order; empty for the home world and a world without a row (Ghessar and Tyrvann until W3). */
	public static List<ResourceKey<Biome>> biomes(Planets.Planet planet) {
		return switch (planet) {
			case SELVAREN -> List.of(SELVAREN_HIGHWOOD, SELVAREN_MEADOW, SELVAREN_TARN);
			case VARKHAST -> List.of(VARKHAST_REDROCK, VARKHAST_ASHFALL, VARKHAST_TOMB_VALLEY);
			default -> List.of();
		};
	}

	/** Registers the decoration feature types and the palette rows. */
	public static void init() {
		for (PlanetDecorFeature.Kind kind : PlanetDecorFeature.Kind.values()) {
			Registry.register(BuiltInRegistries.FEATURE, OldRepublicMod.id(kind.id()), new PlanetDecorFeature(kind));
		}

		TerrainPalette.Layers selvaren = new TerrainPalette.Layers(Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.DIRT.defaultBlockState(),
				Blocks.STONE.defaultBlockState());
		TerrainPalette.register(SELVAREN_HIGHWOOD, selvaren);
		TerrainPalette.register(SELVAREN_MEADOW, selvaren);
		TerrainPalette.register(SELVAREN_TARN, selvaren);
		TerrainPalette.register(VARKHAST_REDROCK, new TerrainPalette.Layers(Blocks.RED_SAND.defaultBlockState(), Blocks.RED_SANDSTONE.defaultBlockState(),
				Blocks.STONE.defaultBlockState()));
		TerrainPalette.register(VARKHAST_ASHFALL, new TerrainPalette.Layers(Blocks.COARSE_DIRT.defaultBlockState(), Blocks.TUFF.defaultBlockState(),
				Blocks.STONE.defaultBlockState()));
		TerrainPalette.register(VARKHAST_TOMB_VALLEY, new TerrainPalette.Layers(Blocks.RED_SAND.defaultBlockState(), Blocks.SMOOTH_SANDSTONE.defaultBlockState(),
				Blocks.STONE.defaultBlockState()));
	}

	private static ResourceKey<Biome> biome(String path) {
		return ResourceKey.create(Registries.BIOME, OldRepublicMod.id(path));
	}

	private PlanetWorldgen() {
	}
}
