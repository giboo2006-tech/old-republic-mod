package com.oldrepublic.saber.duel;

/** How a spar ended. {@link #ENDED} = without a result (death, logout, dimension change, server stop). */
public enum SparResult {
	WON,
	LOST,
	INTERRUPTED,
	ENDED
}
