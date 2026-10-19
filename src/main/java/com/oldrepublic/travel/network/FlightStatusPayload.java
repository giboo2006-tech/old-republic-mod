package com.oldrepublic.travel.network;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.travel.FlightModel;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The pilot's flight status ({@code flight_status}, S2C, spec MILESTONE_5.md 4.2, 4.3, 18.2): sent
 * to the owner when they take the helm, at each step of the take-off, while they fly (when the
 * boost changes, and every few ticks) and once with {@link #HELM_NONE} when the helm is released.
 * It carries the numbers the pilot's client flies the ship by, so a server's balance reaches it.
 *
 * @param helm     {@link #HELM_NONE}, {@link #HELM_SPOOL}, {@link #HELM_RISE} or {@link #HELM_PILOTED}
 * @param boost    the boost meter, 0 to 1
 * @param boosting the boost is on
 * @param profile  the ship's flight numbers
 */
public record FlightStatusPayload(int helm, float boost, boolean boosting, FlightModel.Profile profile) implements CustomPacketPayload {
	/** Not at the helm (released, or never taken). */
	public static final int HELM_NONE = 0;
	/** Taking the helm: the Spool. */
	public static final int HELM_SPOOL = 1;
	/** Taking the helm: the rise under server control. */
	public static final int HELM_RISE = 2;
	/** Flying by hand. */
	public static final int HELM_PILOTED = 3;

	public static final Type<FlightStatusPayload> TYPE = new Type<>(OldRepublicMod.id("flight_status"));
	public static final StreamCodec<RegistryFriendlyByteBuf, FlightStatusPayload> CODEC = StreamCodec.of(FlightStatusPayload::write, FlightStatusPayload::read);

	private static void write(RegistryFriendlyByteBuf buf, FlightStatusPayload p) {
		buf.writeVarInt(p.helm);
		buf.writeFloat(p.boost);
		buf.writeBoolean(p.boosting);
		FlightModel.Profile f = p.profile;
		buf.writeFloat(f.top());
		buf.writeFloat(f.thrust());
		buf.writeFloat(f.brake());
		buf.writeFloat(f.climb());
		buf.writeFloat(f.turn());
		buf.writeFloat(f.decay());
		buf.writeFloat(f.reverse());
		buf.writeFloat(f.strafe());
		buf.writeFloat(f.pitchLimit());
		buf.writeFloat(f.boost());
		buf.writeFloat(f.cap());
	}

	private static FlightStatusPayload read(RegistryFriendlyByteBuf buf) {
		int helm = buf.readVarInt();
		float boost = buf.readFloat();
		boolean boosting = buf.readBoolean();
		FlightModel.Profile profile = new FlightModel.Profile(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
				buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat());
		return new FlightStatusPayload(helm, boost, boosting, profile);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
