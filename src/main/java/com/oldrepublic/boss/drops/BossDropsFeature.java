package com.oldrepublic.boss.drops;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

/**
 * Milestone 7's boss and elite drops (docs/MILESTONE_7.md 7.3 and 7.4): the elites' kill listener and the drops'
 * pity counters. The bosses' own drops are paid where their rewards are today ({@link BossDrops}); the chests' and
 * the poacher captain's parts are in their loot tables, the vendors' new stock in their stock files. Called once from
 * {@code M7Contracts.init()}.
 */
public final class BossDropsFeature {
	public static void init() {
		// The record of the drops and their pity counters (registers the attachment).
		BossDropsData.init();
		ServerLivingEntityEvents.AFTER_DEATH.register(EliteDrops::onDeath);
	}

	private BossDropsFeature() {
	}
}
