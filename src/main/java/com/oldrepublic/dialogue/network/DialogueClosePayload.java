package com.oldrepublic.dialogue.network;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client intent: the player closed the dialogue screen of session {@code session} without choosing. */
public record DialogueClosePayload(int session) implements CustomPacketPayload {
	public static final Type<DialogueClosePayload> TYPE = new Type<>(OldRepublicMod.id("dialogue_close"));
	public static final StreamCodec<RegistryFriendlyByteBuf, DialogueClosePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, DialogueClosePayload::session,
			DialogueClosePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
