package com.oldrepublic.travel.network;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The pilot's boost key ({@code flight_boost}, C2S, spec MILESTONE_5.md 4.3, 18.2): Sprint went
 * down or came up while flying by hand. The server checks the sender pilots their own ship, keeps
 * the meter, refuses an empty one and answers with a {@link FlightStatusPayload}.
 *
 * @param pressed the key is down
 */
public record FlightBoostPayload(boolean pressed) implements CustomPacketPayload {
	public static final Type<FlightBoostPayload> TYPE = new Type<>(OldRepublicMod.id("flight_boost"));
	public static final StreamCodec<RegistryFriendlyByteBuf, FlightBoostPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, FlightBoostPayload::pressed,
			FlightBoostPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
