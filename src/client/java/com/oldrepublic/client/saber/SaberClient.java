package com.oldrepublic.client.saber;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.saber.workbench.LightsaberWorkbenchScreen;
import com.oldrepublic.saber.workbench.LightsaberWorkbenchMenu;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.multiplayer.ClientLevel;

/** Client initialisation for the saber feature. Called from OldRepublicModClient. */
public final class SaberClient {
	private static ClientLevel lastLevel;

	public static void init() {
		SaberModelProperties.register();
		SaberKeys.init();
		SaberParticleProviders.init();
		SaberFx.init();
		SaberActionsClient.init();
		ThrownSaberFx.init();
		MenuScreens.register(LightsaberWorkbenchMenu.TYPE, LightsaberWorkbenchScreen::new);
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, OldRepublicMod.id("guard_stamina"), GuardHud::extract);
		LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(SwingTrailRenderer::render);
		LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(ThrownSaberFx::render);
		ClientTickEvents.END_CLIENT_TICK.register(SaberClient::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
	}

	private static void tick(Minecraft mc) {
		if (mc.level != lastLevel) {
			// Join or dimension change: entity ids and sounds from the old level are stale.
			lastLevel = mc.level;
			reset();
		}

		SaberKeys.tick(mc);
		SaberActionsClient.tick(mc);
		SaberAnimations.tick(mc);
		SaberAudio.tick(mc);
		SwingTrailRenderer.tick(mc);
		ThrownSaberFx.tick(mc);
		GuardHud.tick(mc);
	}

	private static void reset() {
		SaberAudio.clear();
		SwingTrailRenderer.clear();
		SaberAnimations.clear();
		ThrownSaberFx.clear();
		SaberFx.clear();
	}

	private SaberClient() {
	}
}
