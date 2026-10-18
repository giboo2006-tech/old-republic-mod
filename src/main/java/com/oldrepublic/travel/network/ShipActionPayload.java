package com.oldrepublic.travel.network;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * A ship screen intent ({@code ship_action}, C2S, spec MILESTONE_4.md 4.10, 16.2): a button and its
 * argument. The server checks that the sender has the ship menu open and may use the button, and
 * validates everything else; see {@code ShipActions}.
 *
 * @param button one of the constants below
 * @param arg    the destination ordinal ({@link #LAUNCH}), the socket ({@link #FIT_SOCKET}), the
 *               cells wanted, 0 for all that fit ({@link #FUEL}), or the upgrade bit
 * @param target a player's UUID ({@link #ADD_CREW}, {@link #REMOVE_CREW}), else empty
 */
public record ShipActionPayload(int button, int arg, String target) implements CustomPacketPayload {
	public static final int LAUNCH = 0;
	public static final int FIT_SOCKET = 1;
	public static final int FUEL = 2;
	public static final int FIT_UPGRADE = 3;
	public static final int REMOVE_UPGRADE = 4;
	public static final int ADD_CREW = 5;
	public static final int REMOVE_CREW = 6;
	/** Asks for a fresh {@code ship_status}. */
	public static final int REFRESH = 7;

	public static final Type<ShipActionPayload> TYPE = new Type<>(OldRepublicMod.id("ship_action"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ShipActionPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, ShipActionPayload::button,
			ByteBufCodecs.VAR_INT, ShipActionPayload::arg,
			ByteBufCodecs.stringUtf8(64), ShipActionPayload::target,
			ShipActionPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
