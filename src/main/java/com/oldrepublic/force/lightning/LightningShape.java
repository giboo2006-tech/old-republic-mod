package com.oldrepublic.force.lightning;

/**
 * The exclusive Force Lightning upgrades (skill group {@code lightning_shape}). The ordinal travels
 * in effect events, so only append new values.
 */
public enum LightningShape {
	NONE(""),
	/** Arcs jump from the primary target to up to two more nearby. */
	CHAIN("lightning_chain"),
	/** One target; damage ramps with unbroken contact and part of it heals the caster. */
	CONSUMING("lightning_consuming");

	private final String nodeId;

	LightningShape(String nodeId) {
		this.nodeId = nodeId;
	}

	/** Skill node id, or {@code ""} for {@link #NONE}. */
	public String nodeId() {
		return nodeId;
	}

	public static LightningShape byOrdinal(int ordinal) {
		LightningShape[] values = values();
		return ordinal >= 0 && ordinal < values.length ? values[ordinal] : NONE;
	}
}
