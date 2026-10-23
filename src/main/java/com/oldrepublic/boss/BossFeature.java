package com.oldrepublic.boss;

import com.oldrepublic.enemy.construct.ConstructFeature;

/**
 * Server and common initialisation for the boss feature: the Reliquary and Orren Vasch, the
 * Collector (spec section 8). Called from OldRepublicMod.
 *
 * <p>Order (W5 plan 4.1): the Ruin Sentinels and the Reliquary placement first, then the fight's
 * registrations and handlers.
 */
public final class BossFeature {
	public static void init() {
		ConstructFeature.init();
		ReliquarySites.init();

		BossBlocks.init();
		BossEntities.init();
		BossParticles.init();
		BossDamageTypes.init();
		CollectorReactions.init();
		BossArenas.init();
		CollectorDialogues.init();
		BossCommands.init();
	}

	private BossFeature() {
	}
}
