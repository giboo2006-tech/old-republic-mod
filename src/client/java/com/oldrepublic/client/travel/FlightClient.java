package com.oldrepublic.client.travel;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.config.ClientConfig;
import com.oldrepublic.client.force.ForceKeys;
import com.oldrepublic.travel.FlightModel;
import com.oldrepublic.travel.ShipBody;
import com.oldrepublic.travel.ShipEntities;
import com.oldrepublic.travel.network.FlightBoostPayload;
import com.oldrepublic.travel.network.FlightMotionPayload;
import com.oldrepublic.travel.network.FlightStatusPayload;
import com.oldrepublic.travel.network.ShipActionPayload;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;

/**
 * Milestone 5 client initialisation for the helm key, the flight camera and HUD, the ship's engine
 * sounds and the interceptor's renderer (4.11). Owned by W1 flight and ships; the lead calls it
 * from {@code OldRepublicModClient}, so the worker never edits the client entry point.
 *
 * <p>The helm (spec MILESTONE_5.md 4.2, 4.3, 4.5, 4.6): the Helm key asks the server for it
 * ({@code ship_action TAKE_HELM}: on the ground, while the autopilot comes down, or over an orbit
 * where the ship is held) and, in the air at the helm, to land ({@code LAND}); the server's
 * {@code flight_status} says how far the take-off has come and carries the numbers the ship flies
 * by. While the player flies by hand, their controls (their {@code Input} and look) reach the ship
 * at the end of every {@code rideTick} ({@link #steer}), Sprint asks the server for the boost, and
 * the inventory key opens the ship screen ({@code OPEN_SCREEN}). When the server moved the ship
 * itself, its {@code flight_motion} says how the ship flies on.
 */
public final class FlightClient {
	/** The Helm key (13.1): in the pilot seat, take the helm; at the helm in the air, land. */
	public static final KeyMapping HELM = new KeyMapping("key.oldrepublic.helm", InputConstants.Type.KEYSYM, InputConstants.KEY_Y, ForceKeys.CATEGORY);
	/** A {@code flight_motion} that came before the ship it is for (a crossing makes a new body) waits this long for it (ticks). */
	private static final int MOTION_WAIT = 40;

	/** The server's last word on the local player's helm, or null when they are not at it. */
	private static @Nullable FlightStatusPayload status;
	/** Sprint as last told to the server. */
	private static boolean boostAsked;
	/** The server's last {@code flight_motion} not yet given to the ship, and how long it has waited. */
	private static @Nullable FlightMotionPayload motion;
	private static int motionWaited;

	public static void init() {
		KeyMappingHelper.registerKeyMapping(HELM);
		ModelLayerRegistry.registerModelLayer(InterceptorModel.LAYER, InterceptorModel::createBodyLayer);
		EntityRendererRegistry.register(ShipEntities.INTERCEPTOR, InterceptorRenderer::new);
		ClientPlayNetworking.registerGlobalReceiver(FlightStatusPayload.TYPE, (payload, context) -> onStatus(payload));
		ClientPlayNetworking.registerGlobalReceiver(FlightMotionPayload.TYPE, (payload, context) -> onMotion(payload));

		FlightFx.init();
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, OldRepublicMod.id("helm_hud"), HelmHud::extract);
		HudElementRegistry.addLast(OldRepublicMod.id("atmosphere_overlay"), AtmosphereOverlay::extract);
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof LevelLoadingScreen) {
				ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, partialTick) -> AtmosphereOverlay.extractOnLoadingScreen(graphics, partialTick));
			}
		});

		ClientTickEvents.START_CLIENT_TICK.register(FlightClient::beforeKeys);
		ClientTickEvents.END_CLIENT_TICK.register(FlightClient::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
	}

	/** Whether the local player is at the helm: taking it, flying by hand, landing, held or tumbling. */
	static boolean atHelm() {
		return status != null;
	}

	/** The server's last word on the local player's helm, or null when they are not at it (public for the client test steps). */
	public static @Nullable FlightStatusPayload status() {
		return status;
	}

	/** Whether the atmosphere overlay is up, from the line until it has cleared (the client test steps wait on it). */
	public static boolean atmosphereShowing() {
		return AtmosphereOverlay.showing();
	}

	/**
	 * The inventory key at the helm opens the ship screen instead of the inventory (4.3). Read at the
	 * start of the client tick: vanilla handles its keys before the player's {@code rideTick}, so the
	 * presses are taken here, before it sees them.
	 */
	private static void beforeKeys(Minecraft mc) {
		if (mc.gui.screen() != null || !Piloting.isPiloting()) {
			return;
		}

		boolean pressed = false;

		while (mc.options.keyInventory.consumeClick()) {
			pressed = true;
		}

		if (pressed) {
			ClientPlayNetworking.send(new ShipActionPayload(ShipActionPayload.OPEN_SCREEN, 0, ""));
		}
	}

	private static void tick(Minecraft mc) {
		while (HELM.consumeClick()) {
			helmKey(mc);
		}

		applyMotion(mc);
		FlightFx.tick(mc);
		AtmosphereOverlay.tick(mc);
	}

	/**
	 * The Helm key, riding a ship: at the helm in the air it asks to land (4.6); not at the helm (or
	 * held over an orbit) it asks for the helm, which also takes it from the autopilot's descent
	 * (4.5). The server checks the rest; while it flies the ship itself the key does nothing.
	 */
	private static void helmKey(Minecraft mc) {
		if (mc.player == null || !(mc.player.getVehicle() instanceof ShipBody)) {
			return;
		}

		int helm = status == null ? FlightStatusPayload.HELM_NONE : status.helm();
		int button = switch (helm) {
			case FlightStatusPayload.HELM_PILOTED -> ShipActionPayload.LAND;
			case FlightStatusPayload.HELM_NONE, FlightStatusPayload.HELM_HOVER -> ShipActionPayload.TAKE_HELM;
			default -> -1;
		};

		if (button >= 0) {
			ClientPlayNetworking.send(new ShipActionPayload(button, 0, ""));
		}
	}

	/** {@code flight_status}: kept for the controls and the HUD; a new stage moves the camera (out for the rise, back when released). */
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

	/** {@code flight_motion} (4.7): given to the ship as soon as the local player pilots it. */
	private static void onMotion(FlightMotionPayload payload) {
		motion = payload;
		motionWaited = 0;
		applyMotion(Minecraft.getInstance());
	}

	/**
	 * The server moved the piloted ship itself (into an orbit or a sky, back from the orbit's edge):
	 * the ship flies on from its new place at the server's velocity and heading, and at the edge the
	 * pilot's look turns with it. After a crossing the new body arrives a little after the payload,
	 * so the payload waits for it, at most {@link #MOTION_WAIT} ticks.
	 */
	private static void applyMotion(Minecraft mc) {
		FlightMotionPayload pending = motion;

		if (pending == null) {
			return;
		}

		LocalPlayer player = mc.player;

		if (player != null && Piloting.isPiloting() && player.getVehicle() instanceof ShipBody ship) {
			ship.impose(new Vec3(pending.vx(), pending.vy(), pending.vz()), pending.yaw());

			if (pending.turnLook()) {
				player.absSnapRotationTo(pending.yaw(), player.getXRot());
			}

			motion = null;
		} else if (++motionWaited > MOTION_WAIT) {
			motion = null;
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

	/** Disconnect: off the helm, and every flight effect and overlay stops. */
	private static void clear() {
		status = null;
		boostAsked = false;
		motion = null;
		FlightFx.clear();
		AtmosphereOverlay.clear();
	}

	private FlightClient() {
	}
}
