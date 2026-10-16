package com.oldrepublic.client.combat;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.force.ForceKeys;
import com.oldrepublic.client.ui.Ui;
import com.oldrepublic.client.ui.UiAnim;
import com.oldrepublic.client.ui.UiColors;
import com.oldrepublic.combat.Holds;
import com.oldrepublic.combat.Holds.HeldState;
import com.oldrepublic.force.ForcePush;
import com.oldrepublic.skill.Skills;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The client half of {@link Holds} (spec 12.3). While the local player's {@code oldrepublic:held}
 * attachment is set, they float at the anchor with no gravity drift; movement input is zeroed;
 * jump, sneak, sprint, attack and use are swallowed, and so is every Force power key except the
 * slot holding Force Push when the hold allows it. A short line under the crosshair reads "Held:
 * Push to break free" (or "Held"). The server stays authoritative: it steers the player as well and
 * refuses every other power.
 */
public final class ClientHolds {
	/** Fraction of the way to the anchor the player moves each tick: an eased rise, then stillness. */
	private static final double STEER = 0.5;
	/** Gap between the crosshair centre and the hint line, clear of the meditation ring. */
	private static final int HINT_OFFSET = 20;
	private static final int FADE_IN_TICKS = 6;

	private static boolean wasHeld;

	static void init() {
		// At the start of the tick, before vanilla reads the keys and before the player moves.
		ClientTickEvents.START_CLIENT_TICK.register(ClientHolds::tick);
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, OldRepublicMod.id("held_hint"), ClientHolds::extract);
	}

	private static void tick(Minecraft mc) {
		LocalPlayer player = mc.player;

		if (player == null) {
			wasHeld = false;
			return;
		}

		HeldState held = player.getAttached(Holds.HELD);

		if (held == null) {
			if (wasHeld && mc.gui.screen() == null) {
				// Pick up the keys still physically held, as vanilla does when a screen closes.
				KeyMapping.setAll();
			}

			wasHeld = false;
			return;
		}

		wasHeld = true;
		Options options = mc.options;
		swallow(options.keyUp, options.keyDown, options.keyLeft, options.keyRight, options.keyJump, options.keyShift,
				options.keySprint, options.keyAttack, options.keyUse);

		for (int slot = 0; slot < ForceKeys.SLOTS.size(); slot++) {
			if (!held.pushAllowed() || !ForcePush.ABILITY_ID.equals(Skills.slot(player, slot))) {
				swallow(ForceKeys.SLOTS.get(slot));
			}
		}

		player.setSprinting(false);
		player.setDeltaMovement(held.anchor().subtract(player.position()).scale(STEER));
	}

	/** Releases the keys and drops the presses queued since the last tick. */
	private static void swallow(KeyMapping... keys) {
		for (KeyMapping key : keys) {
			key.setDown(false);

			while (key.consumeClick()) {
				// Discard the press.
			}
		}
	}

	private static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;

		if (player == null || mc.gui.hud.isHidden()) {
			return;
		}

		HeldState held = player.getAttached(Holds.HELD);

		if (held == null) {
			return;
		}

		float partialTick = deltaTracker.getGameTimeDeltaPartialTick(true);
		float age = player.level().getGameTime() - held.since() + partialTick;
		float alpha = Mth.clamp(age / FADE_IN_TICKS, 0f, 1f) * (0.75f + 0.25f * UiAnim.pulse(UiAnim.time(partialTick), 40f));
		Component hint = Component.translatable(held.pushAllowed() ? "hud.oldrepublic.held.push" : "hud.oldrepublic.held");
		Font font = mc.font;
		int width = font.width(hint);
		int cx = graphics.guiWidth() / 2;
		int y = graphics.guiHeight() / 2 + HINT_OFFSET;

		Ui.fadeLine(graphics, cx, y - 3, width / 2 + 10, UiColors.AMBER_DIM, alpha);
		Ui.text(graphics, font, hint, cx - width / 2, y, UiColors.AMBER_BRIGHT, alpha);
		Ui.fadeLine(graphics, cx, y + font.lineHeight + 1, width / 2 + 10, UiColors.AMBER_DIM, alpha);
	}

	private ClientHolds() {
	}
}
