package com.oldrepublic.quest;

import org.jspecify.annotations.Nullable;

/**
 * How a boss fight ended for a player (spec sections 8.7 and 8.9): the owner's choice in the yield
 * dialogue, a co-op helper's stance, or a rematch against the echo. Stored by ordinal in
 * {@code StoryData.Chapter3}, so only append.
 */
public enum BossOutcome {
	SPARED,
	EXECUTED,
	BOUND,
	RELEASED,
	BROUGHT_TO_REPUBLIC,
	BROUGHT_TO_EMPIRE,
	ECHO;

	/** The outcome at this ordinal, or null for -1 (none) or an unknown value. */
	public static @Nullable BossOutcome of(int ordinal) {
		BossOutcome[] values = values();
		return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
	}
}
