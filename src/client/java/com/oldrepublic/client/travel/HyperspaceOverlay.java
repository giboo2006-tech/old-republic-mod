package com.oldrepublic.client.travel;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.config.ClientConfig;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.client.quest.QuestClient;
import com.oldrepublic.client.ui.Ui;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.registry.ModSounds;
import com.oldrepublic.travel.ShipRules;
import com.oldrepublic.travel.network.FlightPhasePayload;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * The hyperspace overlay (spec MILESTONE_4.md 4.6, 11.2): at the jump the stars stretch into lines
 * that streak past a blue-white tunnel with a slow pulse and a dark vignette, over the engine's deep
 * hum, and the HUD fades under it. It covers the client's chunk load in the new world: it is drawn
 * over the HUD and over the level loading screen, and it ends only once the Hyperspace phase is over
 * <em>and</em> the client's level is ready, whichever is later.
 *
 * <p>Flashing effects off: no pulse. Reduced preset: fewer streaks. With the overlay turned off in
 * the client config, a plain dark fade covers the same moments. It always ends: when the player is
 * off the ship for {@link #UNSEATED_LIMIT} ticks with the level ready, after {@link #SAFETY_TICKS},
 * on disconnect and when the level is gone.
 */
final class HyperspaceOverlay {
	/** Ticks the stars take to stretch (the Jump phase) and to snap back at the end. */
	private static final int STRETCH_TICKS = 10;
	private static final int FADE_OUT_TICKS = 20;
	/** Never shown longer than this (30 s), whatever the server says. */
	private static final int SAFETY_TICKS = 600;
	/** Ended when the player has not been riding for this long with the level ready. */
	private static final int UNSEATED_LIMIT = 100;
	/** Streaks at the Standard preset; the preset scales them, with a floor for Reduced. */
	private static final int STREAKS = 40;
	private static final int MIN_STREAKS = 15;
	private static final int MAX_STREAKS = 90;
	/** The tunnel's colours: deep blue at the rim, blue-white at the core. */
	private static final int DEEP = 0xFF040A1C;
	private static final int TUNNEL = 0xFF2C5A9E;
	private static final int CORE = 0xFFD8ECFF;
	private static final int STREAK = 0xFFCFE6FF;

	private static boolean active;
	private static int startTick;
	/** The server said the Hyperspace phase is over; the overlay fades once the level is ready too. */
	private static boolean endRequested;
	/** The tick the fade-out began, or {@link Integer#MIN_VALUE} while it holds. */
	private static int fadeStart = Integer.MIN_VALUE;
	private static int unseated;
	private static @Nullable ShipLoop hum;

	// ---------------------------------------------------------------- triggers

	/** {@code flight_phase}: the jump starts the stretch; the descent (or the end of the flight) asks it to end. */
	static void onPhase(FlightPhasePayload payload) {
		switch (payload.phase()) {
			case ShipRules.PHASE_JUMP, ShipRules.PHASE_HYPERSPACE -> begin();
			case ShipRules.PHASE_DESCENT, ShipRules.PHASE_SETTLE, FlightPhasePayload.OVER -> requestEnd();
			default -> {
			}
		}
	}

	/** {@code hyperspace} (to the rider): intensity 0 at the start of the phase, 1 at its end. */
	static void onEffect(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		if (payload.intensity() < 0.5f) {
			begin();
			startHum();
		} else {
			requestEnd();
		}
	}

	private static void begin() {
		if (active && fadeStart == Integer.MIN_VALUE) {
			return;
		}

		active = true;
		startTick = QuestClient.ticks();
		endRequested = false;
		fadeStart = Integer.MIN_VALUE;
		unseated = 0;
	}

	private static void requestEnd() {
		if (active) {
			endRequested = true;
		}
	}

	/** The deep hum, heard by this player alone; it fades out with the overlay. */
	private static void startHum() {
		if (hum == null || hum.finished()) {
			hum = ShipLoop.play(ModSounds.SHIP_HYPERSPACE, null, 0.9f, 15, HyperspaceOverlay::holding);
		}
	}

	/** Whether the overlay is on and not fading out. */
	static boolean holding() {
		return active && fadeStart == Integer.MIN_VALUE;
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

		int now = QuestClient.ticks();
		boolean ready = levelReady(mc);

		if (ready && mc.player.getVehicle() == null) {
			unseated++;
		} else {
			unseated = 0;
		}

		if (fadeStart == Integer.MIN_VALUE) {
			boolean done = endRequested && ready;

			if (done || now - startTick > SAFETY_TICKS || unseated > UNSEATED_LIMIT) {
				fadeStart = now;
			}
		} else if (now - fadeStart > FADE_OUT_TICKS) {
			clear();
		}
	}

	/** The client has a level and is not on the level loading screen (Fabric's own test of a finished load). */
	private static boolean levelReady(Minecraft mc) {
		return mc.level != null && !(mc.gui.screen() instanceof LevelLoadingScreen);
	}

	/** 0 to 1: how strongly the overlay covers the screen now. */
	static float intensity(float partialTick) {
		if (!active) {
			return 0f;
		}

		float now = QuestClient.ticks() + partialTick;
		float in = Mth.clamp((now - startTick) / STRETCH_TICKS, 0f, 1f);

		if (fadeStart == Integer.MIN_VALUE) {
			return in;
		}

		return in * (1f - Mth.clamp((now - fadeStart) / FADE_OUT_TICKS, 0f, 1f));
	}

	static void clear() {
		active = false;
		endRequested = false;
		fadeStart = Integer.MIN_VALUE;
		unseated = 0;

		if (hum != null) {
			hum.release();
			hum = null;
		}
	}

	// ---------------------------------------------------------------- drawing

	/** The HUD element (drawn last, over everything); skipped while the loading screen draws it instead. */
	static void extract(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();

		if (!active || mc.gui.screen() instanceof LevelLoadingScreen) {
			return;
		}

		draw(g, deltaTracker.getGameTimeDeltaPartialTick(true));
	}

	/** Over the level loading screen while the new world loads behind it. */
	static void extractOnLoadingScreen(GuiGraphicsExtractor g, float partialTick) {
		if (active) {
			draw(g, partialTick);
		}
	}

	private static void draw(GuiGraphicsExtractor g, float partialTick) {
		float k = intensity(partialTick);

		if (k <= 0.01f) {
			return;
		}

		int w = g.guiWidth();
		int h = g.guiHeight();
		float time = QuestClient.ticks() + partialTick;

		if (!ClientConfig.get().hyperspaceOverlay) {
			// The plain fade: the same moments, no motion.
			g.fill(0, 0, w, h, Ui.a(DEEP, 0.97f * k));
			return;
		}

		float pulse = Vfx.flashing() ? 0.5f + 0.5f * Mth.sin(time * 0.09f) : 0.5f;
		drawTunnel(g, w, h, k, pulse);
		drawStreaks(g, w, h, k, time);
		drawVignette(g, w, h, k);
	}

	/** Near-opaque deep blue, brightening toward a blue-white core in soft concentric steps. */
	private static void drawTunnel(GuiGraphicsExtractor g, int w, int h, float k, float pulse) {
		g.fill(0, 0, w, h, Ui.a(DEEP, 0.97f * k));
		int cx = w / 2;
		int cy = h / 2;
		int rings = 9;
		float reach = Math.max(w, h) * 0.55f;

		for (int i = 0; i < rings; i++) {
			float t = (float) i / (rings - 1);
			int rx = Math.round(reach * (1f - t * 0.92f));
			int ry = Math.round(rx * 0.62f);
			float a = (0.05f + 0.07f * t) * k * (0.8f + 0.4f * pulse);
			int color = t < 0.7f ? TUNNEL : CORE;
			g.fill(cx - rx, cy - ry, cx + rx, cy + ry, Ui.a(color, a));
		}
	}

	/**
	 * Lines rushing out of the core: each has a seeded angle and speed, grows longer as it nears the
	 * edge (perspective) and longer still while the stars stretch at the jump.
	 */
	private static void drawStreaks(GuiGraphicsExtractor g, int w, int h, float k, float time) {
		int count = Mth.clamp(Math.round(STREAKS * ClientConfig.get().preset.particleMultiplier), MIN_STREAKS, MAX_STREAKS);
		float cx = w / 2f;
		float cy = h / 2f;
		float edge = (float) Math.sqrt(cx * cx + cy * cy);
		float stretch = 0.25f + 0.75f * k;

		for (int i = 0; i < count; i++) {
			float angle = hash(i * 3 + 1) * Mth.TWO_PI;
			float speed = 0.006f + 0.012f * hash(i * 3 + 2);
			float phase = (hash(i * 3 + 3) + time * speed) % 1f;
			float r0 = edge * phase * phase;
			float length = (6f + 90f * phase * phase) * stretch;
			float alpha = k * Mth.clamp(phase * 3f, 0f, 1f) * (0.35f + 0.5f * hash(i * 7 + 5));
			int thickness = phase > 0.6f ? 2 : 1;

			g.pose().pushMatrix();
			g.pose().translate(cx, cy);
			g.pose().rotate(angle);
			g.fill(Math.round(r0), 0, Math.round(r0 + length), thickness, Ui.a(STREAK, alpha));
			g.pose().popMatrix();
		}
	}

	/** Dark edges in stepped bands, so the eye stays on the core. */
	private static void drawVignette(GuiGraphicsExtractor g, int w, int h, float k) {
		int bands = 6;
		int depth = Math.max(8, Math.min(w, h) / 10);

		for (int i = 0; i < bands; i++) {
			int d = depth * (bands - i) / bands;
			float a = 0.12f * k;
			g.fill(0, 0, w, d, Ui.a(0xFF000000, a));
			g.fill(0, h - d, w, h, Ui.a(0xFF000000, a));
			g.fill(0, d, d, h - d, Ui.a(0xFF000000, a));
			g.fill(w - d, d, w, h - d, Ui.a(0xFF000000, a));
		}
	}

	/** A fixed pseudo-random 0 to 1 per streak, so the pattern holds from frame to frame. */
	private static float hash(int n) {
		int x = n * 0x45D9F3B;
		x = (x >>> 16 ^ x) * 0x45D9F3B;
		x = x >>> 16 ^ x;
		return (x & 0xFFFF) / 65535f;
	}

	private HyperspaceOverlay() {
	}
}
