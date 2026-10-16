package com.oldrepublic.saber.duel;

/** What a saber NPC's blade is doing, synced for its pose. */
public enum GuardState {
	READY,
	GUARDING,
	ATTACKING,
	RECOVERING,
	/** Guard broken: cannot attack or guard and takes extra damage for a short window. */
	BROKEN
}
