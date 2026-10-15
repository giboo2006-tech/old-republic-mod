package com.oldrepublic.client.hud;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.fx.ClientEffects;
import com.oldrepublic.progression.Progression;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

/**
 * Client initialisation for the HUD feature: the Force cluster (top-left, clear of every vanilla
 * element), the meditation focus ring around the crosshair, level-up and resonance banners, the
 * journal toast and the {@code /oldrepublicfx} comfort command. Everything is drawn with the shared
 * kit in {@code com.oldrepublic.client.ui}. Called from OldRepublicModClient.
 */
public final class HudClient {
	public static void init() {
		// Before chat so chat and the player list draw over the panel.
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, OldRepublicMod.id("force_panel"), ForceHud::extract);
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, OldRepublicMod.id("focus_ring"), FocusRingHud::extract);
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, OldRepublicMod.id("flourish"), FlourishHud::extract);

		ClientEffects.register(Progression.LEVEL_UP, FlourishHud::onLevelUp);
		ClientEffects.register(Progression.RESONANCE_UP, FlourishHud::onResonanceUp);

		ClientCommandsFx.init();
	}

	/**
	 * Flashes the Force Energy bar as "not enough energy". The HUD already does this when the power
	 * key is pressed below the cheapest Push; the foundation's {@code energy_fail} handler may call it
	 * too (repeated calls within a few ticks are merged).
	 */
	public static void onEnergyFail() {
		ForceHud.energyFail();
	}

	/** Forgets per-session HUD state (called on disconnect). */
	public static void reset() {
		ForceHud.reset();
		FlourishHud.reset();
	}

	private HudClient() {
	}
}
