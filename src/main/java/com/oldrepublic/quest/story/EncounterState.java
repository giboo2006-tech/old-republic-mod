package com.oldrepublic.quest.story;

/** Where Smoke on the Ridge stands. Stored by ordinal; only append. */
public enum EncounterState {
	/** No site chosen yet. */
	NOT_PLACED,
	/** The site is chosen (and the camp built, if structures are allowed); nobody spawned yet. */
	PLACED,
	/** The crew and the captives are in the world. */
	SPAWNED,
	/** Resolved and rewarded. */
	DONE;

	public static EncounterState of(int ordinal) {
		EncounterState[] values = values();
		return values[Math.max(0, Math.min(values.length - 1, ordinal))];
	}
}
