package com.oldrepublic.client.saber;

import java.util.ArrayList;
import java.util.List;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.saber.workbench.LightsaberWorkbenchScreen;
import com.oldrepublic.saber.KyberCrystalItem;
import com.oldrepublic.saber.SaberAssembly;
import com.oldrepublic.saber.workbench.LightsaberWorkbenchMenu;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

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
		LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(ThrownSaberFx::render);
		ClientTickEvents.END_CLIENT_TICK.register(SaberClient::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
		ItemTooltipCallback.EVENT.register(SaberClient::tooltip);
	}

	/**
	 * The singing crystal is a plain item of the lead's registry, so it gets the kyber crystal's
	 * lines (owner and hue, or the unattuned hint, and that it sings) here, right under its name.
	 */
	private static void tooltip(ItemStack stack, Item.TooltipContext context, TooltipFlag flag, List<Component> lines) {
		if (!SaberAssembly.isSingingCrystal(stack) || stack.getItem() instanceof KyberCrystalItem) {
			return;
		}

		List<Component> crystal = new ArrayList<>();
		KyberCrystalItem.crystalLines(stack, crystal::add);
		lines.addAll(Math.min(1, lines.size()), crystal);
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
		ThrownSaberFx.tick(mc);
		GuardHud.tick(mc);
	}

	private static void reset() {
		SaberAudio.clear();
		BladeTrails.clear();
		SaberAnimations.clear();
		ThrownSaberFx.clear();
		SaberFx.clear();
		SaberDualClient.clear();
	}

	private SaberClient() {
	}
}
