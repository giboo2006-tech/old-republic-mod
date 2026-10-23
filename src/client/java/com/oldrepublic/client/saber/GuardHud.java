package com.oldrepublic.client.saber;

import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.client.ui.Ui;
import com.oldrepublic.client.ui.UiColors;
import com.oldrepublic.saber.SaberBalance;
import com.oldrepublic.saber.SaberGuard;
import com.oldrepublic.saber.SaberGuard.GuardSnapshot;
import com.oldrepublic.saber.SaberPassives;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Guard stamina: a small framed, segmented bar just below the crosshair (in third person beside the
 * head, {@link Ui#crosshairBar}), shown while guarding or while stamina is recovering, and faded out
 * one second after it is full. Holo blue, amber below
 * 25%, red with hazard stripes while the guard is broken. For the first ticks of a guard (the parry
 * window) the bar is bracketed by bright chevrons that fade as the window closes, so perfect-parry
 * timing can be learned at a glance; with flashing effects off they glow softly instead.
 */
final class GuardHud {
	private static final int WIDTH = 44;
	private static final int HEIGHT = 5;
	private static final int OFFSET_Y = 16;
	private static final int FADE_TICKS = 20;
	/**
	 * Default balance for the client-side parry window estimate (the server's tuned numbers are not
	 * synced; the window only drives this cue, never the parry itself).
	 */
	private static final SaberBalance DEFAULT_BALANCE = new SaberBalance();

	private static int visibleTicks;
	private static int fullTicks;

	/** Advances the fade once per client tick. */
	static void tick(Minecraft mc) {
		GuardSnapshot guard = mc.player == null ? null : mc.player.getAttached(SaberGuard.GUARD);
		boolean active = guard != null && (guard.broken() || guard.stamina() < guard.max() || !SaberGuard.guardingSaber(mc.player).isEmpty());

		if (active) {
			visibleTicks = FADE_TICKS;
			fullTicks = 0;
		} else if (visibleTicks > 0 && ++fullTicks > FADE_TICKS) {
			visibleTicks--;
		}
	}

	static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();

		if (mc.player == null || mc.gui.hud.isHidden() || mc.player.isSpectator() || visibleTicks <= 0) {
			return;
		}

		GuardSnapshot guard = mc.player.getAttached(SaberGuard.GUARD);

		if (guard == null || guard.max() <= 0) {
			return;
		}

		float alpha = Math.min(1f, visibleTicks / (float) FADE_TICKS);
		float fraction = Math.max(0f, Math.min(1f, guard.stamina() / guard.max()));
		Ui.Spot spot = Ui.crosshairBar(graphics, WIDTH, HEIGHT, OFFSET_Y);
		int x = spot.x();
		int y = spot.y();
		int top;
		int bottom;

		if (guard.broken()) {
			top = 0xFFFF7A66;
			bottom = 0xFF8A2018;
		} else if (fraction < 0.25f) {
			top = UiColors.AMBER_BRIGHT;
			bottom = UiColors.AMBER_DIM;
		} else {
			top = UiColors.HOLO_BRIGHT;
			bottom = 0xFF4E8FBF;
		}

		Ui.bar(graphics, x, y, WIDTH, HEIGHT, fraction, top, bottom, 4, alpha);

		if (guard.broken()) {
			// Hazard stripes across the whole bar while the guard is broken.
			for (int dx = 1; dx < WIDTH - 1; dx++) {
				for (int row = 1; row < HEIGHT - 1; row++) {
					if ((dx + row) % 4 == 0) {
						graphics.fill(x + dx, y + row, x + dx + 1, y + row + 1, Ui.a(0x80000000, alpha));
					}
				}
			}
		}

		// Brass end caps tie it to the rest of the kit.
		graphics.fill(x - 2, y + 1, x - 1, y + HEIGHT - 1, Ui.a(UiColors.BRASS_LIGHT, alpha));
		graphics.fill(x + WIDTH + 1, y + 1, x + WIDTH + 2, y + HEIGHT - 1, Ui.a(UiColors.BRASS_LIGHT, alpha));

		float window = parryWindow(mc.player, deltaTracker.getGameTimeDeltaPartialTick(true));

		if (window > 0f && !guard.broken()) {
			float peak = Vfx.flashing() ? 1f : 0.55f;
			int c = Ui.a(0xFFFFFFFF, alpha * window * peak);
			int glow = Ui.a(UiColors.HOLO, alpha * window * peak * 0.5f);
			graphics.fill(x - 1, y - 2, x + WIDTH + 1, y - 1, glow);
			graphics.fill(x - 1, y + HEIGHT + 1, x + WIDTH + 1, y + HEIGHT + 2, glow);
			chevron(graphics, x - 6, y + HEIGHT / 2, -1, c);
			chevron(graphics, x + WIDTH + 5, y + HEIGHT / 2, 1, c);
		}
	}

	/** A 3-pixel-tall chevron pointing toward the bar ({@code dir} -1 points right, 1 points left). */
	private static void chevron(GuiGraphicsExtractor g, int x, int cy, int dir, int color) {
		g.fill(x, cy - 2, x + 1, cy - 1, color);
		g.fill(x - dir, cy - 1, x - dir + 1, cy, color);
		g.fill(x - 2 * dir, cy, x - 2 * dir + 1, cy + 1, color);
		g.fill(x - dir, cy + 1, x - dir + 1, cy + 2, color);
		g.fill(x, cy + 2, x + 1, cy + 3, color);
	}

	/** 1 at the start of a guard falling to 0 as the (estimated) parry window closes; 0 otherwise. */
	private static float parryWindow(LocalPlayer player, float partial) {
		ItemStack saber = SaberGuard.guardingSaber(player);

		if (saber.isEmpty() || !player.isUsingItem()) {
			return 0f;
		}

		// The form and steady_guard are known here (the skill data is synced to its owner).
		float ticks = SaberPassives.stats(player, saber, DEFAULT_BALANCE, 0f).parryTicks();
		float used = player.getTicksUsingItem() + partial;
		return ticks <= 0f ? 0f : Math.max(0f, 1f - used / ticks);
	}

	private GuardHud() {
	}
}
