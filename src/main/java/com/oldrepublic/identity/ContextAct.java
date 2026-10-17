package com.oldrepublic.identity;

/**
 * Contextual acts that move alignment by a small, capped amount. The identity feature detects most
 * of them itself; abilities report only {@link #HELPED_OTHER} (healing someone else).
 */
public enum ContextAct {
	HELPED_OTHER,
	DEFENDED_INNOCENT,
	CRUELTY,
	HARMED_INNOCENT,
	KILLED_INNOCENT,
	/** Killed a trooper that was not hostile to the player (spec section 5.2). */
	KILLED_SOLDIER
}
