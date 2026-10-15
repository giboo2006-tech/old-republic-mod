package com.oldrepublic.client.hud;

import com.oldrepublic.client.quest.MeditationFx;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Thin ring of dots around the crosshair that fills over the length of a meditation. Shown even when
 * the Force panel is hidden, because it is the only feedback for the meditation itself.
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

		for (int i = 0; i < SEGMENTS; i++) {
			double angle = -Math.PI / 2 + Math.PI * 2 * i / SEGMENTS;
			int x = cx + (int) Math.round(Math.cos(angle) * RADIUS);
			int y = cy + (int) Math.round(Math.sin(angle) * RADIUS);
			float part = Math.max(0f, Math.min(1f, filled - i));
			int alpha = (int) (0x38 + (0xD8 - 0x38) * part);
			graphics.fill(x, y, x + 1, y + 1, alpha << 24 | 0xDDEEFF);
		}
	}

	private FocusRingHud() {
	}
}
