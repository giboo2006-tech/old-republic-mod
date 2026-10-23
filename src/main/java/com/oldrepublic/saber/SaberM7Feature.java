package com.oldrepublic.saber;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

/**
 * Milestone 7's saber hooks (docs/MILESTONE_7.md 3.3 and 7.2): the hilt materials' traits and the boss sets'
 * bonuses. Called once from {@code M7Contracts.init()}.
 * <ul>
 * <li>{@link HiltTraits}: aurelite's (and the hilt's own) Force cost modifier and hymnsteel's Force damage
 * modifier; starfall iron, duskglass and sunstone act in {@link SaberCombat}, clan iron in {@link SaberGuard};</li>
 * <li>{@link HiltSetEffects}: the three-part effects, hooked into {@link SaberCombat} and {@link SaberGuard};
 * the two-part bonuses are {@link SaberStats}' numbers;</li>
 * <li>a killing saber blow still has its hilt's on-hit effects ({@link SaberCombat#afterKill}).</li>
 * </ul>
 */
public final class SaberM7Feature {
	public static void init() {
		HiltTraits.init();
		HiltSetEffects.init();
		// API-CHECK: Fabric's AFTER_DAMAGE is assumed to skip a killing blow (AFTER_DEATH fires instead); if both fire, the hit is still handled once.
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> SaberCombat.afterKill(entity));
	}

	private SaberM7Feature() {
	}
}
