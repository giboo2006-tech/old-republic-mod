package com.oldrepublic.client.travel;

import java.util.List;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.fx.ClientEffects;
import com.oldrepublic.client.hud.Banners;
import com.oldrepublic.client.ui.UiColors;
import com.oldrepublic.registry.M4Items;
import com.oldrepublic.travel.NavData;
import com.oldrepublic.travel.ShipEntities;
import com.oldrepublic.travel.ShipMenu;
import com.oldrepublic.travel.ShipRules;
import com.oldrepublic.travel.TravelEffects;
import com.oldrepublic.travel.network.FlightPhasePayload;
import com.oldrepublic.travel.network.ShipStatusPayload;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Milestone 4 client initialisation for ships and travel (spec MILESTONE_4.md 4, 11, 13): the
 * courier shuttle's model and renderer, the ship screen, the {@code ship_status} and
 * {@code flight_phase} receivers, the ship's effects and loops, the flight HUD, the hyperspace
 * overlay (also over the level loading screen), the flight camera, the toasts, the arrival banner
 * and the nav computer's tooltip. Owned by W3 ships and travel; the lead calls it from
 * {@code OldRepublicModClient}, so the worker never edits the client entry point.
 */
public final class TravelClient {
	public static void init() {
		ModelLayerRegistry.registerModelLayer(CourierShuttleModel.LAYER, CourierShuttleModel::createBodyLayer);
		EntityRendererRegistry.register(ShipEntities.COURIER_SHUTTLE, CourierShuttleRenderer::new);
		MenuScreens.register(ShipMenu.TYPE, ShipScreen::new);

		ClientPlayNetworking.registerGlobalReceiver(ShipStatusPayload.TYPE, (payload, context) -> onStatus(context.client(), payload));
		ClientPlayNetworking.registerGlobalReceiver(FlightPhasePayload.TYPE, (payload, context) -> onFlight(payload));

		TravelFx.init();
		ClientEffects.register(TravelEffects.HYPERSPACE, HyperspaceOverlay::onEffect);
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, OldRepublicMod.id("flight_hud"), FlightHud::extract);
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, OldRepublicMod.id("ship_toasts"), ShipToasts::extract);
		HudElementRegistry.addLast(OldRepublicMod.id("hyperspace_overlay"), HyperspaceOverlay::extract);
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof LevelLoadingScreen) {
				ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, partialTick) -> HyperspaceOverlay.extractOnLoadingScreen(graphics, partialTick));
			}
		});

		ClientTickEvents.END_CLIENT_TICK.register(TravelClient::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
		ItemTooltipCallback.EVENT.register(TravelClient::tooltip);
	}

	/** {@code ship_status}: a notice goes to the toasts; any other view to the open ship screen. */
	private static void onStatus(Minecraft client, ShipStatusPayload payload) {
		if (payload.notice() != ShipStatusPayload.NOTICE_NONE) {
			ShipToasts.onNotice(payload);
			return;
		}

		ShipClientState.setStatus(payload);

		if (client.gui.screen() instanceof ShipScreen screen) {
			screen.onStatus(payload);
		}
	}

	/** {@code flight_phase}: the HUD's clock, the overlay, the camera, and the banner at the Settle. */
	private static void onFlight(FlightPhasePayload payload) {
		ShipClientState.setFlight(payload);
		HyperspaceOverlay.onPhase(payload);
		ShipCameraClient.onPhase(payload);

		if (payload.phase() == ShipRules.PHASE_SETTLE) {
			Banners.show(Component.translatable("screen.oldrepublic.ship.flight.arrived"), FlightHud.destinationName(payload.destination()), UiColors.HOLO);
		}
	}

	private static void tick(Minecraft mc) {
		ShipClientState.tick(mc);
		TravelFx.tick(mc);
		HyperspaceOverlay.tick(mc);
		ShipCameraClient.tick(mc);
	}

	private static void clear() {
		ShipCameraClient.clear();
		HyperspaceOverlay.clear();
		ShipToasts.clear();
		TravelFx.clear();
		ShipClientState.clear();
	}

	/** A nav computer's coordinates (4.2): the worlds it carries, or that it is blank. */
	private static void tooltip(ItemStack stack, Item.TooltipContext context, TooltipFlag flag, List<Component> lines) {
		if (!stack.is(M4Items.NAV_COMPUTER)) {
			return;
		}

		List<String> planets = NavData.planets(stack);

		if (planets.isEmpty()) {
			lines.add(Math.min(1, lines.size()), Component.translatable("ship.oldrepublic.nav.tooltip.blank").withStyle(ChatFormatting.GRAY));
			return;
		}

		MutableComponent worlds = Component.empty();

		for (int i = 0; i < planets.size(); i++) {
			if (i > 0) {
				worlds.append(", ");
			}

			worlds.append(Component.translatable("ship.oldrepublic.planet." + planets.get(i)));
		}

		lines.add(Math.min(1, lines.size()), Component.translatable("ship.oldrepublic.nav.tooltip.loaded", worlds).withStyle(ChatFormatting.AQUA));
	}

	private TravelClient() {
	}
}
