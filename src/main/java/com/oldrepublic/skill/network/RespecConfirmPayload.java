package com.oldrepublic.skill.network;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The player confirmed a full respec in the Force screen ({@code respec_confirm}). Validated on the server. */
public record RespecConfirmPayload() implements CustomPacketPayload {
	public static final RespecConfirmPayload INSTANCE = new RespecConfirmPayload();
	public static final Type<RespecConfirmPayload> TYPE = new Type<>(OldRepublicMod.id("respec_confirm"));
	public static final StreamCodec<RegistryFriendlyByteBuf, RespecConfirmPayload> CODEC = StreamCodec.of((buf, p) -> {
	}, buf -> INSTANCE);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
