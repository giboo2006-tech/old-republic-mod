package com.oldrepublic.dialogue.network;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: the dialogue session ended (close the screen if it shows this session). */
public record DialogueEndPayload(int session) implements CustomPacketPayload {
	public static final Type<DialogueEndPayload> TYPE = new Type<>(OldRepublicMod.id("dialogue_end"));
	public static final StreamCodec<RegistryFriendlyByteBuf, DialogueEndPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, DialogueEndPayload::session,
			DialogueEndPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
