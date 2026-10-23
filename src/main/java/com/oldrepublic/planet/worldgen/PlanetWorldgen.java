package com.oldrepublic.planet.worldgen;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.planet.PlanetBlocks;
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
 * The planets' world generation hooks (spec MILESTONE_4.md 3.1 to 3.3, 3.6; MILESTONE_5.md 3.1 to
 * 3.3): the twelve biome keys and their tags, the decoration feature types
 * ({@link PlanetDecorFeature}) and the biomes' terrain palette rows. Dimensions, noise, biomes and
 * placed features are data ({@code gen_structures_m4.py} for Selvaren and Varkhast,
 * {@code gen_structures_m5.py} for Ghessar and Tyrvann). Code reads a planet's biomes and biome tag
 * through {@link #biomes} and {@link #biomeTag}.
 */
public final class PlanetWorldgen {
	public static final ResourceKey<Biome> SELVAREN_HIGHWOOD = biome("selvaren_highwood");
	public static final ResourceKey<Biome> SELVAREN_MEADOW = biome("selvaren_meadow");
	public static final ResourceKey<Biome> SELVAREN_TARN = biome("selvaren_tarn");
	public static final ResourceKey<Biome> VARKHAST_REDROCK = biome("varkhast_redrock");
	public static final ResourceKey<Biome> VARKHAST_ASHFALL = biome("varkhast_ashfall");
	public static final ResourceKey<Biome> VARKHAST_TOMB_VALLEY = biome("varkhast_tomb_valley");
	/** Ghessar's dune sea: sand over sandstone, dead bushes, bleached ribs (MILESTONE_5.md 3.2). */
	public static final ResourceKey<Biome> GHESSAR_DUNES = biome("ghessar_dunes");
	/** Ghessar's salt pans: salt crust over sandstone, white haze, salt pillars. */
	public static final ResourceKey<Biome> GHESSAR_SALTFLAT = biome("ghessar_saltflat");
	/** Ghessar's canyon country: terracotta walls, red sand floors, sunstone in the walls. */
	public static final ResourceKey<Biome> GHESSAR_CANYONS = biome("ghessar_canyons");
	/** Tyrvann's storm jungle of tall dark trees and hanging moss (3.3). */
	public static final ResourceKey<Biome> TYRVANN_STORMWOOD = biome("tyrvann_stormwood");
	/** Tyrvann's mire: mud, roots, black pools and peat. */
	public static final ResourceKey<Biome> TYRVANN_MIRE = biome("tyrvann_mire");
	/** Tyrvann's basalt ridges, where the stormglass grows and ridge lightning strikes. */
	public static final ResourceKey<Biome> TYRVANN_BASALT = biome("tyrvann_basalt");
	/** Every planet biome, Selvaren's first, then Varkhast's, Ghessar's and Tyrvann's. */
	public static final List<ResourceKey<Biome>> BIOMES = List.of(SELVAREN_HIGHWOOD, SELVAREN_MEADOW, SELVAREN_TARN, VARKHAST_REDROCK,
			VARKHAST_ASHFALL, VARKHAST_TOMB_VALLEY, GHESSAR_DUNES, GHESSAR_SALTFLAT, GHESSAR_CANYONS, TYRVANN_STORMWOOD, TYRVANN_MIRE, TYRVANN_BASALT);

	public static final TagKey<Biome> IS_SELVAREN = TagKey.create(Registries.BIOME, OldRepublicMod.id("is_selvaren"));
	public static final TagKey<Biome> IS_VARKHAST = TagKey.create(Registries.BIOME, OldRepublicMod.id("is_varkhast"));
	public static final TagKey<Biome> IS_PLANET = TagKey.create(Registries.BIOME, OldRepublicMod.id("is_planet"));
	/** Ghessar's biomes (MILESTONE_5.md 3.2). */
	public static final TagKey<Biome> IS_GHESSAR = TagKey.create(Registries.BIOME, OldRepublicMod.id("is_ghessar"));
	/** Tyrvann's biomes (MILESTONE_5.md 3.3). */
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

	/** A planet's own biomes, in {@link #BIOMES} order; empty for the home world and a world without a row. */
	public static List<ResourceKey<Biome>> biomes(Planets.Planet planet) {
		return switch (planet) {
			case SELVAREN -> List.of(SELVAREN_HIGHWOOD, SELVAREN_MEADOW, SELVAREN_TARN);
			case VARKHAST -> List.of(VARKHAST_REDROCK, VARKHAST_ASHFALL, VARKHAST_TOMB_VALLEY);
			case GHESSAR -> List.of(GHESSAR_DUNES, GHESSAR_SALTFLAT, GHESSAR_CANYONS);
			case TYRVANN -> List.of(TYRVANN_STORMWOOD, TYRVANN_MIRE, TYRVANN_BASALT);
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
		TerrainPalette.register(GHESSAR_DUNES, new TerrainPalette.Layers(Blocks.SAND.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState(),
				Blocks.SANDSTONE.defaultBlockState()));
		TerrainPalette.register(GHESSAR_SALTFLAT, new TerrainPalette.Layers(PlanetBlocks.SALT_CRUST.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState(),
				Blocks.SANDSTONE.defaultBlockState()));
		TerrainPalette.register(GHESSAR_CANYONS, new TerrainPalette.Layers(Blocks.RED_SAND.defaultBlockState(), Blocks.TERRACOTTA.defaultBlockState(),
				Blocks.RED_SANDSTONE.defaultBlockState()));
		TerrainPalette.register(TYRVANN_STORMWOOD, new TerrainPalette.Layers(Blocks.PODZOL.defaultBlockState(), Blocks.DIRT.defaultBlockState(),
				Blocks.STONE.defaultBlockState()));
		TerrainPalette.register(TYRVANN_MIRE, new TerrainPalette.Layers(Blocks.MUD.defaultBlockState(), PlanetBlocks.PEAT.defaultBlockState(),
				Blocks.CLAY.defaultBlockState()));
		TerrainPalette.register(TYRVANN_BASALT, new TerrainPalette.Layers(Blocks.SMOOTH_BASALT.defaultBlockState(), Blocks.BASALT.defaultBlockState(),
				Blocks.BLACKSTONE.defaultBlockState()));
	}

	private static ResourceKey<Biome> biome(String path) {
		return ResourceKey.create(Registries.BIOME, OldRepublicMod.id(path));
	}

	private PlanetWorldgen() {
	}
}
