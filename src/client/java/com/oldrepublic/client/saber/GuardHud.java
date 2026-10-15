package com.oldrepublic.client.saber;

import com.oldrepublic.saber.SaberGuard;
import com.oldrepublic.saber.SaberGuard.GuardSnapshot;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;

/**
 * Guard stamina: a thin bar just below the crosshair, shown while guarding or while stamina is
 * recovering, and faded out one second after it is full. Pale blue, amber below 25%, red while the
 * guard is broken.
 */
final class GuardHud {
	private static final int WIDTH = 40;
	private static final int HEIGHT = 3;
	private static final int OFFSET_Y = 16;
	private static final int FADE_TICKS = 20;

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
		int color = guard.broken() ? 0xE04848 : fraction < 0.25f ? 0xFFB347 : 0xA8D8FF;
		int x = graphics.guiWidth() / 2 - WIDTH / 2;
		int y = graphics.guiHeight() / 2 + OFFSET_Y;

		graphics.fill(x - 1, y - 1, x + WIDTH + 1, y + HEIGHT + 1, ARGB.multiplyAlpha(0xA0101418, alpha));
		graphics.fill(x, y, x + Math.round(WIDTH * fraction), y + HEIGHT, ARGB.multiplyAlpha(0xFF000000 | color, alpha));
	}

	private GuardHud() {
	}
}
