package com.oldrepublic.saber.duel;

/**
 * How a saber duelist fights.
 *
 * @param yieldFraction health fraction at which it yields in a spar or encounter; 0 = never
 */
public record DuelistProfile(String id, float damage, float guardStamina, float parryChance, int stringMin, int stringMax,
		int windupTicks, int gapTicks, int recoveryTicks, boolean shove, float yieldFraction) {
}
