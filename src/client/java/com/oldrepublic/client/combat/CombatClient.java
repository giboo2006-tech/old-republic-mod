package com.oldrepublic.client.combat;

/**
 * Client initialisation for the combat contracts: the client half of {@code Holds}
 * ({@link ClientHolds}). Called from {@code OldRepublicModClient}.
 */
public final class CombatClient {
	/** Registers the held-state input blocking and HUD hint. Call once during client init. */
	public static void init() {
		ClientHolds.init();
	}

	private CombatClient() {
	}
}
