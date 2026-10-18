package com.oldrepublic.client.travel;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.config.ClientConfig;
import com.oldrepublic.travel.ShipRules;
import com.oldrepublic.travel.network.FlightPhasePayload;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;

/**
 * The flight camera (spec MILESTONE_4.md 4.6): with {@code ClientConfig.shipCamera} on, a rider in
 * first person is switched to third person behind the ship for the climb and the descent, and put
 * back at the Settle. A camera the player changes themselves mid-flight is theirs: it is never
 * switched again that flight and never restored. The camera is also put back when the flight ends
 * any other way, on disconnect, and when the client loses its level. (The server's
 * {@code CAMERA_DISTANCE} modifier backs the third-person camera off the large ship.)
 */
final class ShipCameraClient {
	/** The camera to put back, or null when this class is not managing the camera. */
	private static @Nullable CameraType restoreTo;
	/** The camera this class set, to notice the player changing it. */
	private static @Nullable CameraType setTo;
	/** The player chose a camera themselves this flight. */
	private static boolean playerChose;

	/** {@code flight_phase}: out at the climb and descent, back at the Settle or the end. */
	static void onPhase(FlightPhasePayload payload) {
		Minecraft mc = Minecraft.getInstance();

		switch (payload.phase()) {
			case ShipRules.PHASE_SPOOL -> playerChose = false;
			case ShipRules.PHASE_CLIMB, ShipRules.PHASE_DESCENT -> switchOut(mc);
			case ShipRules.PHASE_SETTLE, FlightPhasePayload.OVER -> {
				restore(mc);
				playerChose = false;
			}
			default -> {
			}
		}
	}

	private static void switchOut(Minecraft mc) {
		if (!ClientConfig.get().shipCamera || playerChose || setTo != null) {
			return;
		}

		CameraType now = mc.options.getCameraType();

		if (!now.isFirstPerson()) {
			return;
		}

		restoreTo = now;
		setTo = CameraType.THIRD_PERSON_BACK;
		mc.options.setCameraType(setTo);
	}

	/** Notices the player changing the camera, and puts it back once the flight is over. */
	static void tick(Minecraft mc) {
		if (setTo == null) {
			return;
		}

		if (mc.options.getCameraType() != setTo) {
			// The player chose: leave their camera alone for the rest of the flight.
			restoreTo = null;
			setTo = null;
			playerChose = true;
			return;
		}

		if (mc.level == null || mc.player == null || ShipClientState.flight() == null) {
			restore(mc);
		}
	}

	private static void restore(Minecraft mc) {
		if (setTo != null && restoreTo != null && mc.options.getCameraType() == setTo) {
			mc.options.setCameraType(restoreTo);
		}

		restoreTo = null;
		setTo = null;
	}

	/** Disconnect: the camera goes back and the flight is forgotten. */
	static void clear() {
		restore(Minecraft.getInstance());
		playerChose = false;
	}

	private ShipCameraClient() {
	}
}
