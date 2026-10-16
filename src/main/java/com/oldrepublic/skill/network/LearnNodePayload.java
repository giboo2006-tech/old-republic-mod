package com.oldrepublic.skill.network;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client request to buy one rank of a node, or to switch to it inside its group ({@code learn_node}). */
public record LearnNodePayload(String nodeId) implements CustomPacketPayload {
	public static final Type<LearnNodePayload> TYPE = new Type<>(OldRepublicMod.id("learn_node"));
	public static final StreamCodec<RegistryFriendlyByteBuf, LearnNodePayload> CODEC = ByteBufCodecs.stringUtf8(64)
			.map(LearnNodePayload::new, LearnNodePayload::nodeId)
			.cast();

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
