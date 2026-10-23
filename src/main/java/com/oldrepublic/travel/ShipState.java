package com.oldrepublic.travel;

import java.util.Locale;

/** A ship's state (spec MILESTONE_4.md 4.1). Stored and synced by ordinal: append only. */
public enum ShipState {
	/** The hulk under its tarp, before the four repairs. */
	HULK,
	/** Repaired, tank empty. */
	GROUNDED,
	/** Can launch. */
	READY,
	LAUNCHING,
	IN_TRANSIT,
	LANDING,
	/** Hull below the jump minimum. */
	DISABLED,
	/**
	 * Milestone 5 (spec MILESTONE_5.md 4.2): the owner flies the ship by hand, in a sky or an orbit.
	 * The record's position stays the last landing spot, so a ship found in this state with nobody
	 * at the helm belongs there (4.10); W1 writes that handling.
	 */
	FLYING;

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/**
	 * Between the start of the spool and the end of the settle, or at the helm: riders are locked in
	 * (MILESTONE_4.md 4.6, MILESTONE_5.md 4.2).
	 */
	public boolean flying() {
		return this == LAUNCHING || this == IN_TRANSIT || this == LANDING || this == FLYING;
	}
}
