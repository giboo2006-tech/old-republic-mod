package com.oldrepublic.space.network;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client intent from a ship's weapons (spec MILESTONE_5.md 5.3, 18.2): a trigger pressed or
 * released. The server fires while it is held, from the ship's server position and heading, and
 * ignores a sender who is not the pilot (or the gunner, for the primary trigger).
 *
 * @param weapon {@link #PRIMARY} (the attack button: the pilot's cannons, the gunner's turret) or
 *               {@link #MISSILE} (the use button: the pilot's missile lock, fired on release)
 */
public record ShipFirePayload(int weapon, boolean pressed) implements CustomPacketPayload {
	public static final int PRIMARY = 0;
	public static final int MISSILE = 1;

	public static final Type<ShipFirePayload> TYPE = new Type<>(OldRepublicMod.id("ship_fire"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ShipFirePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, ShipFirePayload::weapon,
			ByteBufCodecs.BOOL, ShipFirePayload::pressed,
			ShipFirePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
