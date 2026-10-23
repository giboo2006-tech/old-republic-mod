package com.oldrepublic.client.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

/**
 * The shared Old Republic UI kit: framed panels and wells (nine-slice GUI sprites), framed and
 * segmented progress bars, label/value rows, progress rings around chamfered badges, the
 * decorative notch-script strip and holo scanlines. Every method takes an alpha so HUD elements
 * can fade as a whole.
 */
public final class Ui {
	/** GUI sprites under {@code textures/gui/sprites/ui/} (stitched into the vanilla GUI atlas). */
	public static final Identifier PANEL = sprite("panel");
	public static final Identifier PANEL_INSET = sprite("panel_inset");
	public static final Identifier HUD_PLATE = sprite("hud_plate");
	public static final Identifier BUTTON = sprite("button");
	public static final Identifier BUTTON_HOVER = sprite("button_hover");
	public static final Identifier BUTTON_PRESSED = sprite("button_pressed");
	public static final Identifier BUTTON_DISABLED = sprite("button_disabled");
	public static final Identifier CARD = sprite("card");
	public static final Identifier CARD_SELECTED = sprite("card_selected");
	public static final Identifier CARD_LOCKED = sprite("card_locked");
	public static final Identifier SOCKET = sprite("socket");
	public static final Identifier SLOT = sprite("slot");
	public static final Identifier EMBLEM = sprite("emblem");
	public static final Identifier EMBLEM_LARGE = sprite("emblem_large");
	public static final Identifier PROJECTOR = sprite("projector");
	public static final Identifier JOURNAL_SEAL = sprite("journal_seal");
	public static final Identifier JOURNAL_ORNAMENT = sprite("journal_ornament");

	/** Plain texture (not an atlas sprite): the notch-script strip, blitted with explicit UVs. */
	private static final Identifier GLYPHS = OldRepublicMod.id("textures/gui/ui/glyph_strip.png");
	private static final int GLYPHS_W = 128;
	public static final int GLYPHS_H = 7;

	private static final Map<Long, int[]> RINGS = new HashMap<>();

	public static Identifier sprite(String name) {
		return OldRepublicMod.id("ui/" + name);
	}

	public static int a(int color, float alpha) {
		return ARGB.multiplyAlpha(color, Mth.clamp(alpha, 0f, 1f));
	}

	// ---------------------------------------------------------------- frames

