package com.oldrepublic.force.pull;

/**
 * The exclusive Force Pull upgrades (skill group {@code pull_shape}). The ordinal travels in
 * effect events, so only append new values.
 */
public enum PullShape {
	NONE(""),
	/** Pulls the weapon out of line: no shooting, no guard, a short item cooldown in PvP. */
	DISARM("pull_disarm"),
	/** A harder pull that staggers on arrival and empowers the next saber hit. */
	REEL("pull_reel");

	private final String nodeId;

	PullShape(String nodeId) {
		this.nodeId = nodeId;
	}

	/** Skill node id, or {@code ""} for {@link #NONE}. */
	public String nodeId() {
		return nodeId;
	}

	public static PullShape byOrdinal(int ordinal) {
		PullShape[] values = values();
		return ordinal >= 0 && ordinal < values.length ? values[ordinal] : NONE;
	}

	/** Speed multiplier of this shape. */
	public float speedMult(PullBalance b) {
		return switch (this) {
			case DISARM -> b.disarmKnockMult;
			case REEL -> b.reelKnockMult;
			case NONE -> 1f;
		};
	}
}
