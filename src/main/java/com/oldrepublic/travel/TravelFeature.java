package com.oldrepublic.travel;

/**
 * Milestone 4 server and common initialisation for the courier shuttle, repairs, flight, pads, the
 * landing beacon and the ship commands (section 4). Owned by W3 ships and travel; the lead calls it
 * from {@code OldRepublicMod} after the Phase 0 contracts, so the worker never edits the mod's
 * entry point. Registers this feature's blocks, entity, particles, nav data component, menu,
 * payloads, events, commands and dialogue hooks.
 *
 * <p>The {@code ship} and {@code travel} balance sections ({@link ShipBalance}) are read on first
 * use, which the restart recovery does at server start.
 */
public final class TravelFeature {
	public static void init() {
		ShipEntities.init();
		TravelBlocks.init();
		TravelParticles.init();
		NavData.init();
		TravelVisits.init();
		ShipMenu.init();
		TravelNetwork.init();
		ShipEvents.init();
		CombatLock.init();
		ShipCommands.init();
		TravelDialogue.init();
	}

	private TravelFeature() {
	}
}
