package com.oldrepublic.enemy.hymn;

/**
 * A Milestone 5 foe whose tell in progress a stagger, a Push or a shock stops (the M3 and M4 rule,
 * spec MILESTONE_5.md 12.1). {@link FoeReactions} calls {@link #interrupt()} from the shared Force
 * hooks; the foe drops the tell on its next server tick.
 */
public interface Interruptible {
	/** Stops the tell in progress (the bruiser's string, the captain's frag, a storm call, a charge). */
	void interrupt();
}
