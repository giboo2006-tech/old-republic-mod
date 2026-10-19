package com.oldrepublic.client.travel;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.travel.ShipBody;

/**
 * The local player at a ship's controls (spec MILESTONE_5.md 4.2, 4.3, 5.2): whether they fly it by
 * hand, whether they man its gunner seat, and the ship. Owned by W1 flight; other client code (W2's
 * combat HUD, the camera, the key handlers) reads it. Phase 0 stub: nobody pilots or mans a gun
 * until W1 fills it; W1 keeps these signatures and may add methods.
 */
public final class Piloting {
	/** Whether the local player flies a ship by hand (its pilot, with the piloted flag set). */
	public static boolean isPiloting() {
		// Phase 0 stub: W1 fills it.
		return false;
	}

	/** Whether the local player sits in a ship's gunner seat (5.2). */
	public static boolean isGunner() {
		// Phase 0 stub: W1 fills it.
		return false;
	}

	/** The ship the local player pilots or mans, else null. */
	public static @Nullable ShipBody ship() {
		// Phase 0 stub: W1 fills it.
		return null;
	}

	private Piloting() {
	}
}
