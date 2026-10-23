package com.oldrepublic.combat;

/**
 * Who an incident squad fights besides its usual targets (spec 5.2, 7.5). Saved on each trooper
 * with its expiry, by ordinal: append only.
 */
public enum IncidentMode {
	/** No incident: the cold peace holds. */
	NONE,
	/** Hostile to the other army. */
	ARMY,
	/** Hostile to the other army, and engaged with the mission's owner from the start. */
	ARMY_AND_OWNER
}
