package com.oldrepublic.world.site.far;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

/**
 * Request options beyond the frozen {@link FarSites.Request} chain (docs/MILESTONE_6.md 7.1 lets
 * workers add only through the lead): kept apart so the contract's signatures stay as they are.
 */
public final class FarRequests {
	/**
	 * Keeps the target in one biome: for sites whose biome has no tag of its own (the planets'
	 * verse stones, the Hymnal, the debtor camp). Works with {@link FarSites.Request#inBiomes}: a
	 * target must then satisfy both.
	 */
	public static FarSites.Request inBiome(FarSites.Request request, ResourceKey<Biome> biome) {
		request.biome = biome;
		return request;
	}

	private FarRequests() {
	}
}
