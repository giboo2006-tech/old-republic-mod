package com.oldrepublic.force.jump;

/**
 * The exclusive Force Jump upgrades (skill group {@code jump_shape}). The ordinal travels in effect
 * events, so only append new values.
 */
public enum JumpShape {
	NONE(""),
	/** Landing from a height sends out a damaging shockwave. */
	CRUSH("jump_crushing_descent"),
	/** Further, higher leaps and a short glide while Jump is held. */
	SOAR("jump_soaring_leap");

	private final String nodeId;

	JumpShape(String nodeId) {
		this.nodeId = nodeId;
	}

	/** Skill node id, or {@code ""} for {@link #NONE}. */
	public String nodeId() {
		return nodeId;
	}

	public static JumpShape byOrdinal(int ordinal) {
		JumpShape[] values = values();
		return ordinal >= 0 && ordinal < values.length ? values[ordinal] : NONE;
	}
}
