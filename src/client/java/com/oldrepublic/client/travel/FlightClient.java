package com.oldrepublic.client.travel;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;
import com.oldrepublic.client.config.ClientConfig;
import com.oldrepublic.client.force.ForceKeys;
import com.oldrepublic.travel.FlightModel;
import com.oldrepublic.travel.ShipBody;
import com.oldrepublic.travel.network.FlightBoostPayload;
import com.oldrepublic.travel.network.FlightStatusPayload;
import com.oldrepublic.travel.network.ShipActionPayload;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Input;

/**
 * Milestone 5 client initialisation for the helm key, the flight camera and HUD, the ship's engine
 * sounds and the interceptor's renderer (4.11). Owned by W1 flight and ships; the lead calls it
 * from {@code OldRepublicModClient}, so the worker never edits the client entry point.
 *
 * <p>The helm (spec MILESTONE_5.md 4.2, 4.3): the Helm key asks the server for it
 * ({@code ship_action TAKE_HELM}); the server's {@code flight_status} says how far the take-off has
 * come and carries the numbers the ship flies by. While the player flies by hand, their controls
 * (their {@code Input} and look) reach the ship at the end of every {@code rideTick} ({@link #steer}),
 * and Sprint asks the server for the boost.
 */
public final class FlightClient {
	/** The Helm key (13.1): in the pilot seat on the ground, take the helm. */
	public static final KeyMapping HELM = new KeyMapping("key.oldrepublic.helm", InputConstants.Type.KEYSYM, InputConstants.KEY_Y, ForceKeys.CATEGORY);

	/** The server's last word on the local player's helm, or null when they are not at it. */
	private static @Nullable FlightStatusPayload status;
	/** Sprint as last told to the server. */
	private static boolean boostAsked;

	public static void init() {
		KeyMappingHelper.registerKeyMapping(HELM);
		ClientPlayNetworking.registerGlobalReceiver(FlightStatusPayload.TYPE, (payload, context) -> onStatus(payload));
		ClientTickEvents.END_CLIENT_TICK.register(FlightClient::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
	}

	/** Whether the local player is at the helm: taking it, or flying by hand. */
	static boolean atHelm() {
		return status != null;
	}

	/** The Helm key: asks for the helm when riding a ship and not at it yet; the server checks the rest (4.2). */
	private static void tick(Minecraft mc) {
		while (HELM.consumeClick()) {
			if (mc.player != null && mc.player.getVehicle() instanceof ShipBody && status == null) {
				ClientPlayNetworking.send(new ShipActionPayload(ShipActionPayload.TAKE_HELM, 0, ""));
			}
		}
	}

	/** {@code flight_status}: kept for the controls; a new stage moves the camera (out for the rise, back when released). */
	private static void onStatus(FlightStatusPayload payload) {
		int before = status == null ? FlightStatusPayload.HELM_NONE : status.helm();
		status = payload.helm() == FlightStatusPayload.HELM_NONE ? null : payload;

		if (payload.helm() != before) {
			ShipCameraClient.onHelm(payload.helm());
		}

		if (status == null) {
			boostAsked = false;
		}
	}

	/**
	 * The pilot's controls for their ship's next tick (4.3), read at the end of the local player's
	 * {@code rideTick} (a mixin), after this tick's input: thrust, brake, strafe, climb and descent
	 * from the {@code Input} record, the heading from the look (the pitch inverted when
	 * {@code invertShipPitch} is on), and the boost as the server granted it. A change of Sprint asks
	 * the server for the boost.
	 */
	public static void steer(LocalPlayer player) {
		FlightStatusPayload flying = status;

		if (flying == null || flying.helm() != FlightStatusPayload.HELM_PILOTED || !(player.getVehicle() instanceof ShipBody ship)
				|| !ship.piloted() || ship.getControllingPassenger() != player) {
			return;
		}

		Input keys = player.input.keyPresses;

		if (keys.sprint() != boostAsked) {
			boostAsked = keys.sprint();
			ClientPlayNetworking.send(new FlightBoostPayload(boostAsked));
		}

		float pitch = ClientConfig.get().invertShipPitch ? -player.getXRot() : player.getXRot();
		ship.steer(new FlightModel.Controls(keys.forward(), keys.backward(), keys.left(), keys.right(), keys.jump(), keys.shift(),
				player.getYRot(), pitch, flying.boosting()), flying.profile());
	}

	/** Disconnect: off the helm. */
	private static void clear() {
		status = null;
		boostAsked = false;
	}

	private FlightClient() {
	}
}
