package com.oldrepublic.combat;

/**
 * How an entity regards one particular player (spec 5.2). Attitudes are per player: the same squad
 * can be Allied to one player and Wary to another. Synced by ordinal: append only.
 */
public enum Attitude {
	/** Attacks on sight. */
	HOSTILE,
	/** Warns, then opens fire if the player stays close. */
	WARY,
	/** Ignores the player. */
	NEUTRAL,
	/** Defends the player; its bolts pass through them. */
	ALLIED
}
