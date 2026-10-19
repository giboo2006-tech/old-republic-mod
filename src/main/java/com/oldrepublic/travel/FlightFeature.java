package com.oldrepublic.travel;

import com.oldrepublic.travel.network.FlightBoostPayload;
import com.oldrepublic.travel.network.FlightStatusPayload;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * Milestone 5 server and common initialisation for free flight and the ship (section 4): the helm,
 * the flight model, landing anywhere, sky and orbit transitions, jumps from orbit, the M5 upgrades,
 * the interceptor refit, the disabled ship's rescue and leaving mid-flight (spec MILESTONE_5.md).
 * Owned by W1 flight and ships; the lead calls it from {@code OldRepublicMod} after the Phase 0
 * contracts, so the worker never edits the mod's entry point. Registers this feature's payloads
 * ({@code flight_status} out, {@code flight_boost} in, 18.2); the helm itself runs from the ship's
 * events ({@link ShipEvents}) and the Milestone 4 {@code ship_action}.
 */
public final class FlightFeature {
	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(FlightStatusPayload.TYPE, FlightStatusPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(FlightBoostPayload.TYPE, FlightBoostPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(FlightBoostPayload.TYPE, (payload, context) -> Helm.boost(context.player(), payload.pressed()));
	}

	private FlightFeature() {
	}
}
