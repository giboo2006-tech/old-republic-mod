package com.oldrepublic.force;

/**
 * The exclusive Force Push specialisations. The id is stored in
 * {@link com.oldrepublic.progression.ForceData#pushBranch}; the ordinal travels in effect events,
 * so only append new values.
 */
public enum PushBranch {
	/** No branch chosen yet: the base push. */
	NONE(""),
	/** Wide, short cone that staggers crowds. */
	REPULSE_WAVE("repulse_wave"),
	/** Narrow, long spear that slams a single foe into walls. */
	FOCUSED_BLAST("focused_blast"),
	/** Defensive 360 degree pulse that turns projectiles around. */
	DEFLECTING_PULSE("deflecting_pulse");

	private final String id;

	PushBranch(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	/** The skill node of this branch ({@code push_repulse_wave} and so on), or {@code ""} for {@link #NONE}. */
	public String nodeId() {
		return id.isEmpty() ? "" : "push_" + id;
	}

	/** The branch with this id, or {@link #NONE} for empty or unknown ids. */
	public static PushBranch byId(String id) {
		for (PushBranch branch : values()) {
			if (branch.id.equals(id)) {
				return branch;
			}
		}

		return NONE;
	}

	/** The branch with this ordinal, or {@link #NONE} when out of range. */
	public static PushBranch byOrdinal(int ordinal) {
		PushBranch[] values = values();
		return ordinal >= 0 && ordinal < values.length ? values[ordinal] : NONE;
	}
}
