package com.oldrepublic.client.equipment;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.config.ClientConfig;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.client.ui.Ui;
import com.oldrepublic.client.ui.UiColors;
import com.oldrepublic.equipment.EquipmentM4Balance;
import com.oldrepublic.equipment.blaster.BlasterBalance;
import com.oldrepublic.equipment.blaster.BlasterHeat;
import com.oldrepublic.equipment.blaster.BlasterItem;
import com.oldrepublic.equipment.blaster.BlasterRules;
import com.oldrepublic.equipment.blaster.Blasters;
import com.oldrepublic.equipment.jet.JetPack;
import com.oldrepublic.equipment.mod.ModEffects;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * The blaster HUD (spec MILESTONE_4.md 5.8 and 11.2), all drawn around the crosshair:
 * <ul>
 * <li><b>Reticle.</b> While a blaster is in the main hand (first person, {@code blasterReticle} on)
 * the vanilla crosshair is replaced by four short ticks whose gap is the current spread: hip,
 * steadied, moving, airborne and the focused barrel, eased so it breathes rather than jumps. A rifle
 * charge draws the ticks in; a loaded heavy bolt adds corner brackets. Locked by an overheat, the
 * ticks turn red-orange. With the reticle off, the vanilla crosshair stays and the markers and gauge
 * still show.</li>
 * <li><b>Hit markers.</b> A brief X on a confirmed hit (larger and red-orange on a kill), a small
 * amber diamond when a bolt was deflected.</li>
 * <li><b>Heat gauge.</b> A thin arc right of the crosshair filling bottom to top: holo blue, warming
 * to amber; the perfect-vent band (70 to 90) is marked by two notches and glows white while the heat
 * is inside it; red-orange from 90; flashing during an overheat lock (a steady glow with flashing
 * effects off). Free shots after a perfect vent show as small white dots. Shown while a blaster is
 * held and for 40 ticks after.</li>
 * <li><b>Jet pack pips</b> under the gauge while a jet pack is worn and the player is airborne or a
 * charge is refilling.</li>
 * </ul>
 * The client never reads the balance file, so spread and cooling use the default numbers; they only
 * drive this display, never a shot.
 */
final class BlasterHud {
	private static final BlasterBalance DEFAULTS = new BlasterBalance();
	private static final EquipmentM4Balance.ModNumbers MOD_DEFAULTS = new EquipmentM4Balance.ModNumbers();
	/** Ticks the gauge stays after the blaster is put away; the last {@link #FADE} fade it out. */
	private static final int LINGER = 40;
	private static final int FADE = 10;
	private static final int TICK_LENGTH = 4;
	private static final int MIN_GAP = 2;
	private static final int MAX_GAP = 40;
	private static final int HIT_TICKS = 7;
	private static final int DEFLECT_TICKS = 12;
	private static final int PERFECT_TICKS = 10;
	/** The gauge arc: radius and half-span (radians) around the crosshair's right side. */
	private static final float GAUGE_RADIUS = 13f;
	private static final float GAUGE_HALF_SPAN = 0.85f;
	private static final int GAUGE_POINTS = 26;
	private static final int COOL = 0xFF8FD8FF;
	private static final int WARM = 0xFFFFB347;
	private static final int HOT = 0xFFFF5A3C;
	private static final int TICK_COLOR = 0xFFF2F8FF;
	private static final int SHADOW = 0x90000000;
	private static final int LOADED = 0xFFB8FFC8;
	private static final int KILL = 0xFFFF6A50;

	private static int linger;
	private static BlasterItem.@Nullable Kind kind;
	private static @Nullable BlasterHeat heat;
	private static float gap;
	private static float gapO;
	private static boolean overheatLock;

