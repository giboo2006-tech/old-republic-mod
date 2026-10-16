package com.oldrepublic.holocron;

/**
 * What a player did with an attuned holocron (spec sections 4.4 and 4.5): kept it as its owner,
 * listened to an opposed-side holocron, sealed it, or broke it. Stored by ordinal, so only append.
 */
public enum HolocronChoice {
	OWN,
	LISTEN,
	SEAL,
	BREAK
}
