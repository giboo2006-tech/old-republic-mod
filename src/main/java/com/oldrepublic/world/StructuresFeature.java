package com.oldrepublic.world;

import com.oldrepublic.world.build.BuildBlocks;
import com.oldrepublic.world.gen.ClearOfOriginPlacement;
import com.oldrepublic.world.site.SiteMeta;
import com.oldrepublic.world.site.far.FarSitesFeature;

/**
 * Milestone 6, the structures overhaul (docs/MILESTONE_6.md 7.1): the build kit, the
 * {@code oldrepublic:clear_of_origin} structure placement, site meta and far placement. Called from
 * {@code OldRepublicMod} after the Milestone 5 features.
 */
public final class StructuresFeature {
	public static void init() {
		BuildBlocks.init();
		ClearOfOriginPlacement.init();
		SiteMeta.init();
		FarSitesFeature.init();
	}

	private StructuresFeature() {
	}
}
