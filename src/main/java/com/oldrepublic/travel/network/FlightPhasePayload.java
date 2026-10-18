package com.oldrepublic.travel.network;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * A flight phase began ({@code flight_phase}, S2C, spec MILESTONE_4.md 16.2): sent to every rider at
 * each phase of 4.6, and with {@code phase} −1 when they are put off at the end of the Settle. The
 * client's flight HUD, camera and arrival banner follow it.
 *
 * @param phase       the 4.6 phase index (0 spool to 5 settle), or −1: the flight is over for this rider
 * @param length      the phase's length in ticks
 * @param destination the {@code Destination} ordinal
 * @param padY        the height of the pad the altitude is measured to (the origin's until the jump)
 * @param hull        hull integrity, 0 to 100
 * @param fuel        cells in the tank
 * @param tank        the tank's size
 */
public record FlightPhasePayload(int phase, int length, int destination, int padY, int hull, int fuel, int tank) implements CustomPacketPayload {
	public static final int OVER = -1;

	public static final Type<FlightPhasePayload> TYPE = new Type<>(OldRepublicMod.id("flight_phase"));
	public static final StreamCodec<RegistryFriendlyByteBuf, FlightPhasePayload> CODEC = StreamCodec.of(FlightPhasePayload::write, FlightPhasePayload::read);

	private static void write(RegistryFriendlyByteBuf buf, FlightPhasePayload p) {
		buf.writeVarInt(p.phase + 1);
		buf.writeVarInt(p.length);
		buf.writeVarInt(p.destination);
		buf.writeInt(p.padY);
		buf.writeVarInt(p.hull);
		buf.writeVarInt(p.fuel);
		buf.writeVarInt(p.tank);
	}

	private static FlightPhasePayload read(RegistryFriendlyByteBuf buf) {
		return new FlightPhasePayload(buf.readVarInt() - 1, buf.readVarInt(), buf.readVarInt(), buf.readInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
