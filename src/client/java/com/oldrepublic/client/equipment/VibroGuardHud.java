package com.oldrepublic.client.equipment;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.ui.Ui;
import com.oldrepublic.client.ui.UiColors;
import com.oldrepublic.equipment.melee.MeleeCombat;
import com.oldrepublic.equipment.melee.MeleeCombat.GuardView;
import com.oldrepublic.equipment.melee.VibroWeaponItem;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;

/**
 * The vibroweapon and electrostaff guard (spec MILESTONE_4.md 5.2): the same framed, segmented bar
 * just below the crosshair as the saber guard, so the two read as one idea, but in steel rather than
 * holo blue (this guard never parries, so it has no parry-window chevrons). Amber below 25%, red with
 * hazard stripes while broken. Shown while guarding or recovering and faded out a second after it is
 * full. A saber and a vibroweapon are never in the main hand together, so the two bars never share
 * the spot for long.
 */
final class VibroGuardHud {
	private static final int WIDTH = 44;
	private static final int HEIGHT = 5;
	private static final int OFFSET_Y = 16;
	private static final int FADE_TICKS = 20;
	private static final int STEEL_TOP = 0xFFE8EEF4;
	private static final int STEEL_BOTTOM = 0xFF7C8894;

	private static int visibleTicks;
	private static int fullTicks;

	static void init() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, OldRepublicMod.id("vibro_guard"), VibroGuardHud::extract);
		ClientTickEvents.END_CLIENT_TICK.register(VibroGuardHud::tick);
	}

	private static void tick(Minecraft mc) {
		LocalPlayer player = mc.player;
		GuardView guard = player == null ? null : player.getAttached(MeleeCombat.GUARD);
		boolean guarding = player != null && player.isUsingItem() && player.getUseItem().getItem() instanceof VibroWeaponItem;
		boolean active = guard != null && (guard.broken() || guard.stamina() < guard.max() || guarding);

		if (active) {
			visibleTicks = FADE_TICKS;
			fullTicks = 0;
		} else if (visibleTicks > 0 && ++fullTicks > FADE_TICKS) {
			visibleTicks--;
		}
	}

	private static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();

		if (mc.player == null || mc.gui.hud.isHidden() || mc.player.isSpectator() || visibleTicks <= 0) {
			return;
		}

		GuardView guard = mc.player.getAttached(MeleeCombat.GUARD);

		if (guard == null || guard.max() <= 0f) {
			return;
		}

		float alpha = Math.min(1f, visibleTicks / (float) FADE_TICKS);
		float fraction = Math.max(0f, Math.min(1f, guard.stamina() / guard.max()));
		int x = graphics.guiWidth() / 2 - WIDTH / 2;
		int y = graphics.guiHeight() / 2 + OFFSET_Y;
		int top;
		int bottom;

		if (guard.broken()) {
			top = 0xFFFF7A66;
			bottom = 0xFF8A2018;
		} else if (fraction < 0.25f) {
			top = UiColors.AMBER_BRIGHT;
			bottom = UiColors.AMBER_DIM;
		} else {
			top = STEEL_TOP;
			bottom = STEEL_BOTTOM;
		}

		Ui.bar(graphics, x, y, WIDTH, HEIGHT, fraction, top, bottom, 4, alpha);

		if (guard.broken()) {
			for (int dx = 1; dx < WIDTH - 1; dx++) {
				for (int row = 1; row < HEIGHT - 1; row++) {
					if ((dx + row) % 4 == 0) {
						graphics.fill(x + dx, y + row, x + dx + 1, y + row + 1, Ui.a(0x80000000, alpha));
					}
				}
			}
		}

		// Steel end caps (the saber bar's are brass).
		graphics.fill(x - 2, y + 1, x - 1, y + HEIGHT - 1, Ui.a(UiColors.STEEL_LIGHT, alpha));
		graphics.fill(x + WIDTH + 1, y + 1, x + WIDTH + 2, y + HEIGHT - 1, Ui.a(UiColors.STEEL_LIGHT, alpha));
	}

	private VibroGuardHud() {
	}
}
