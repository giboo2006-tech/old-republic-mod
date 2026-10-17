package com.oldrepublic.skill.network;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client request from the Force screen to make an owned form active, or {@code ""} for Unformed ({@code set_form}). */
public record SetFormPayload(String formId) implements CustomPacketPayload {
	public static final Type<SetFormPayload> TYPE = new Type<>(OldRepublicMod.id("set_form"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SetFormPayload> CODEC = ByteBufCodecs.stringUtf8(64)
			.map(SetFormPayload::new, SetFormPayload::formId)
			.cast();

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
