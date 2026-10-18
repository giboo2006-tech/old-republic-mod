package com.oldrepublic.travel;

/**
 * Milestone 5 server and common initialisation for free flight and the ship (section 4): the helm,
 * the flight model, landing anywhere, sky and orbit transitions, jumps from orbit, the M5 upgrades,
 * the interceptor refit, the disabled ship's rescue and leaving mid-flight (spec MILESTONE_5.md).
 * Owned by W1 flight and ships; the lead calls it from {@code OldRepublicMod} after the Phase 0
 * contracts, so the worker never edits the mod's entry point. Register this feature's own items,
 * blocks, entities, particles, payloads, balance section, events and commands from here.
 */
public final class FlightFeature {
	public static void init() {
	}

	private FlightFeature() {
	}
}
