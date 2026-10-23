package com.oldrepublic.travel.network;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.travel.FlightModel;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The pilot's flight status ({@code flight_status}, S2C, spec MILESTONE_5.md 4.2, 4.3, 4.6, 4.7,
 * 18.2): sent to the owner when they take the helm, at each step of the take-off and the landing,
 * while they fly (when the boost or the landing check changes, and every few ticks) and once with
 * {@link #HELM_NONE} when the helm is released. It carries the numbers the pilot's client flies the
 * ship by, so a server's balance reaches it, and what the flight HUD shows (4.11).
 *
 * @param helm     {@link #HELM_NONE} to {@link #HELM_HOVER}
 * @param boost    the boost meter, 0 to 1
 * @param boosting the boost is on
 * @param profile  the ship's flight numbers
 * @param landing  the landing check of the spot below ({@code FlightRules.Landing} ordinal)
 * @param cooldown ticks until the next sky or orbit transition may happen (0: none pending)
 * @param edge     blocks to the orbit's edge while within {@code flight.edgeWarning} of it, else -1 (the HUD's warning)
 * @param ceiling  the height where a planet's sky gives onto its orbit ({@code flight.orbitCeiling}) when there is
 *                 an orbit to climb into, else -1 (the HUD's orbit cue)
 */
public record FlightStatusPayload(int helm, float boost, boolean boosting, FlightModel.Profile profile, int landing, int cooldown, int edge,
		int ceiling) implements CustomPacketPayload {
	/** Not at the helm (released, or never taken). */
	public static final int HELM_NONE = 0;
	/** Taking the helm: the Spool. */
	public static final int HELM_SPOOL = 1;
	/** Taking the helm: the rise under server control. */
	public static final int HELM_RISE = 2;
	/** Flying by hand. */
	public static final int HELM_PILOTED = 3;
	/** Landing by hand: the glide onto a bay, the settle down and the Settle, under server control (4.5, 4.6). */
	public static final int HELM_LANDING = 4;
	/** Disabled: the controls are locked while the ship tumbles before the rescue (5.4). */
	public static final int HELM_TUMBLE = 5;
	/** Held in place by the server with nobody flying it ({@code flight.enabled} off, the blockade's arrival, 6.5). */
	public static final int HELM_HOVER = 6;

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
		buf.writeVarInt(p.landing);
		buf.writeVarInt(p.cooldown);
		buf.writeVarInt(p.edge);
		buf.writeVarInt(p.ceiling);
	}

	private static FlightStatusPayload read(RegistryFriendlyByteBuf buf) {
		int helm = buf.readVarInt();
		float boost = buf.readFloat();
		boolean boosting = buf.readBoolean();
		FlightModel.Profile profile = new FlightModel.Profile(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
				buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat());
		return new FlightStatusPayload(helm, boost, boosting, profile, buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
