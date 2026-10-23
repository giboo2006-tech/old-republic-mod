package com.oldrepublic.registry;

import com.oldrepublic.boss.drops.BossDropsFeature;
import com.oldrepublic.saber.SaberM7Feature;
import com.oldrepublic.saber.forge.ForgeFeature;
import com.oldrepublic.world.ore.OreFeature;

/**
 * Milestone 7's Phase 0 contracts (spec MILESTONE_7.md 12.1): the shared blocks and items, then the workers'
 * features, which register their own content from their own init. Called once from {@code OldRepublicMod}
 * after the structures, before the commands and the creative tab.
 */
public final class M7Contracts {
	public static void init() {
		M7Items.init();
		SaberM7Feature.init();
		ForgeFeature.init();
		OreFeature.init();
		BossDropsFeature.init();
	}

	private M7Contracts() {
	}
}
