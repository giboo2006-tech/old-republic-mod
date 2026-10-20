package com.oldrepublic.travel.network;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The server's word on a piloted ship's motion ({@code flight_motion}, S2C, spec MILESTONE_5.md 4.7,
 * 18.2): sent to the pilot right after the server moved their ship itself (into an orbit or a sky,
 * back from the orbit's edge, out of a jump into an orbit), together with the vehicle's position.
 * The pilot's client flies on from there with this velocity and heading instead of counting the
 * move as a correction. With {@code turnLook} the pilot's own look turns to the heading too (the
 * edge's turn back toward the system).
 *
 * @param vx       the velocity, blocks per tick
 * @param yaw      the ship's heading
 * @param turnLook whether the pilot's look turns with it
 */
public record FlightMotionPayload(float vx, float vy, float vz, float yaw, boolean turnLook) implements CustomPacketPayload {
	public static final Type<FlightMotionPayload> TYPE = new Type<>(OldRepublicMod.id("flight_motion"));
	public static final StreamCodec<RegistryFriendlyByteBuf, FlightMotionPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.FLOAT, FlightMotionPayload::vx,
			ByteBufCodecs.FLOAT, FlightMotionPayload::vy,
			ByteBufCodecs.FLOAT, FlightMotionPayload::vz,
			ByteBufCodecs.FLOAT, FlightMotionPayload::yaw,
			ByteBufCodecs.BOOL, FlightMotionPayload::turnLook,
			FlightMotionPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
