package com.oldrepublic.client.travel;

import com.oldrepublic.client.config.ClientConfig;
import com.oldrepublic.client.ui.Ui;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.util.Mth;

/**
 * The "breaking atmosphere" overlay (spec MILESTONE_5.md 4.7, 15.1, 15.3): as the ship reaches the
 * line between a planet's sky and its orbit, streaks of air rush past the screen under a heat
 * shimmer; across the line the view goes black (up into the orbit) or white with cloud (down into
 * the sky), then clears on the new place. About 30 ticks in all when the arrival's chunks are
 * ready at once. It covers the client's load of the new world: it is drawn over the HUD and over
 * the level loading screen, and its clock stands still while that screen shows.
 *
 * <p>Screen-space streaks, not particles: 40 at the Standard preset, 15 at Reduced (the Milestone 4
 * hyperspace overlay's rule). Nothing flashes: the shimmer is a slow drift. With the hyperspace
 * overlay turned off in the client config, a plain fade to black or white covers the same moments.
 * It always ends: once the server says the ship is across (or turned back), after
 * {@link #SAFETY_TICKS}, on disconnect and when the level is gone.
 */
final class AtmosphereOverlay {
	/** Ticks to cover the screen, and to clear once the ship is across. */
	private static final int IN_TICKS = 12;
	private static final int OUT_TICKS = 18;
	/** Never held longer than this (10 s), whatever the server says. */
	private static final int SAFETY_TICKS = 200;
	/** Streaks at the Standard preset; the preset scales them, with a floor for Reduced. */
	private static final int STREAKS = 40;
	private static final int MIN_STREAKS = 15;
	private static final int MAX_STREAKS = 90;
	/** The heat's glow, the void above the line and the cloud below it. */
	private static final int HEAT = 0xFFFF8A3C;
	private static final int HEAT_CORE = 0xFFFFD9A0;
	private static final int VOID = 0xFF020309;
	private static final int CLOUD = 0xFFE9EEF2;
	private static final int CLOUD_SHADE = 0xFFB9C4CE;
	private static final int STREAK_UP = 0xFFD6EAFF;
	private static final int STREAK_DOWN = 0xFFFFE4C0;

	private static boolean active;
	/** Into the orbit (else down into a sky). */
	private static boolean up;
	/** Ticks run while the level shows (the loading screen stops the clock), and in all. */
	private static int age;
	private static int total;
	/** The server said the ship is across; the overlay clears once the level is ready too. */
	private static boolean endRequested;
	/** The age the clearing began at, or -1 while it holds. */
	private static int fadeStart = -1;

	// ---------------------------------------------------------------- triggers

	/** The ship reaches the line: the streaks and the heat begin. */
	static void start(boolean goingUp) {
		active = true;
		up = goingUp;
		age = 0;
		total = 0;
		endRequested = false;
		fadeStart = -1;
	}

	/** The ship is across (or turned back): the overlay clears as soon as the new place is shown. */
	static void end() {
		if (active) {
			endRequested = true;
		}
	}

	/** Whether the overlay is up: from the line until it has cleared. */
	static boolean showing() {
		return active;
	}

	// ---------------------------------------------------------------- tick

	static void tick(Minecraft mc) {
		if (!active) {
			return;
		}

		if (mc.level == null || mc.player == null) {
			clear();
			return;
		}

		boolean ready = !(mc.gui.screen() instanceof LevelLoadingScreen);
		total++;

		if (ready) {
			age++;
		}

		if (fadeStart < 0) {
			if (endRequested && ready && age >= IN_TICKS || total > SAFETY_TICKS) {
				fadeStart = age;
			}
		} else if (age - fadeStart > OUT_TICKS) {
			clear();
		}
	}

	static void clear() {
		active = false;
		endRequested = false;
		fadeStart = -1;
		age = 0;
		total = 0;
	}

	// ---------------------------------------------------------------- drawing

	/** The HUD element (drawn last, over everything); skipped while the loading screen draws it instead. */
	static void extract(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		if (!active || Minecraft.getInstance().gui.screen() instanceof LevelLoadingScreen) {
			return;
		}

		draw(g, age + deltaTracker.getGameTimeDeltaPartialTick(true));
	}

	/** Over the level loading screen while the new world loads behind it (the clock stands still). */
	static void extractOnLoadingScreen(GuiGraphicsExtractor g, float partialTick) {
		if (active) {
			draw(g, age);
		}
	}

