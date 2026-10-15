package com.oldrepublic.saber.network;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client intent from the Toggle Lightsaber key. Carries nothing: the server picks the held saber
 * and decides.
 */
public record ToggleSaberPayload() implements CustomPacketPayload {
	public static final ToggleSaberPayload INSTANCE = new ToggleSaberPayload();
	public static final Type<ToggleSaberPayload> TYPE = new Type<>(OldRepublicMod.id("toggle_saber"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ToggleSaberPayload> CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
