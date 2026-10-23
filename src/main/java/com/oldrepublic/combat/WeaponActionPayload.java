package com.oldrepublic.combat;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client intent from the Weapon Action key (spec MILESTONE_4.md 11.1): pressed or released. */
public record WeaponActionPayload(boolean pressed) implements CustomPacketPayload {
	public static final Type<WeaponActionPayload> TYPE = new Type<>(OldRepublicMod.id("weapon_action"));
	public static final StreamCodec<RegistryFriendlyByteBuf, WeaponActionPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, WeaponActionPayload::pressed,
			WeaponActionPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
