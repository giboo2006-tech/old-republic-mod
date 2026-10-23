package com.oldrepublic.ability;

/** What the player did with an ability slot key. */
public enum AbilityAction {
	/** Key pressed. */
	START,
	/** Key released (fires hold-and-release powers, ends channels). */
	RELEASE,
	/** The client gave up (a screen opened, another slot was pressed). */
	CANCEL
}
