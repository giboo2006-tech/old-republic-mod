package com.oldrepublic.progression;

/**
 * An enemy that pays its own kill XP (spec MILESTONE_5.md 16.3) instead of the M1 rule's
 * {@code min(killXpCap, max health * killXpPerHealth)}. The anti-grind fatigue applies to it all the same.
 */
public interface KillXp {
	/** The XP for killing this enemy, before fatigue. */
	int killXp();
}
