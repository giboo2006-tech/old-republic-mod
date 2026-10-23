package com.oldrepublic.saber.network;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client intent for a dual-wielder's off-hand strike ({@code offhand_strike}, spec MILESTONE_5.md
 * 10.2, 20): the attack button pressed while the main hand is still recovering and the off hand is
 * ready. The client has already swung its off hand. The server checks everything again
 * ({@code SaberDual#strike}): the vanilla attack packet carries no hand and the server checks melee
 * reach for the main hand only, so the off hand needs its own payload.
 *
 * @param targetId the entity under the off saber's reach, or -1 for a swing at nothing
 */
public record OffhandStrikePayload(int targetId) implements CustomPacketPayload {
	public static final Type<OffhandStrikePayload> TYPE = new Type<>(OldRepublicMod.id("offhand_strike"));
	public static final StreamCodec<RegistryFriendlyByteBuf, OffhandStrikePayload> CODEC = StreamCodec.of(OffhandStrikePayload::write, OffhandStrikePayload::read);

	private static void write(RegistryFriendlyByteBuf buf, OffhandStrikePayload payload) {
		buf.writeVarInt(payload.targetId);
	}

	private static OffhandStrikePayload read(RegistryFriendlyByteBuf buf) {
		return new OffhandStrikePayload(buf.readVarInt());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