	/** Draws a GUI sprite (nine-sliced when its metadata says so) tinted by alpha. */
	public static void sprite(GuiGraphicsExtractor g, Identifier sprite, int x, int y, int w, int h, float alpha) {
		g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, w, h, ARGB.white(Mth.clamp(alpha, 0f, 1f)));
	}

	/** Draws a GUI sprite with an ARGB tint. */
	public static void sprite(GuiGraphicsExtractor g, Identifier sprite, int x, int y, int w, int h, int tint) {
		g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, w, h, tint);
	}

	/** Main screen frame: durasteel with brass trim, rivets and holo corner brackets, glass inside. */
	public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		sprite(g, PANEL, x, y, w, h, 1f);
	}

	/** Recessed well for stat blocks and previews. */
	public static void inset(GuiGraphicsExtractor g, int x, int y, int w, int h, float alpha) {
		sprite(g, PANEL_INSET, x, y, w, h, alpha);
	}

	/** Light HUD frame over translucent glass. */
	public static void plate(GuiGraphicsExtractor g, int x, int y, int w, int h, float alpha) {
		sprite(g, HUD_PLATE, x, y, w, h, alpha);
	}

	// ---------------------------------------------------------------- bars

	/**
	 * A framed bar: dark outline and well, then the fill with a vertical gradient, a highlight row
	 * and a bright leading edge, then segment separators.
	 *
	 * @param fill     0 to 1
	 * @param top      fill colour at the top row
	 * @param bottom   fill colour at the bottom row
	 * @param segments number of segments (separators drawn between them), or 0 for none
	 */
	public static void bar(GuiGraphicsExtractor g, int x, int y, int w, int h, float fill, int top, int bottom, int segments, float alpha) {
		frame(g, x, y, w, h, alpha);
		int ix = x + 1;
		int iw = w - 2;
		int ih = h - 2;
		int fw = Math.round(iw * Mth.clamp(fill, 0f, 1f));

		if (fw > 0) {
			fillInner(g, ix, y + 1, fw, ih, top, bottom, alpha);
		}

		segments(g, ix, y + 1, iw, ih, segments, alpha);
	}

	/** Outline plus dark well, the empty part of every bar. */
	public static void frame(GuiGraphicsExtractor g, int x, int y, int w, int h, float alpha) {
		g.fill(x - 1, y - 1, x + w + 1, y + h + 1, a(0xB0000000, alpha));
		g.fill(x, y, x + w, y + h, a(UiColors.STEEL_DARK, alpha));
		g.fill(x + 1, y + 1, x + w - 1, y + h - 1, a(UiColors.WELL, alpha));
		// A lit lower edge sells the recess.
		g.fill(x + 1, y + h - 1, x + w - 1, y + h, a(0x40A8B4C0, alpha));
	}

	/** Gradient fill with a highlight row and a bright leading column. */
	public static void fillInner(GuiGraphicsExtractor g, int x, int y, int w, int h, int top, int bottom, float alpha) {
		g.fillGradient(x, y, x + w, y + h, a(top, alpha), a(bottom, alpha));

		if (h > 2) {
			g.fill(x, y, x + w, y + 1, a(0x50FFFFFF, alpha));
		}

		g.fill(x + w - 1, y, x + w, y + h, a(0xC0FFFFFF, alpha));
	}

	/** Thin separators splitting a bar into equal segments. */
	public static void segments(GuiGraphicsExtractor g, int x, int y, int w, int h, int segments, float alpha) {
		for (int i = 1; i < segments; i++) {
			int sx = x + Math.round(w * (float) i / segments);
			g.fill(sx, y, sx + 1, y + h, a(0x90000000, alpha));
		}
	}

	/** A translucent block between two fractions of a bar (spent energy, previews). */
	public static void span(GuiGraphicsExtractor g, int x, int y, int w, int h, float from, float to, int color, float alpha) {
		int x0 = x + 1 + Math.round((w - 2) * Mth.clamp(Math.min(from, to), 0f, 1f));
		int x1 = x + 1 + Math.round((w - 2) * Mth.clamp(Math.max(from, to), 0f, 1f));

		if (x1 > x0) {
			g.fill(x0, y + 1, x1, y + h - 1, a(color, alpha));
		}
	}

	// ---------------------------------------------------------------- rings and pips

	/**
	 * Lights the outline of a chamfered box clockwise from the top centre, like a dial filling up.
	 *
	 * @param cut size of the diagonal corner cut
	 */
	public static void ring(GuiGraphicsExtractor g, int x, int y, int w, int h, int cut, float fraction, int lit, int dim, float alpha) {
		int[] path = ringPath(w, h, cut);
		int n = path.length / 2;
		int on = Math.round(n * Mth.clamp(fraction, 0f, 1f));

		for (int i = 0; i < n; i++) {
			int px = x + path[i * 2];
			int py = y + path[i * 2 + 1];
			int color = i < on ? (i == on - 1 && on < n ? 0xFFFFF4D6 : lit) : dim;

			if (ARGB.alpha(color) == 0) {
				continue;
			}

			g.fill(px, py, px + 1, py + 1, a(color, alpha));
		}
	}

	private static int[] ringPath(int w, int h, int cut) {
		long key = ((long) w << 32) | ((long) h << 16) | cut;
		return RINGS.computeIfAbsent(key, k -> buildRing(w, h, cut));
	}

	/** Pixel offsets along a chamfered rectangle's outline, clockwise from the top centre. */
	private static int[] buildRing(int w, int h, int cut) {
		int right = w - 1;
		int bottom = h - 1;
		int[][] corners = {
				{w / 2, 0}, {right - cut, 0}, {right, cut}, {right, bottom - cut}, {right - cut, bottom},
				{cut, bottom}, {0, bottom - cut}, {0, cut}, {cut, 0}, {w / 2, 0}};
		int[] out = new int[(w + h) * 4 + 8];
		int count = 0;

		for (int c = 0; c < corners.length - 1; c++) {
			int x0 = corners[c][0];
			int y0 = corners[c][1];
			int x1 = corners[c + 1][0];
			int y1 = corners[c + 1][1];
			int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));

			for (int s = 0; s < steps; s++) {
				out[count++] = x0 + Integer.signum(x1 - x0) * s;
				out[count++] = y0 + Integer.signum(y1 - y0) * s;
			}
		}

		int[] trimmed = new int[count];
		System.arraycopy(out, 0, trimmed, 0, count);
		return trimmed;
	}

	/** A row of small square pips, the first {@code filled} lit (mastery ranks and the like). */
	public static void pips(GuiGraphicsExtractor g, int x, int y, int count, int filled, int lit, float alpha) {
		for (int i = 0; i < count; i++) {
			int px = x + i * 6;
			g.fill(px, y, px + 5, y + 5, a(UiColors.OUTLINE, alpha));

			if (i < filled) {
				g.fill(px + 1, y + 1, px + 4, y + 4, a(lit, alpha));
				g.fill(px + 1, y + 1, px + 4, y + 2, a(0x80FFFFFF, alpha));
			} else {
				g.fill(px + 1, y + 1, px + 4, y + 4, a(UiColors.STEEL_DARK, alpha));
			}
		}
	}

	// ---------------------------------------------------------------- decoration

	/**
	 * The decorative notch-script strip, {@link #GLYPHS_H} px tall, scrolled by {@code offset}
	 * pixels so it can drift slowly like a data readout.
	 */
	public static void glyphs(GuiGraphicsExtractor g, int x, int y, int w, int color, int offset) {
		int dx = 0;

		while (dx < w) {
			int u = Math.floorMod(offset + dx, GLYPHS_W);
			int chunk = Math.min(w - dx, GLYPHS_W - u);
			// API-CHECK: blit(pipeline, id, x, y, u, v, w, h, texW, texH, argb) draws a 1:1 region tinted by argb.
			g.blit(RenderPipelines.GUI_TEXTURED, GLYPHS, x + dx, y, u, 0f, chunk, GLYPHS_H, GLYPHS_W, GLYPHS_H, color);
			dx += chunk;
		}
	}

	/** Faint horizontal holo scanlines every third row, drifting down with time. */
	public static void scanlines(GuiGraphicsExtractor g, int x, int y, int w, int h, int color, float time) {
		int shift = Math.floorMod((int) (time / 4f), 3);

		for (int row = shift; row < h; row += 3) {
			g.fill(x, y + row, x + w, y + row + 1, color);
		}
	}

	/** Horizontal line fading out toward both ends (drawn as stepped columns). */
	public static void fadeLine(GuiGraphicsExtractor g, int cx, int y, int half, int color, float alpha) {
		int fade = Math.max(4, half / 3);
		g.fill(cx - half + fade, y, cx + half - fade, y + 1, a(color, alpha));

		for (int i = 0; i < fade; i += 2) {
			float t = (float) (i + 1) / fade;
			g.fill(cx - half + i, y, cx - half + i + 2, y + 1, a(color, alpha * t));
			g.fill(cx + half - i - 2, y, cx + half - i, y + 1, a(color, alpha * t));
		}
	}

	/**
	 * A section header: the title in holo white, then the notch strip and a fading rule filling the
	 * rest of {@code w}.
	 */
	public static void header(GuiGraphicsExtractor g, Font font, Component title, int x, int y, int w, float time) {
		g.text(font, title, x, y, UiColors.HOLO_BRIGHT, true);
		int tx = x + font.width(title) + 5;
		int rest = x + w - tx;

		if (rest > 12) {
			glyphs(g, tx, y + 1, rest, a(UiColors.HOLO, 0.35f * UiAnim.flicker(time)), (int) (time / 6f));
		}

		g.fill(x, y + 10, x + w, y + 11, a(UiColors.HOLO_DIM, 0.9f));
		g.fill(x, y + 10, x + 6, y + 11, UiColors.HOLO);
	}

	// ---------------------------------------------------------------- text

	/** Text with shadow, skipped when nearly transparent (fully transparent text renders opaque). */
	public static void text(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int color, float alpha) {
		if (alpha > 0.04f) {
			g.text(font, text, x, y, a(color, alpha), true);
		}
	}

	public static void text(GuiGraphicsExtractor g, Font font, String text, int x, int y, int color, float alpha) {
		if (alpha > 0.04f) {
			g.text(font, text, x, y, a(color, alpha), true);
		}
	}

	/**
	 * A label on the left and its value right-aligned, joined by a dotted leader so the eye can
	 * follow the row.
	 */
	public static void labelValue(GuiGraphicsExtractor g, Font font, Component label, Component value, int x, int y, int w, int labelColor, int valueColor) {
		g.text(font, label, x, y, labelColor, true);
		int vw = font.width(value);
		g.text(font, value, x + w - vw, y, valueColor, true);
		int from = x + font.width(label) + 3;
		int to = x + w - vw - 3;

		for (int dx = from; dx < to; dx += 2) {
			g.fill(dx, y + 7, dx + 1, y + 8, a(UiColors.TEXT_DIM, 0.6f));
		}
	}

	/**
	 * Word-wraps plain text to {@code width}, keeping at most {@code maxLines} lines; when text is
	 * left over, the last kept line ends with an ellipsis. Explicit line breaks are honoured.
	 */
	public static List<String> wrap(Font font, String text, int width, int maxLines) {
		List<String> lines = new ArrayList<>();

		for (String paragraph : text.split("\n")) {
			StringBuilder line = new StringBuilder();

			for (String word : paragraph.split(" ")) {
				if (!line.isEmpty() && font.width(line + " " + word) > width) {
					lines.add(line.toString());
					line.setLength(0);
				}

				if (!line.isEmpty()) {
					line.append(' ');
				}

				line.append(word);
			}

			lines.add(line.toString());
		}

		if (lines.size() <= maxLines) {
			return lines;
		}

		List<String> kept = new ArrayList<>(lines.subList(0, Math.max(0, maxLines)));

		if (!kept.isEmpty()) {
			int last = kept.size() - 1;
			kept.set(last, fit(font, kept.get(last) + "...", width));
		}

		return kept;
	}

	/** A short label string, cut with an ellipsis when it would overflow {@code maxWidth}. */
	public static String fit(Font font, String text, int maxWidth) {
		if (font.width(text) <= maxWidth) {
			return text;
		}

		return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("..."))) + "...";
	}

	// ---------------------------------------------------------------- placement

	/** The top-left corner of a HUD element. */
	public record Spot(int x, int y) {
	}

	/**
	 * Where a bar {@code w} by {@code h} that belongs {@code below} pixels under the crosshair goes. In first person it
	 * stays there. In third person that spot is the player's own chest (vanilla hides the crosshair there), so the bar
	 * sits beside the head instead, on the side away from the sword arm as the view shows it, clear of the body and the
	 * held weapon.
	 */
	public static Spot crosshairBar(GuiGraphicsExtractor g, int w, int h, int below) {
		Minecraft mc = Minecraft.getInstance();
		CameraType camera = mc.options.getCameraType();
		int cx = g.guiWidth() / 2;
		int cy = g.guiHeight() / 2;

		if (camera.isFirstPerson() || mc.player == null) {
			return new Spot(cx - w / 2, cy + below);
		}

		// From behind, the player's left arm is on the left of the screen; from the front it is on the right.
		boolean left = (mc.player.getMainArm() == HumanoidArm.RIGHT) != (camera == CameraType.THIRD_PERSON_FRONT);
		int gap = Math.max(26, g.guiHeight() / 9);
		return new Spot(left ? cx - gap - w : cx + gap, cy - h / 2);
	}

	private Ui() {
	}
}
