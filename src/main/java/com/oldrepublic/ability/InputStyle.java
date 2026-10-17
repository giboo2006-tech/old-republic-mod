package com.oldrepublic.ability;

/** How an ability reads its key. */
public enum InputStyle {
	/** Fires on press. */
	TAP,
	/** Charges while held, fires on release. */
	HOLD_RELEASE,
	/** Runs while held, ends on release. */
	CHANNEL
}