	static void init() {
		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, vanilla -> (graphics, deltaTracker) -> {
			if (reticleShown(Minecraft.getInstance())) {
				reticle(graphics, deltaTracker);
			} else {
				vanilla.extractRenderState(graphics, deltaTracker);
			}
		});
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, OldRepublicMod.id("blaster_heat"), BlasterHud::gauge);
		ClientTickEvents.END_CLIENT_TICK.register(BlasterHud::tick);
	}

	// ---------------------------------------------------------------- state

	private static void tick(Minecraft mc) {
		LocalPlayer player = mc.player;

		if (player == null || mc.level == null) {
			linger = 0;
			heat = null;
			kind = null;
			return;
		}

		if (mc.isPaused()) {
			return;
		}

		gapO = gap;
		ItemStack main = player.getMainHandItem();

		if (main.getItem() instanceof BlasterItem blaster) {
			linger = LINGER;
			kind = blaster.kind();
			heat = Blasters.heat(main);
			float target = spreadDegrees(player, main, blaster.kind());
			gap = gap <= 0f ? target : gap + (target - gap) * 0.45f;
		} else if (linger > 0) {
			linger--;
		}

		long now = mc.level.getGameTime();
		overheatLock = heat != null && BlasterRules.overheated(heat, now);
	}

	/** The cone half-angle the server would use for {@code player}'s next shot, in degrees. */
	private static float spreadDegrees(LocalPlayer player, ItemStack gun, BlasterItem.Kind kind) {
		return BlasterRules.spread(BlasterBalance.of(DEFAULTS.hipSpread, kind), Blasters.steadied(player), Blasters.fast(player),
				!player.onGround(), ModEffects.spread(gun, MOD_DEFAULTS), DEFAULTS);
	}

	private static boolean reticleShown(Minecraft mc) {
		return mc.player != null && ClientConfig.get().blasterReticle && !mc.player.isSpectator()
				&& mc.options.getCameraType().isFirstPerson() && mc.player.getMainHandItem().getItem() instanceof BlasterItem;
	}

	// ---------------------------------------------------------------- the reticle

	private static void reticle(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();

		if (mc.gui.hud.isHidden()) {
			return;
		}

		float partial = deltaTracker.getGameTimeDeltaPartialTick(true);
		int cx = g.guiWidth() / 2;
		int cy = g.guiHeight() / 2;
		float degrees = Mth.lerp(partial, gapO, gap);
		int charge = GearEffectsClient.chargeTicks();
		float pull = charge < 0 ? 0f : Math.min(1f, (charge + partial) / DEFAULTS.chargedTicks);
		int px = Math.round(Mth.lerp(pull * 0.6f, pixels(mc, g, degrees), MIN_GAP));
		int color = overheatLock ? HOT : pull >= 1f || GearEffectsClient.chargeLoaded() ? LOADED : TICK_COLOR;

		// Up, down, left, right, each with a one-pixel shadow so it reads on snow and sky.
		tick(g, cx, cy - px - TICK_LENGTH, 1, TICK_LENGTH, color);
		tick(g, cx, cy + px + 1, 1, TICK_LENGTH, color);
		tick(g, cx - px - TICK_LENGTH, cy, TICK_LENGTH, 1, color);
		tick(g, cx + px + 1, cy, TICK_LENGTH, 1, color);
		g.fill(cx, cy, cx + 1, cy + 1, Ui.a(color, 0.7f));

		if (GearEffectsClient.chargeLoaded()) {
			brackets(g, cx, cy, px + TICK_LENGTH + 2, LOADED);
		}
	}

	/** The spread in degrees as screen pixels from the crosshair, for the current field of view. */
	private static int pixels(Minecraft mc, GuiGraphicsExtractor g, float degrees) {
		double halfFov = Math.toRadians(mc.options.fov().get() / 2.0);
		double offset = Math.tan(Math.toRadians(degrees)) / Math.tan(halfFov) * (g.guiHeight() / 2.0);
		return Mth.clamp((int) Math.round(offset), MIN_GAP, MAX_GAP);
	}

	private static void tick(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
		g.fill(x - 1, y - 1, x + w + 1, y + h + 1, SHADOW);
		g.fill(x, y, x + w, y + h, color);
	}

	/** Four small corner brackets {@code r} pixels out: the heavy bolt is loaded. */
	private static void brackets(GuiGraphicsExtractor g, int cx, int cy, int r, int color) {
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sy = -1; sy <= 1; sy += 2) {
				int x = cx + sx * r;
				int y = cy + sy * r;
				g.fill(Math.min(x, x - sx * 2), y, Math.max(x, x - sx * 2) + 1, y + 1, color);
				g.fill(x, Math.min(y, y - sy * 2), x + 1, Math.max(y, y - sy * 2) + 1, color);
			}
		}
	}

	// ---------------------------------------------------------------- markers, gauge and pips

	private static void gauge(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;

		if (player == null || mc.level == null || mc.gui.hud.isHidden() || player.isSpectator()) {
			return;
		}

		int cx = g.guiWidth() / 2;
		int cy = g.guiHeight() / 2;
		float partial = deltaTracker.getGameTimeDeltaPartialTick(true);

		if (mc.options.getCameraType().isFirstPerson() && linger > 0) {
			markers(g, cx, cy, partial);
		}

		if (linger > 0 && heat != null && kind != null) {
			float alpha = Math.min(1f, linger / (float) FADE);
			float value = BlasterRules.heatAt(heat, mc.level.getGameTime(), kind, DEFAULTS) / BlasterRules.MAX_HEAT;
			arc(g, cx, cy, value, heat.freeShots(), alpha);
		}

		JetPack.JetView jet = player.getAttached(JetPack.JET);

		if (jet != null && jet.max() > 0 && (!player.onGround() || jet.charges() < jet.max())) {
			int y = cy + Math.round(GAUGE_RADIUS * Mth.sin(GAUGE_HALF_SPAN)) + 5;
			Ui.pips(g, cx + 6, y, jet.max(), jet.charges(), UiColors.AMBER, 0.9f);
		}
	}

	/** The hit X (a kill's is larger and red-orange) and the deflect diamond, fading out. */
	private static void markers(GuiGraphicsExtractor g, int cx, int cy, float partial) {
		float sinceHit = GearEffectsClient.sinceHit() + partial;

		if (sinceHit < HIT_TICKS) {
			boolean kill = GearEffectsClient.hitWasKill();
			float alpha = 1f - sinceHit / HIT_TICKS;
			int color = Ui.a(kill ? KILL : 0xFFFFFFFF, alpha);
			int inner = 3;
			int outer = kill ? 8 : 6;

			for (int d = inner; d <= outer; d++) {
				g.fill(cx - d, cy - d, cx - d + 1, cy - d + 1, color);
				g.fill(cx + d, cy - d, cx + d + 1, cy - d + 1, color);
				g.fill(cx - d, cy + d, cx - d + 1, cy + d + 1, color);
				g.fill(cx + d, cy + d, cx + d + 1, cy + d + 1, color);
			}
		}

		float sinceDeflect = GearEffectsClient.sinceDeflect() + partial;

		if (sinceDeflect < DEFLECT_TICKS) {
			float alpha = 1f - sinceDeflect / DEFLECT_TICKS;
			int color = Ui.a(UiColors.AMBER, alpha);
			int r = 7 + Math.round(sinceDeflect / 4f);

			for (int i = 0; i < r; i++) {
				int j = r - i;
				g.fill(cx + i, cy - j, cx + i + 1, cy - j + 1, color);
				g.fill(cx + j, cy + i, cx + j + 1, cy + i + 1, color);
				g.fill(cx - i, cy + j, cx - i + 1, cy + j + 1, color);
				g.fill(cx - j, cy - i, cx - j + 1, cy - i + 1, color);
			}
		}
	}

	/**
	 * The heat arc: {@code fraction} lit from the bottom; notches at the perfect-vent band, which
	 * glows white while the heat is inside it; red-orange from 90; flashing in an overheat lock.
	 */
	private static void arc(GuiGraphicsExtractor g, int cx, int cy, float fraction, int freeShots, float alpha) {
		float low = DEFAULTS.perfectLow() / BlasterRules.MAX_HEAT;
		float high = DEFAULTS.perfectHigh() / BlasterRules.MAX_HEAT;
		boolean inBand = fraction >= low && fraction <= high;
		int ticks = GearEffectsClient.ticks();
		boolean flashOn = !overheatLock || !Vfx.flashing() || (ticks / 3) % 2 == 0;
		float perfect = Math.max(0f, 1f - GearEffectsClient.sincePerfect() / (float) PERFECT_TICKS);
		int lit = Math.round(fraction * GAUGE_POINTS);

		for (int i = 0; i < GAUGE_POINTS; i++) {
			float f = (i + 0.5f) / GAUGE_POINTS;
			float angle = GAUGE_HALF_SPAN - 2f * GAUGE_HALF_SPAN * f;
			int x = cx + Math.round(GAUGE_RADIUS * Mth.cos(angle));
			int y = cy + Math.round(GAUGE_RADIUS * Mth.sin(angle));
			boolean band = f >= low && f <= high;
			int color;

			if (i < lit && flashOn) {
				color = overheatLock || f >= high ? HOT : band && inBand ? 0xFFFFFFFF : blend(COOL, WARM, Math.min(1f, f / low));
			} else {
				color = band ? 0x70DCF4FF : 0x60202830;
			}

			if (perfect > 0f) {
				color = blend(color, 0xFFFFFFFF, perfect);
			}

			g.fill(x - 1, y - 1, x + 2, y + 1, Ui.a(UiColors.OUTLINE, alpha * 0.55f));
			g.fill(x, y, x + 2, y + 1, Ui.a(color, alpha));
		}

		// The band's two notches just outside the arc.
		for (float edge : new float[] {low, high}) {
			float angle = GAUGE_HALF_SPAN - 2f * GAUGE_HALF_SPAN * edge;
			int x = cx + Math.round((GAUGE_RADIUS + 3f) * Mth.cos(angle));
			int y = cy + Math.round((GAUGE_RADIUS + 3f) * Mth.sin(angle));
			g.fill(x, y, x + 2, y + 1, Ui.a(inBand ? 0xFFFFFFFF : UiColors.HOLO_DIM, alpha));
		}

		// Free shots after a perfect vent: white dots above the arc's top end.
		int topX = cx + Math.round(GAUGE_RADIUS * Mth.cos(-GAUGE_HALF_SPAN));
		int topY = cy + Math.round(GAUGE_RADIUS * Mth.sin(-GAUGE_HALF_SPAN)) - 4;

		for (int i = 0; i < Math.min(freeShots, 5); i++) {
			g.fill(topX + i * 3, topY, topX + i * 3 + 2, topY + 2, Ui.a(0xFFFFFFFF, alpha * 0.9f));
		}
	}

	/** ARGB blend from {@code a} to {@code b}. */
	private static int blend(int a, int b, float t) {
		float u = Mth.clamp(t, 0f, 1f);
		int alpha = Math.round(Mth.lerp(u, (a >>> 24) & 0xFF, (b >>> 24) & 0xFF));
		int red = Math.round(Mth.lerp(u, (a >> 16) & 0xFF, (b >> 16) & 0xFF));
		int green = Math.round(Mth.lerp(u, (a >> 8) & 0xFF, (b >> 8) & 0xFF));
		int blue = Math.round(Mth.lerp(u, a & 0xFF, b & 0xFF));
		return alpha << 24 | red << 16 | green << 8 | blue;
	}

	private BlasterHud() {
	}
}
