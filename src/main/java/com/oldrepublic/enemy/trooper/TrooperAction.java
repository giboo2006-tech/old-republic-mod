package com.oldrepublic.enemy.trooper;

/**
 * What a trooper is visibly doing right now, synced by ordinal ({@link TrooperEntity#action()}) so
 * the client can pose the model and run the matching effects. Append only.
 */
public enum TrooperAction {
	/** Default stance. */
	IDLE,
	/** The 12-tick charge tell before a shot: rifle raised, muzzle glow. */
	CHARGING,
	/** A Republic rifle venting after its bursts: rifle lowered, steam and shimmer. */
	VENTING,
	/** A medic kneeling over a wounded ally during the kolto channel. */
	HEALING,
	/** A Republic sergeant's raised fist before a Rally. */
	RALLY_TELL,
	/** An Imperial sergeant raising the flare pistol before calling reinforcements. */
	FLARE_TELL,
	/** A grenadier's throwing arm drawn back, the grenade blinking. */
	GRENADE_TELL,
	/** Right hand to the brow for an Allied player. */
	SALUTE,
	/** Rifle lowered across the body, facing a rival patrol under the cold peace. */
	STANDOFF,
	/** Hands behind the back: a trooper prisoner under escort. */
	BOUND,
	/** Reserved for a trooper kneeling to tend a downed comrade. Never sent yet. */
	KNEEL_TEND;

	/** The action with this ordinal, or {@link #IDLE} for an unknown one. */
	public static TrooperAction byOrdinal(int ordinal) {
		TrooperAction[] values = values();
		return ordinal >= 0 && ordinal < values.length ? values[ordinal] : IDLE;
	}
}
