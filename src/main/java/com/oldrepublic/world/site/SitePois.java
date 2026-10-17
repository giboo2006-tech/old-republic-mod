package com.oldrepublic.world.site;

import java.util.Comparator;
import java.util.List;

import com.oldrepublic.OldRepublicMod;

import net.fabricmc.fabric.api.object.builder.v1.world.poi.PoiHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;

/**
 * POI types of the site blocks (spec 3.3): echo cairns and bounty terminals are found through the
 * POI index, which covers worldgen, runtime and game-test placements alike, never by block scans.
 */
public final class SitePois {
	public static final ResourceKey<PoiType> ECHO_CAIRN = key("echo_cairn");
	public static final ResourceKey<PoiType> BOUNTY_TERMINAL = key("bounty_terminal");

	/** Registers both POI types (one ticket each, no search distance: they are lookup indexes, not villager jobs). */
	public static void init() {
		PoiHelper.register(ECHO_CAIRN.identifier(), 0, 1, SiteBlocks.ECHO_CAIRN);
		PoiHelper.register(BOUNTY_TERMINAL.identifier(), 0, 1, SiteBlocks.BOUNTY_TERMINAL);
	}

	/**
	 * Every block of POI {@code type} within {@code radius} blocks of {@code centre} in the POI index,
	 * nearest first. Reads the POI index only; it never loads or generates a chunk.
	 */
	public static List<BlockPos> find(ServerLevel level, ResourceKey<PoiType> type, BlockPos centre, int radius) {
		return level.getPoiManager()
				.findAll(holder -> holder.is(type), pos -> true, centre, radius, PoiManager.Occupancy.ANY)
				.map(BlockPos::immutable)
				.sorted(Comparator.comparingDouble(pos -> pos.distSqr(centre)))
				.toList();
	}

	private static ResourceKey<PoiType> key(String path) {
		return ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, OldRepublicMod.id(path));
	}

	private SitePois() {
	}
}
