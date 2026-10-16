package com.oldrepublic.client.ui;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * Drawing for skill-tree nodes: a 20×20 socket with a 16×16 icon and a rim that shows the node's
 * state, plus the thin links between nodes and the small "or" diamond that exclusive groups fork
 * from. Owned is a lit rim, Available a slow pulse, Locked dim with a lock glyph, Dormant a cracked
 * amber rim. A small brass crest marks a granted node; brass corner brackets mark the selection.
 */
public final class NodeSocket {
	public static final int SIZE = 20;
	private static final int ICON = 16;
	private static final int PULSE_TICKS = 40;

	public enum State {
		OWNED,
		AVAILABLE,
		LOCKED,
		DORMANT
	}

	/**
	 * Draws one node.
	 *
	 * @param icon GUI sprite of the node's icon, or null for an empty socket
	 */
	public static void draw(GuiGraphicsExtractor g, int x, int y, @Nullable Identifier icon, State state, boolean granted, boolean selected,
			boolean hovered, float time, float alpha) {
		Ui.sprite(g, Ui.SOCKET, x, y, SIZE, SIZE, alpha);

		if (icon != null) {
			int tint = switch (state) {
				case OWNED, AVAILABLE -> 0xFFFFFFFF;
				case LOCKED -> 0xFF5A6068;
				case DORMANT -> 0xFFB89A6A;
			};
			Ui.sprite(g, icon, x + (SIZE - ICON) / 2, y + (SIZE - ICON) / 2, ICON, ICON, Ui.a(tint, alpha));
		}

		switch (state) {
			case OWNED -> Ui.ring(g, x, y, SIZE, SIZE, 3, 1f, UiColors.HOLO_BRIGHT, 0, alpha);
			case AVAILABLE -> Ui.ring(g, x, y, SIZE, SIZE, 3, 1f, UiColors.HOLO, 0, alpha * (0.35f + 0.65f * UiAnim.pulse(time, PULSE_TICKS)));
			case LOCKED -> {
				Ui.ring(g, x, y, SIZE, SIZE, 3, 1f, UiColors.STEEL, 0, alpha);
				lockGlyph(g, x + SIZE - 7, y + SIZE - 8, alpha);
			}
			case DORMANT -> {
				Ui.ring(g, x, y, SIZE, SIZE, 3, 1f, UiColors.AMBER_DIM, 0, alpha);
				crack(g, x, y, alpha);
			}
		}

		if (hovered && state != State.LOCKED) {
			g.fill(x + 3, y + 3, x + SIZE - 3, y + SIZE - 3, Ui.a(0xFFFFFFFF, 0.08f * alpha));
		}

		if (granted) {
			crest(g, x + SIZE - 5, y - 1, alpha);
		}

		if (selected) {
			brackets(g, x - 2, y - 2, SIZE + 4, SIZE + 4, Ui.a(UiColors.BRASS_LIGHT, alpha));
		}
	}

	/** A thin link from one node centre to another: horizontal first, then vertical. */
	public static void link(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, boolean lit, float alpha) {
		int color = Ui.a(lit ? UiColors.HOLO : UiColors.STEEL, alpha);
		g.fill(Math.min(x0, x1), y0, Math.max(x0, x1) + 1, y0 + 1, color);
		g.fill(x1, Math.min(y0, y1), x1 + 1, Math.max(y0, y1) + 1, color);
	}

	/** The small diamond an exclusive group forks from, centred on cx, cy. */
	public static void orDiamond(GuiGraphicsExtractor g, int cx, int cy, boolean lit, float alpha) {
		int color = Ui.a(lit ? UiColors.HOLO_BRIGHT : UiColors.STEEL_LIGHT, alpha);

		for (int r = 0; r <= 3; r++) {
			int half = 3 - r;
			g.fill(cx - half, cy - r, cx + half + 1, cy - r + 1, Ui.a(UiColors.OUTLINE, alpha));
			g.fill(cx - half, cy + r, cx + half + 1, cy + r + 1, Ui.a(UiColors.OUTLINE, alpha));
		}

		for (int r = 0; r <= 2; r++) {
			int half = 2 - r;
			g.fill(cx - half, cy - r, cx + half + 1, cy - r + 1, color);
			g.fill(cx - half, cy + r, cx + half + 1, cy + r + 1, color);
		}
	}

	private static void lockGlyph(GuiGraphicsExtractor g, int x, int y, float alpha) {
		int body = Ui.a(UiColors.STEEL_LIGHT, alpha);
		g.fill(x - 1, y - 1, x + 6, y + 8, Ui.a(UiColors.OUTLINE, alpha));
		g.fill(x + 1, y, x + 4, y + 1, body);
		g.fill(x, y + 1, x + 1, y + 3, body);
		g.fill(x + 4, y + 1, x + 5, y + 3, body);
		g.fill(x, y + 3, x + 5, y + 7, body);
		g.fill(x + 2, y + 4, x + 3, y + 6, Ui.a(UiColors.OUTLINE, alpha));
	}

	/** A jagged crack across the rim, so dormant reads as damaged rather than merely dim. */
	private static void crack(GuiGraphicsExtractor g, int x, int y, float alpha) {
		int color = Ui.a(UiColors.AMBER, alpha);
		int[][] points = {{4, 0}, {5, 1}, {5, 2}, {6, 3}, {15, 17}, {14, 18}, {15, 19}};

		for (int[] p : points) {
			g.fill(x + p[0], y + p[1], x + p[0] + 1, y + p[1] + 1, color);
		}
	}

	/** A small brass crest (a pointed shield) marking a granted node. */
	private static void crest(GuiGraphicsExtractor g, int x, int y, float alpha) {
		g.fill(x - 1, y - 1, x + 6, y + 6, Ui.a(UiColors.OUTLINE, alpha));
		g.fill(x, y, x + 5, y + 3, Ui.a(UiColors.BRASS_LIGHT, alpha));
		g.fill(x + 1, y + 3, x + 4, y + 4, Ui.a(UiColors.BRASS, alpha));
		g.fill(x + 2, y + 4, x + 3, y + 5, Ui.a(UiColors.BRASS_DARK, alpha));
	}

	/** Corner brackets just outside a box. */
	private static void brackets(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
		int l = 4;
		g.fill(x, y, x + l, y + 1, color);
		g.fill(x, y, x + 1, y + l, color);
		g.fill(x + w - l, y, x + w, y + 1, color);
		g.fill(x + w - 1, y, x + w, y + l, color);
		g.fill(x, y + h - 1, x + l, y + h, color);
		g.fill(x, y + h - l, x + 1, y + h, color);
		g.fill(x + w - l, y + h - 1, x + w, y + h, color);
		g.fill(x + w - 1, y + h - l, x + w, y + h, color);
	}

	private NodeSocket() {
	}
}
