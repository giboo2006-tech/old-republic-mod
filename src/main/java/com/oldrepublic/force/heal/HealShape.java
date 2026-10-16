package com.oldrepublic.force.heal;

/**
 * The exclusive Force Heal upgrades (skill group {@code heal_shape}). The ordinal travels in effect
 * events, so only append new values.
 */
public enum HealShape {
	NONE(""),
	/** Also heals allies, settlers, villagers and tamed animals nearby. */
	SHARED("heal_shared_light"),
	/** A channel of at least a second ends with absorption and restored saber stamina. */
	WARD("heal_serene_ward");

	private final String nodeId;

	HealShape(String nodeId) {
		this.nodeId = nodeId;
	}

	/** Skill node id, or {@code ""} for {@link #NONE}. */
	public String nodeId() {
		return nodeId;
	}
}
