package com.oldrepublic.dialogue.network;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client intent: pick option {@code option} (its index in the node) in dialogue session {@code session}. */
public record DialogueChoicePayload(int session, int option) implements CustomPacketPayload {
	public static final Type<DialogueChoicePayload> TYPE = new Type<>(OldRepublicMod.id("dialogue_choice"));
	public static final StreamCodec<RegistryFriendlyByteBuf, DialogueChoicePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, DialogueChoicePayload::session,
			ByteBufCodecs.VAR_INT, DialogueChoicePayload::option,
			DialogueChoicePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
