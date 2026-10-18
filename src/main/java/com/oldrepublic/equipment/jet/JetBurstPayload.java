package com.oldrepublic.equipment.jet;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * {@code oldrepublic:jet_burst} (C2S, spec MILESTONE_4.md 16.2): the wearer pressed jump while
 * airborne. It carries nothing; the server checks everything ({@link JetPack#burst}).
 */
public record JetBurstPayload() implements CustomPacketPayload {
	public static final JetBurstPayload INSTANCE = new JetBurstPayload();
	public static final Type<JetBurstPayload> TYPE = new Type<>(OldRepublicMod.id("jet_burst"));
	public static final StreamCodec<RegistryFriendlyByteBuf, JetBurstPayload> CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
