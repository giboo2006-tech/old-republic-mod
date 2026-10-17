package com.oldrepublic.world.puzzle;

import com.oldrepublic.OldRepublicMod;

import net.fabricmc.fabric.api.object.builder.v1.world.poi.PoiHelper;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.block.Block;

/**
 * Point-of-interest types of the puzzle blocks (spec 3.3): every lookup of a puzzle block (urns
 * finding their keeper, the Sense provider) goes through {@code PoiManager}, never a block scan.
 * POIs are indexed per chunk section for worldgen, runtime and test placements alike. Ids are the
 * block ids.
 */
public final class PuzzlePois {
	public static final ResourceKey<PoiType> VEILED_STONE = register("veiled_stone", PuzzleBlocks.VEILED_STONE);
	public static final ResourceKey<PoiType> ECHO_URN = register("echo_urn", PuzzleBlocks.ECHO_URN);
	public static final ResourceKey<PoiType> URN_KEEPER = register("urn_keeper", PuzzleBlocks.URN_KEEPER);
	public static final ResourceKey<PoiType> HOLOCRON_PEDESTAL = register("holocron_pedestal", PuzzleBlocks.HOLOCRON_PEDESTAL);
	public static final ResourceKey<PoiType> COUNTERWEIGHT_STONE = register("counterweight_stone", PuzzleBlocks.COUNTERWEIGHT_STONE);
	public static final ResourceKey<PoiType> PULL_ANCHOR = register("pull_anchor", PuzzleBlocks.PULL_ANCHOR);

	private static ResourceKey<PoiType> register(String name, Block block) {
		Identifier id = OldRepublicMod.id(name);
		PoiHelper.register(id, 0, 1, block);
		return ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, id);
	}

	/** Loads the class: registers the POI types. Call after {@link PuzzleBlocks#init}. */
	public static void init() {
	}

	private PuzzlePois() {
	}
}
