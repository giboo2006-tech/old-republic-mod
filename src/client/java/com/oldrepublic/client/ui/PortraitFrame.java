package com.oldrepublic.client.ui;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * A square portrait in a chamfered brass frame over a dark glass well, with a thin accent rule under
 * it (the speaker's side colour, for example). Used by the dialogue screen and the Standing tab's
 * mentor card. Portraits are GUI sprites ({@code textures/gui/sprites/...}).
 */
public final class PortraitFrame {
	private static final int BORDER = 2;
	private static final int CUT = 3;

	/**
	 * Draws a portrait frame whose outer size is {@code size}×{@code size}.
	 *
	 * @param portrait GUI sprite of the portrait, or null for an empty frame (a dim silhouette well)
	 * @param accent   ARGB colour of the rule under the portrait, or 0 for none
	 */
	public static void draw(GuiGraphicsExtractor g, int x, int y, int size, @Nullable Identifier portrait, int accent, float time, float alpha) {
		int inner = size - BORDER * 2;
		g.fill(x - 1, y - 1, x + size + 1, y + size + 1, Ui.a(0xB0000000, alpha));
		g.fill(x, y, x + size, y + size, Ui.a(UiColors.BRASS_DARK, alpha));
		g.fill(x + 1, y + 1, x + size - 1, y + size - 1, Ui.a(UiColors.BRASS, alpha));
		// A highlight on the top and left edges sells the bevel.
		g.fill(x + 1, y + 1, x + size - 1, y + 2, Ui.a(UiColors.BRASS_LIGHT, alpha));
		g.fill(x + 1, y + 1, x + 2, y + size - 1, Ui.a(UiColors.BRASS_LIGHT, alpha * 0.7f));
		g.fill(x + BORDER, y + BORDER, x + BORDER + inner, y + BORDER + inner, Ui.a(UiColors.WELL, alpha));

		if (portrait != null) {
			Ui.sprite(g, portrait, x + BORDER, y + BORDER, inner, inner, alpha);
		}

		Ui.scanlines(g, x + BORDER, y + BORDER, inner, inner, Ui.a(UiColors.HOLO, 0.04f * alpha * UiAnim.flicker(time)), time);
		Ui.ring(g, x, y, size, size, CUT, 1f, UiColors.BRASS_LIGHT, 0, alpha);
		cutCorners(g, x, y, size, alpha);

		if (accent != 0) {
			g.fill(x + BORDER, y + size + 2, x + size - BORDER, y + size + 3, Ui.a(accent, alpha));
		}
	}

	/** Clears the outer corner pixels so the frame reads as chamfered. */
	private static void cutCorners(GuiGraphicsExtractor g, int x, int y, int size, float alpha) {
		int dark = Ui.a(UiColors.OUTLINE, alpha);

		for (int i = 0; i < CUT; i++) {
			int len = CUT - i;
			g.fill(x, y + i, x + len, y + i + 1, dark);
			g.fill(x + size - len, y + i, x + size, y + i + 1, dark);
			g.fill(x, y + size - 1 - i, x + len, y + size - i, dark);
			g.fill(x + size - len, y + size - 1 - i, x + size, y + size - i, dark);
		}
	}

	private PortraitFrame() {
	}
}
