package com.oldrepublic.saber.duel;

/**
 * Rules of one spar.
 *
 * @param goal                 what ends it as a win
 * @param count                parries or technique hits needed (ignored for {@link Goal#YIELD})
 * @param partnerYieldFraction health fraction at which the partner yields (the spar is won)
 * @param partnerDamageScale   multiplier on the partner's damage to the player
 */
public record SparRules(Goal goal, int count, float partnerYieldFraction, float partnerDamageScale) {
	public enum Goal {
		YIELD,
		PARRIES,
		TECHNIQUE_HITS
	}
}
