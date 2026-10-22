package com.oldrepublic.world.site;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.world.site.far.FarSites;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

/**
 * How the runtime placer sits a template in the ground (docs/MILESTONE_6.md 3.4,
 * docs/STRUCTURES.md 7): read from {@code data/<namespace>/site_meta/<template path>.json}, written by
 * the template's generator. Every field is optional; a template without a file gets {@link #DEFAULT}.
 *
 * @param buried       layers below the ground the player walks on (the feet layer)
 * @param maxSlope     the most the ground may vary under the footprint
 * @param clearance    blocks kept clear of other sites and built blocks around the footprint
 * @param supportDepth footings filled down to solid ground, at most this deep
 * @param rampRings    outer rings stepped down toward lower ground
 * @param clearAbove   trees and terrain removed above the template's top, this many blocks
 * @param terrain      the ground far placement prefers
 * @param biomes       a biome tag far placement keeps to, or null
 * @param spawnRadius  how close the owner comes before the site's people spawn
 * @param links        companion templates placed with this one and their origin offsets (unrotated)
 *
 * <p>Phase 0 contract (7.1): the record and the signatures are frozen. The Phase 0 body of
 * {@link #of} is a stub that answers {@link #DEFAULT}; J1 implements the loader.
 */
public record SiteMeta(int buried, int maxSlope, int clearance, int supportDepth, int rampRings, int clearAbove,
		FarSites.Terrain terrain, @Nullable TagKey<Biome> biomes, int spawnRadius, Map<Identifier, BlockPos> links) {
	public static final SiteMeta DEFAULT = new SiteMeta(1, 4, 8, 8, 2, 6, FarSites.Terrain.ANY, null, 64, Map.of());

	public SiteMeta {
		links = Map.copyOf(links);
	}

	/** The meta of a template: its file's values over the defaults, or {@link #DEFAULT} when it has no file. */
	public static SiteMeta of(Identifier template) {
		return DEFAULT;
	}

	/** Registers the loader (server start and {@code /reload}); called from {@code StructuresFeature}. */
	public static void init() {
	}
}
