package com.oldrepublic.client.hud;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.fx.ClientEffects;
import com.oldrepublic.progression.Progression;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

/**
 * Client initialisation for the HUD feature: the Force panel (top-left, clear of every vanilla
 * element), the meditation focus ring around the crosshair, level-up and resonance flourishes, the
 * journal notice and the {@code /oldrepublicfx} comfort command. Called from OldRepublicModClient.
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

	/** Forgets per-session HUD state (called on disconnect). */
	public static void reset() {
		ForceHud.reset();
		FlourishHud.reset();
	}

	private HudClient() {
	}
}
