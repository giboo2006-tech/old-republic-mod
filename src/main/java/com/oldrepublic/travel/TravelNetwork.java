package com.oldrepublic.travel;

import java.util.List;

import com.oldrepublic.travel.network.FlightPhasePayload;
import com.oldrepublic.travel.network.ShipActionPayload;
import com.oldrepublic.travel.network.ShipStatusPayload;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * The ship's payloads (spec MILESTONE_4.md 16.2): {@code ship_action} in, {@code ship_status} and
 * {@code flight_phase} out. Nothing is sent to a player whose client cannot receive it (a test's
 * fake player).
 */
final class TravelNetwork {
	static void init() {
		PayloadTypeRegistry.serverboundPlay().register(ShipActionPayload.TYPE, ShipActionPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ShipStatusPayload.TYPE, ShipStatusPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(FlightPhasePayload.TYPE, FlightPhasePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ShipActionPayload.TYPE, (payload, context) -> ShipActions.handle(context.player(), payload));
	}

	/** Sends {@code payload} when the player's client can receive it. */
	static void send(ServerPlayer player, CustomPacketPayload payload) {
		if (ServerPlayNetworking.canSend(player, payload.type())) {
			ServerPlayNetworking.send(player, payload);
		}
	}

	/** A toast for the player (ship repaired, destination unlocked), carried by a {@code ship_status} of their own ship. */
	static void notice(ServerPlayer player, int notice, int arg) {
		ShipStatusPayload status = ShipActions.status(player, player.getUUID(), notice, arg);

		if (status == null) {
			status = new ShipStatusPayload(ShipStatusPayload.Ship.EMPTY, false, false, false, List.of(), List.of(), List.of(), notice, arg,
					ShipStatusPayload.Air.GROUND);
		}

		send(player, status);
	}

	private TravelNetwork() {
	}
}
