package com.oldrepublic.boss;

/**
 * The Reliquary heart's state (spec 8.2). Saved by ordinal in the heart's {@code State} tag, so only
 * append. {@code ACTIVE} is never restored after a reload: a fight in progress simply resets.
 */
public enum HeartState {
	/** Placed; the owner is away. */
	DORMANT,
	/** The owner is within {@code readyRange}; Vasch stands at the lectern. */
	READY,
	/** A fight is running. */
	ACTIVE,
	/** He kneels at the dais until the owner chooses; persists. */
	YIELDED,
	/** The owner has chosen; the hoard is open; the rematch is available. */
	RESOLVED;

	/** The state at {@code ordinal}, {@link #DORMANT} when out of range. */
	public static HeartState of(int ordinal) {
		HeartState[] values = values();
		return ordinal >= 0 && ordinal < values.length ? values[ordinal] : DORMANT;
	}

	/** Whether the owner's choice is still to come or made (the states the heart keeps over a reload). */
	public boolean persists() {
		return this == YIELDED || this == RESOLVED;
	}
}
