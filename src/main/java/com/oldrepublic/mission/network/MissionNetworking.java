package com.oldrepublic.mission.network;

import com.oldrepublic.mission.MissionBoard;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/**
 * The mission feature's payloads (spec 4.7, 16): the board (S2C), the board actions (C2S) and the
 * tracker hint (S2C). The server trusts nothing the client sends: {@link MissionBoard} re-checks
 * every action.
 */
public final class MissionNetworking {
	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(MissionBoardPayload.TYPE, MissionBoardPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(TrackerHintPayload.TYPE, TrackerHintPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(MissionActionPayload.TYPE, MissionActionPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(MissionActionPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();

			switch (payload.action()) {
				case MissionActionPayload.ACCEPT -> MissionBoard.accept(player, payload.terminal(), payload.offer());
				case MissionActionPayload.ABANDON -> MissionBoard.abandon(player, payload.terminal());
			}
		});
	}

	/** Opens or refreshes the board screen. */
	public static void sendBoard(ServerPlayer p, MissionBoardPayload payload) {
		ServerPlayNetworking.send(p, payload);
	}

	/** One tracker hint. */
	public static void sendHint(ServerPlayer p, TrackerHintPayload payload) {
		ServerPlayNetworking.send(p, payload);
	}

	private MissionNetworking() {
	}
}
