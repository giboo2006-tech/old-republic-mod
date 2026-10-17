package com.oldrepublic.ability.network;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to owner: an ability started or stopped charging or channelling ({@code ability_state}).
 * Drives the HUD's charge and channel arcs and the crosshair brackets with what the server really
 * accepted, so a refused press never shows a phantom charge.
 */
public record AbilityStatePayload(int slot, String abilityId, boolean active) implements CustomPacketPayload {
	public static final Type<AbilityStatePayload> TYPE = new Type<>(OldRepublicMod.id("ability_state"));
	public static final StreamCodec<RegistryFriendlyByteBuf, AbilityStatePayload> CODEC = StreamCodec.of(AbilityStatePayload::write, AbilityStatePayload::read);

	private static void write(RegistryFriendlyByteBuf buf, AbilityStatePayload p) {
		buf.writeVarInt(p.slot);
		buf.writeUtf(p.abilityId);
		buf.writeBoolean(p.active);
	}

	private static AbilityStatePayload read(RegistryFriendlyByteBuf buf) {
		return new AbilityStatePayload(buf.readVarInt(), buf.readUtf(), buf.readBoolean());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
