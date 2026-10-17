package com.oldrepublic.client.hud;

import com.oldrepublic.client.quest.MeditationFx;
import com.oldrepublic.client.ui.Ui;
import com.oldrepublic.client.ui.UiColors;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Thin ring of dots around the crosshair that fills over the length of a meditation, with four
 * small holo ticks at the quarter marks and a brighter leading dot. Shown even when the Force panel
 * is hidden, because it is the only feedback for the meditation itself.
 */
final class FocusRingHud {
	private static final int SEGMENTS = 40;
	private static final float RADIUS = 12f;

	static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();

		if (mc.gui.hud.isHidden()) {
			return;
		}

		float progress = MeditationFx.ownProgress(deltaTracker.getGameTimeDeltaPartialTick(true));

		if (progress < 0f) {
			return;
		}

		int cx = graphics.guiWidth() / 2;
		int cy = graphics.guiHeight() / 2;
		float filled = progress * SEGMENTS;
		int lead = Math.min(SEGMENTS - 1, (int) filled);

		for (int i = 0; i < SEGMENTS; i++) {
			double angle = -Math.PI / 2 + Math.PI * 2 * i / SEGMENTS;
			int x = cx + (int) Math.round(Math.cos(angle) * RADIUS);
			int y = cy + (int) Math.round(Math.sin(angle) * RADIUS);
			float part = Math.max(0f, Math.min(1f, filled - i));
			int alpha = (int) (0x38 + (0xD8 - 0x38) * part);
			int rgb = i == lead && progress < 1f ? 0xFFFFFF : 0xDDEEFF;
			graphics.fill(x, y, x + 1, y + 1, alpha << 24 | rgb);
		}

		// Quarter ticks just outside the ring, lit once the ring passes them.
		for (int q = 0; q < 4; q++) {
			double angle = -Math.PI / 2 + Math.PI / 2 * q;
			int x = cx + (int) Math.round(Math.cos(angle) * (RADIUS + 3));
			int y = cy + (int) Math.round(Math.sin(angle) * (RADIUS + 3));
			boolean lit = progress >= q / 4f;
			graphics.fill(x, y, x + 1, y + 1, Ui.a(lit ? UiColors.HOLO : UiColors.HOLO_DIM, lit ? 0.85f : 0.4f));
		}
	}

	private FocusRingHud() {
	}
}
