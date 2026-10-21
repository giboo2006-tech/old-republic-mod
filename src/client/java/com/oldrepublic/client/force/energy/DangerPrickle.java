package com.oldrepublic.client.force.energy;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * Danger Sense at the corner of the eye, drawn over the edge of the view on the side an unseen attack comes from: the edge
 * there prickles with a pale light that fades inward and toward the ends of that stretch of edge, and fine grit streams
 * in from it, each speck with a short streak behind it. It comes up fast and fades within about a second; no arrow, ring or
 * flash. It adds to the dust tick of {@code DangerEdgeHud}, never replaces it, and never fades with a quality preset.
 */
public final class DangerPrickle {
	private static final int TICKS = 20;
	private static final float IN = 2f;
	private static final float OUT = 13f;
	/** How far in from the edge the light reaches, and the step of its bands, in GUI pixels. */
	private static final int DEPTH = 30;
	private static final int STEP = 2;
	/** The stretch of edge that prickles, as a share of that edge's length, and its pieces. */
	private static final float SPAN = 0.34f;
	private static final int PIECES = 10;
	/** Distance of the stretch's middle from the corner, so a tell straight behind still shows on the bottom edge. */
	private static final int MARGIN = 16;
	private static final float LIGHT_ALPHA = 0.34f;
	private static final int SPECKS = 26;
	/** Pale, warm light. */
	private static final int LIGHT = 0xF4E8CC;
	private static final int GRIT = 0xFFF6E2;

	private static long start = Long.MIN_VALUE;
	private static float yaw;
	private static long seed;

	/** Shows the prickle toward a tell {@code relativeYaw} degrees from straight ahead (positive to the right, 180 behind). */
	public static void prickle(float relativeYaw) {
		start = EnergyRender.ticks();
		yaw = relativeYaw;
		seed++;
	}

	/** The HUD layer (registered after the vanilla overlays). */
	public static void extract(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();

		if (start == Long.MIN_VALUE || mc.player == null || mc.gui.hud.isHidden()) {
			return;
		}

		float t = EnergyRender.ticks() - start + deltaTracker.getGameTimeDeltaPartialTick(true);

		if (t > TICKS || t < 0f) {
			start = t > TICKS ? Long.MIN_VALUE : start;
			return;
		}

		float alpha = Math.min(1f, t / IN) * Mth.clamp((TICKS - t) / OUT, 0f, 1f);

		if (alpha <= 0.01f) {
			return;
		}

		int width = g.guiWidth();
		int height = g.guiHeight();
		double rad = Math.toRadians(yaw);
		float dx = (float) Math.sin(rad);
		float dy = (float) -Math.cos(rad);
		float halfW = width / 2f - MARGIN;
		float halfH = height / 2f - MARGIN;
		boolean sideEdge = halfW / Math.max(1.0e-3f, Math.abs(dx)) < halfH / Math.max(1.0e-3f, Math.abs(dy));
		float reach = Math.min(halfW / Math.max(1.0e-3f, Math.abs(dx)), halfH / Math.max(1.0e-3f, Math.abs(dy)));
		// The middle of the stretch, on the edge itself, and the inward direction there.
		float cx = sideEdge ? (dx > 0 ? width : 0) : width / 2f + dx * reach;
		float cy = sideEdge ? height / 2f + dy * reach : (dy > 0 ? height : 0);
		float inX = sideEdge ? (dx > 0 ? -1f : 1f) : 0f;
		float inY = sideEdge ? 0f : (dy > 0 ? -1f : 1f);
		float span = (sideEdge ? height : width) * SPAN;
		float piece = span / PIECES;

		// The light: bands parallel to the edge, fading inward and toward the ends of the stretch.
		for (int d = 0; d < DEPTH; d += STEP) {
			float fall = 1f - (float) d / DEPTH;

			for (int k = 0; k < PIECES; k++) {
				float s = (k + 0.5f) / PIECES * 2f - 1f;
				float a = LIGHT_ALPHA * alpha * fall * fall * (1f - s * s);
				int color = Mth.clamp(Math.round(a * 255f), 0, 255) << 24 | LIGHT;

				if (a < 0.004f) {
					continue;
				}

				float along = -span / 2f + k * piece;

				if (sideEdge) {
					int x0 = Math.round(cx + inX * d);
					int x1 = Math.round(cx + inX * (d + STEP));
					int y0 = Math.round(cy + along);
					g.fill(Math.min(x0, x1), y0, Math.max(x0, x1), Math.round(y0 + piece), color);
				} else {
					int y0 = Math.round(cy + inY * d);
					int y1 = Math.round(cy + inY * (d + STEP));
					int x0 = Math.round(cx + along);
					g.fill(x0, Math.min(y0, y1), Math.round(x0 + piece), Math.max(y0, y1), color);
				}
			}
		}

		// Grit streaming in from the edge, each speck with a short streak behind it.
		RandomSource random = RandomSource.create(seed * 0x9E3779B97F4A7C15L);

		for (int i = 0; i < SPECKS; i++) {
			float s = (random.nextFloat() * 2f - 1f) * span * 0.5f;
			float speed = 0.9f + random.nextFloat() * 1.6f;
			float depth = 2f + random.nextFloat() * 8f + t * speed;
			float a = alpha * (0.45f + 0.45f * random.nextFloat()) * Mth.clamp(1f - depth / (DEPTH * 2.2f), 0f, 1f);

			if (a < 0.02f) {
				continue;
			}

			float x = cx + inX * depth + (sideEdge ? 0f : s);
			float y = cy + inY * depth + (sideEdge ? s : 0f);
			int px = Mth.floor(x);
			int py = Mth.floor(y);
			int size = random.nextFloat() < 0.35f ? 2 : 1;
			int streak = 2 + Math.round(speed * 2f);
			int grit = Mth.clamp(Math.round(a * 255f), 0, 255) << 24 | GRIT;
			int tail = Mth.clamp(Math.round(a * 0.4f * 255f), 0, 255) << 24 | GRIT;
			g.fill(px, py, px + size, py + size, grit);

			if (sideEdge) {
				int back = Math.round(-inX * streak);
				g.fill(Math.min(px, px + back), py, Math.max(px, px + back), py + 1, tail);
			} else {
				int back = Math.round(-inY * streak);
				g.fill(px, Math.min(py, py + back), px + 1, Math.max(py, py + back), tail);
			}
		}
	}

	/** Forgets the prickle (disconnect). */
	public static void reset() {
		start = Long.MIN_VALUE;
	}

	private DangerPrickle() {
	}
}