	private static void draw(GuiGraphicsExtractor g, float now) {
		float in = Mth.clamp(now / IN_TICKS, 0f, 1f);
		float out = fadeStart < 0 ? 0f : Mth.clamp((now - fadeStart) / OUT_TICKS, 0f, 1f);
		// The rush of air fades in at once and is gone halfway through the clearing; the black or the cloud
		// comes up behind it and clears last.
		float rush = in * (1f - Mth.clamp(out * 2f, 0f, 1f));
		float cover = Mth.clamp((now - IN_TICKS * 0.5f) / IN_TICKS, 0f, 1f) * (1f - out);

		if (rush <= 0.01f && cover <= 0.01f) {
			return;
		}

		int w = g.guiWidth();
		int h = g.guiHeight();

		if (!ClientConfig.get().hyperspaceOverlay) {
			// The plain fade: the same moments, no motion.
			g.fill(0, 0, w, h, Ui.a(up ? VOID : CLOUD, 0.95f * Math.max(rush * 0.6f, cover)));
			return;
		}

		drawHeat(g, w, h, rush, now);
		drawStreaks(g, w, h, rush, now);

		if (up) {
			g.fill(0, 0, w, h, Ui.a(VOID, 0.96f * cover));
		} else {
			drawCloud(g, w, h, cover, now);
		}
	}

	/** A warm wash, strongest at the screen's edges, with slow bands of shimmer drifting through it. */
	private static void drawHeat(GuiGraphicsExtractor g, int w, int h, float k, float now) {
		g.fill(0, 0, w, h, Ui.a(HEAT, 0.16f * k));
		int bands = 6;
		int depth = Math.max(10, Math.min(w, h) / 8);

		for (int i = 0; i < bands; i++) {
			int d = depth * (bands - i) / bands;
			float a = 0.06f * k;
			g.fill(0, 0, w, d, Ui.a(HEAT, a));
			g.fill(0, h - d, w, h, Ui.a(HEAT, a));
			g.fill(0, d, d, h - d, Ui.a(HEAT, a));
			g.fill(w - d, d, w, h - d, Ui.a(HEAT, a));
		}

		// The shimmer: thin bright bands that sway slowly up or down the screen.
		for (int i = 0; i < 5; i++) {
			float drift = (HyperspaceOverlay.hash(i * 5 + 11) + now * (0.01f + 0.006f * i) * (up ? 1f : -1f)) % 1f;
			int y = Math.round((drift < 0 ? drift + 1f : drift) * h);
			int sway = Math.round(Mth.sin(now * 0.15f + i) * 6f);
			g.fill(sway, y, w + sway, y + 2, Ui.a(HEAT_CORE, 0.08f * k));
		}
	}

	/**
	 * Lines of air rushing past: going up they run down the screen, going down they run up it. Each
	 * has a seeded column, speed and length, so the pattern holds from frame to frame.
	 */
	private static void drawStreaks(GuiGraphicsExtractor g, int w, int h, float k, float now) {
		int count = Mth.clamp(Math.round(STREAKS * ClientConfig.get().preset.particleMultiplier), MIN_STREAKS, MAX_STREAKS);
		int color = up ? STREAK_UP : STREAK_DOWN;

		for (int i = 0; i < count; i++) {
			float x = HyperspaceOverlay.hash(i * 3 + 1) * w;
			float speed = 0.03f + 0.05f * HyperspaceOverlay.hash(i * 3 + 2);
			float phase = (HyperspaceOverlay.hash(i * 3 + 3) + now * speed) % 1f;
			float length = 12f + 50f * HyperspaceOverlay.hash(i * 7 + 4);
			float travel = h + length;
			float y = up ? phase * travel - length : h - phase * travel;
			float alpha = k * (0.3f + 0.5f * HyperspaceOverlay.hash(i * 7 + 5));
			int thickness = HyperspaceOverlay.hash(i * 7 + 6) > 0.7f ? 2 : 1;
			g.fill(Math.round(x), Math.round(y), Math.round(x) + thickness, Math.round(y + length), Ui.a(color, alpha));
		}
	}

	/** Below the line: white cloud with soft grey layers drifting up past the ship. */
	private static void drawCloud(GuiGraphicsExtractor g, int w, int h, float k, float now) {
		g.fill(0, 0, w, h, Ui.a(CLOUD, 0.9f * k));
		int layers = 7;

		for (int i = 0; i < layers; i++) {
			float drift = (HyperspaceOverlay.hash(i * 9 + 7) - now * (0.012f + 0.004f * i)) % 1f;
			int y = Math.round((drift < 0 ? drift + 1f : drift) * h);
			int height = 8 + Math.round(HyperspaceOverlay.hash(i * 9 + 8) * h / 6f);
			g.fill(0, y, w, Math.min(h, y + height), Ui.a(CLOUD_SHADE, 0.18f * k));
		}
	}

	private AtmosphereOverlay() {
	}
}
