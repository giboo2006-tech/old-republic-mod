package com.oldrepublic.client.planet;

import com.oldrepublic.client.config.ClientConfig;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/**
 * The dimmed storm flash (spec MILESTONE_5.md 15, 14.4): with {@code ClientConfig.stormFlashes} or
 * {@code flashingEffects} off, {@code mixin.planet.StormFlashMixin} drops lightning's sky flashes and
 * this draws a soft, steady brightening of the whole view instead: it rises over
 * {@link #RISE_TICKS}, holds while a bolt stays lit (a burst of bolts reads as one long glow, never as
 * a strobe), and falls away over {@link #FALL_TICKS}. It covers Tyrvann's storm, Ulvaine's storm phase
 * and the storm-callers alike, since all of them flash through vanilla lightning.
 */
public final class StormFlash {
	private static final int RISE_TICKS = 6;
	private static final int FALL_TICKS = 24;
	/** Ticks the glow holds after the last dropped flash. */
	private static final int HOLD_TICKS = 3;
	private static final float MAX_ALPHA = 0.12f;
	private static final int COLOR = 0xE2E8F4;

	private static float glow;
	private static float oGlow;
	private static int hold;

	/** Whether lightning's flashes are to be dimmed to a steady brightening. */
	public static boolean dimmed() {
		ClientConfig config = ClientConfig.get();
		return !config.stormFlashes || !config.flashingEffects;
	}

	/** A dropped flash: the brightening rises, or holds if it is up. */
	public static void flash() {
		hold = HOLD_TICKS;
	}

	static void tick(Minecraft client) {
		oGlow = glow;

		if (client.level == null) {
			glow = 0f;
			oGlow = 0f;
			hold = 0;
			return;
		}

		if (hold > 0) {
			hold--;
			glow = Math.min(1f, glow + 1f / RISE_TICKS);
		} else {
			glow = Math.max(0f, glow - 1f / FALL_TICKS);
		}
	}

	/** The HUD layer (after the misc overlays). */
	static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		float level = Mth.lerp(deltaTracker.getGameTimeDeltaPartialTick(true), oGlow, glow);

		if (level <= 0.001f || Minecraft.getInstance().level == null) {
			return;
		}

		// Eased, so the rise and fall have soft ends.
		float eased = level * level * (3f - 2f * level);
		int alpha = Math.round(MAX_ALPHA * eased * 255f);
		graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), alpha << 24 | COLOR);
	}

	private StormFlash() {
	}
}
