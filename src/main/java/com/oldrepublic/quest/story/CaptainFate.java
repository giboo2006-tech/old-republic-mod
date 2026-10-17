package com.oldrepublic.quest.story;

/**
 * What became of Captain Hessa Dray. Stored by ordinal; only append. {@link #DIED} covers a death
 * nobody chose (another creature, lava, the void): it resolves the mission without moving
 * alignment.
 */
public enum CaptainFate {
	NONE,
	YIELDED,
	SPARED,
	EXECUTED,
	FLED,
	DIED;

	/** Whether the Captain no longer stands between the player and completing the mission. */
	public boolean resolved() {
		return this == SPARED || this == EXECUTED || this == FLED || this == DIED;
	}

	public static CaptainFate of(int ordinal) {
		CaptainFate[] values = values();
		return values[Math.max(0, Math.min(values.length - 1, ordinal))];
	}
}
