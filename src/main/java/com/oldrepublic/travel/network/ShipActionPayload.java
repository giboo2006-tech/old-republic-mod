package com.oldrepublic.travel.network;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * A ship screen intent ({@code ship_action}, C2S, spec MILESTONE_4.md 4.10, 16.2; MILESTONE_5.md
 * 18.2): a button and its argument. The server checks that the sender has the ship menu open and may
 * use the button (the helm's buttons: that they ride their own ship), and validates everything else;
 * see {@code ShipActions}.
 *
 * @param button one of the constants below
 * @param arg    the destination ordinal ({@link #LAUNCH}, {@link #HAND_TO_AUTOPILOT}, {@link #JUMP_ORBIT}),
 *               the socket ({@link #FIT_SOCKET}), the cells wanted, 0 for all that fit ({@link #FUEL}),
 *               or the upgrade bit
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
	/**
	 * Milestone 5: take the helm (MILESTONE_5.md 4.2): the Helm key, or the ship screen's button. Taken
	 * without an open screen from the owner riding their own ship.
	 */
	public static final int TAKE_HELM = 8;
	/**
	 * Milestone 5: land here (MILESTONE_5.md 4.6): the Helm key in the air, or the ship screen's
	 * button. Taken without an open screen from the owner flying their own ship.
	 */
	public static final int LAND = 9;
	/** Milestone 5: the rescue run to the last landing spot, for a cell or the free daily recall (4.10). */
	public static final int RETURN_TO_LANDING = 10;
	/** Milestone 5: the autopilot from the air to a destination (4.5). */
	public static final int HAND_TO_AUTOPILOT = 11;
	/** Milestone 5: a jump from orbit to a destination's orbit (4.7). */
	public static final int JUMP_ORBIT = 12;
	/**
	 * Milestone 5: the pilot's inventory key opens the ship screen (4.3). Taken without an open screen
	 * from the owner riding their own ship.
	 */
	public static final int OPEN_SCREEN = 13;

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
