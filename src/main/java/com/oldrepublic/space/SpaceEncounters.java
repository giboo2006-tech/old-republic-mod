package com.oldrepublic.space;

import net.minecraft.server.level.ServerPlayer;

/**
 * Encounters in orbit (spec MILESTONE_5.md 5.6): the random patrols and Chapter Five's blockade
 * over Tyrvann. Owned by W2 space. Phase 0 stub: no blockade starts or runs until W2 fills it; W2
 * keeps these signatures and may add methods.
 */
public final class SpaceEncounters {
	/** Starts {@code owner}'s blockade battle in Tyrvann's orbit; W4 calls it from {@code onOrbitEntered} during {@code BLOCKADE} (6.5). */
	public static void startBlockade(ServerPlayer owner) {
		// Phase 0 stub: W2 fills it.
	}

	/** Whether {@code owner}'s blockade battle is running. */
	public static boolean blockadeRunning(ServerPlayer owner) {
		// Phase 0 stub: W2 fills it.
		return false;
	}

	private SpaceEncounters() {
	}
}
