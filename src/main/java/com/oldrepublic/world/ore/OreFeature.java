package com.oldrepublic.world.ore;

import com.oldrepublic.OldRepublicMod;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * Milestone 7's ores (docs/MILESTONE_7.md 4): the deep starfall veins added to the overworld's biomes. The ore
 * blocks and items are {@code M7Items}'; the craters and the Tyrvann clan iron are data. A Phase 0 stub that OR
 * fills; called once from {@code M7Contracts.init()}.
 */
public final class OreFeature {
	/**
	 * The deep starfall veins: data/oldrepublic/worldgen/placed_feature/ore_starfall_deep.json (veins of 4, 2 a
	 * chunk, y -64 to -8; written by tools/assets/gen_textures_ores_m7.py). The craters are the main source; these
	 * are the slow way for a player who would rather dig than travel.
	 */
	// API-CHECK: Registries.PLACED_FEATURE, and PlacedFeature in net.minecraft.world.level.levelgen.placement (from addFeature's signature in the m7 probe); neither is used elsewhere in this repository
	public static final ResourceKey<PlacedFeature> STARFALL_DEEP = ResourceKey.create(Registries.PLACED_FEATURE, OldRepublicMod.id("ore_starfall_deep"));

	public static void init() {
		// Every overworld biome, with the vanilla ores. Biome modifiers are applied before any config is read, so
		// this is always on.
		// API-CHECK: BiomeSelectors.foundInOverworld() and BiomeModifications.addFeature(Predicate, GenerationStep.Decoration, ResourceKey<PlacedFeature>) (probe m7)
		BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.UNDERGROUND_ORES, STARFALL_DEEP);
	}

	private OreFeature() {
	}
}
