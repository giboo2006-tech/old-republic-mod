package com.oldrepublic.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/** Registers payload types shared by all features. Features register their own payloads too. */
public final class ModNetworking {
	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(EffectEventPayload.TYPE, EffectEventPayload.CODEC);
	}

	private ModNetworking() {
	}
}
