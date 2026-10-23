package com.oldrepublic.force.lift;

/** The exclusive Telekinetic Lift upgrades (skill group {@code lift_shape}). Append only. */
public enum LiftShape {
	NONE(""),
	/** Holds longer; allies hit the lifted target harder. */
	SUSPEND("lift_suspend"),
	/** The release throws the target along the aim. */
	HURL("lift_hurl");

	private final String nodeId;

	LiftShape(String nodeId) {
		this.nodeId = nodeId;
	}

	/** Skill node id, or {@code ""} for {@link #NONE}. */
	public String nodeId() {
		return nodeId;
	}
}
