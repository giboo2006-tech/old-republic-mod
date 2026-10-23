package com.oldrepublic.identity;

/**
 * Tiers of a faction reputation value (-1000 to +1000): Hostile up to -500, Distrusted up to -100,
 * Neutral from -99 to +99, Trusted from +100 and Honoured from +500.
 */
public enum ReputationTier {
	HOSTILE,
	DISTRUSTED,
	NEUTRAL,
	TRUSTED,
	HONOURED;

	private static final int STRONG = 500;
	private static final int MILD = 100;

	public static ReputationTier of(int rep) {
		if (rep >= STRONG) {
			return HONOURED;
		}

		if (rep >= MILD) {
			return TRUSTED;
		}

		if (rep <= -STRONG) {
			return HOSTILE;
		}

		if (rep <= -MILD) {
			return DISTRUSTED;
		}

		return NEUTRAL;
	}
}
