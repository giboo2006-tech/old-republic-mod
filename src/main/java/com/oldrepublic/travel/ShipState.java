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
	DISABLED;

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** Between the start of the spool and the end of the settle: riders are locked in (4.6). */
	public boolean flying() {
		return this == LAUNCHING || this == IN_TRANSIT || this == LANDING;
	}
}
