package com.oldrepublic.space;

import com.oldrepublic.space.network.ShipCombatStatusPayload;
import com.oldrepublic.space.network.ShipFirePayload;
import com.oldrepublic.space.network.SpaceEncounterPayload;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Space's payloads (spec MILESTONE_5.md 18.2): the weapons' triggers ({@code ship_fire}, C2S), the
 * combat HUD's numbers ({@code ship_combat_status}, S2C) and the player's encounter or objective
 * ({@code space_encounter}, S2C). The server trusts nothing but the trigger's intent.
 */
public final class SpaceNetwork {
	static void init() {
		PayloadTypeRegistry.serverboundPlay().register(ShipFirePayload.TYPE, ShipFirePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ShipCombatStatusPayload.TYPE, ShipCombatStatusPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SpaceEncounterPayload.TYPE, SpaceEncounterPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ShipFirePayload.TYPE, (payload, context) -> ShipWeapons.trigger(context.player(), payload.weapon(),
				payload.pressed()));
	}

	/** Sends {@code payload} to {@code p} when their client takes it (a test's mock player may not). */
	static void send(ServerPlayer p, CustomPacketPayload payload) {
		if (ServerPlayNetworking.canSend(p, payload.type())) {
			ServerPlayNetworking.send(p, payload);
		}
	}

	private SpaceNetwork() {
	}
}
