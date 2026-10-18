package com.oldrepublic.client.travel;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.travel.network.FlightPhasePayload;
import com.oldrepublic.travel.network.ShipStatusPayload;

import net.minecraft.client.Minecraft;

/**
 * What the client knows about the player's ship and flight: the last {@code ship_status} (the ship
 * screen's view) and the last {@code flight_phase} with the client tick it arrived on (the flight
 * HUD and the camera). Cleared when the client leaves the world.
 */
public final class ShipClientState {
	private static @Nullable ShipStatusPayload status;
	private static @Nullable FlightPhasePayload flight;
	/** Client ticks since the current phase began. */
	private static int phaseTicks;
	private static int ticks;

	/** The last ship view, or null before the first one. */
	public static @Nullable ShipStatusPayload status() {
		return status;
	}

	static void setStatus(ShipStatusPayload payload) {
		status = payload;
	}

	/** The phase the player's flight is in, or null when they are not flying. */
	public static @Nullable FlightPhasePayload flight() {
		return flight;
	}

	/** Ticks (with the partial tick) since the current phase began. */
	public static float phaseTime(float partialTick) {
		return phaseTicks + partialTick;
	}

	/** 0 to 1 through the current phase. */
	public static float phaseProgress(float partialTick) {
		FlightPhasePayload f = flight;
		return f == null ? 0f : Math.min(1f, phaseTime(partialTick) / Math.max(1, f.length()));
	}

	static void setFlight(FlightPhasePayload payload) {
		flight = payload.phase() == FlightPhasePayload.OVER ? null : payload;
		phaseTicks = 0;
	}

	/** Client ticks since the world was joined (an animation clock). */
	static int ticks() {
		return ticks;
	}

	static void tick(Minecraft mc) {
		if (mc.level == null) {
			clear();
			return;
		}

		ticks++;
		phaseTicks++;
	}

	static void clear() {
		status = null;
		flight = null;
		phaseTicks = 0;
	}

	private ShipClientState() {
	}
}
