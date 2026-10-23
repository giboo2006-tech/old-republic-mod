package com.oldrepublic.client.travel;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.quest.QuestClient;
import com.oldrepublic.client.ui.Ui;
import com.oldrepublic.client.ui.UiAnim;
import com.oldrepublic.client.ui.UiColors;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.travel.FlightModel;
import com.oldrepublic.travel.FlightRules;
import com.oldrepublic.travel.ShipBody;
import com.oldrepublic.travel.network.FlightStatusPayload;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The helm HUD (spec MILESTONE_5.md 4.11, 13.2): while the local player holds their ship's helm, a
 * cockpit strip at the top of the screen, where the Milestone 4 flight HUD sits during an autopilot
 * run: the speed and a throttle line under it, the boost meter, the hull bar (out of the class's
 * full hull), the altitude in a sky or "Orbit: &lt;planet&gt;" in an orbit, and what the helm is doing
 * while the server flies the ship (the take-off, a landing, the tumble, a hold). Under it, one line
 * of guidance: the warning near the orbit's edge (a slow pulse; steady with flashing effects off),
 * "Y: Land" when the spot below passes the landing check (once the ship is low, why it does not),
 * and the orbit cue as the ship nears the ceiling of a sky. W2's shield bar, combat panel and scan
 * meter sit beside and under it (5.10).
 */
final class HelmHud {
	private static final int W = 232;
	private static final int H = 34;
	private static final int TOP = 4;
	/** The guidance line sits below W2's scan meter, which hangs under the strip (5.9). */
	private static final int HINT_Y = 62;
	private static final int SLIDE_TICKS = 8;
	/** The orbit cue shows this many blocks below the ceiling. */
	private static final int CUE_BAND = 48;
	/** Hull below these shows amber and then red (the jump minimum). */
	private static final int HULL_WARN = 40;
	private static final int HULL_LOW = 20;
	private static final int BOOST_W = 48;
	/** Narrow enough that "Orbit: the home world" fits left of the hull label. */
	private static final int HULL_W = 40;
	/** Blocks per tick to blocks per second. */
	private static final float TICKS_PER_SECOND = 20f;

	/** The client tick the strip appeared (it slides down), or {@link Integer#MIN_VALUE} while hidden. */
	private static int since = Integer.MIN_VALUE;

	/** The HUD element. */
	static void extract(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		FlightStatusPayload status = FlightClient.status();

		if (status == null || mc.player == null || !(mc.player.getVehicle() instanceof ShipBody ship)) {
			since = Integer.MIN_VALUE;
			return;
		}

		if (mc.gui.hud.isHidden()) {
			return;
		}

		float partial = deltaTracker.getGameTimeDeltaPartialTick(true);
		float time = UiAnim.time(partial);

		if (since == Integer.MIN_VALUE) {
			since = QuestClient.ticks();
		}

		float shown = UiAnim.easeOutCubic((time - since) / SLIDE_TICKS);
		float alpha = shown * (1f - HyperspaceOverlay.intensity(partial));

		if (alpha <= 0.02f) {
			return;
		}

		strip(g, mc.font, status, ship, time, shown, alpha);
		hint(g, mc.font, status, ship, time, alpha);
	}

	// ---------------------------------------------------------------- the strip

	private static void strip(GuiGraphicsExtractor g, Font font, FlightStatusPayload status, ShipBody ship, float time, float shown, float alpha) {
		int x = (g.guiWidth() - W) / 2;
		int y = TOP - Math.round((1f - shown) * (H + TOP));
		boolean tumbling = status.helm() == FlightStatusPayload.HELM_TUMBLE;

		Ui.plate(g, x, y, W, H, alpha * 0.95f);
		Ui.scanlines(g, x + 2, y + 2, W - 4, H - 4, Ui.a(tumbling ? UiColors.DANGER : UiColors.HOLO, 0.04f * alpha), time);

		// Line 1: what the helm is doing (the speed while flown), and the boost meter.
		int row = y + 5;
		g.fill(x + 5, row, x + 7, row + 7, Ui.a(tumbling ? UiColors.DANGER : UiColors.HOLO, alpha * UiAnim.flicker(time)));
		float speed = (float) speed(ship);
		Component stage = stage(status.helm(), speed);
		int stageColor = tumbling ? UiColors.DANGER : status.helm() == FlightStatusPayload.HELM_PILOTED ? UiColors.HOLO_BRIGHT : UiColors.AMBER;
		float stageAlpha = tumbling ? alpha * (0.6f + 0.4f * UiAnim.pulse(time, 16f)) : alpha;
		Ui.text(g, font, stage, x + 10, row, stageColor, stageAlpha);

		int boostX = x + W - 6 - BOOST_W;
		Component boostLabel = Component.translatable("hud.oldrepublic.flight.boost");
		Ui.text(g, font, boostLabel, boostX - font.width(boostLabel) - 4, row, UiColors.TEXT_MUTED, alpha);
		int boostTop = status.boosting() ? UiColors.AMBER_BRIGHT : status.boost() >= 0.999f ? UiColors.HOLO : UiColors.HOLO_DIM;
		int boostBottom = status.boosting() ? UiColors.AMBER : UiColors.HOLO_DEEP;
		Ui.bar(g, boostX, row + 1, BOOST_W, 6, status.boost(), boostTop, boostBottom, 4, alpha);

		// Line 2: the altitude or the orbit, cut short of the hull label, and the hull.
		row = y + 18;
		int hull = ship.hull();
		int max = Math.max(1, ship.maxHull());
		int hullColor = hull < HULL_LOW ? UiColors.DANGER : hull < HULL_WARN ? UiColors.AMBER : UiColors.OK;
		String value = hull + "/" + max;
		int valueX = x + W - 6 - font.width(value);
		int barX = valueX - 4 - HULL_W;
		Component hullLabel = Component.translatable("hud.oldrepublic.flight.hull");
		int labelX = barX - font.width(hullLabel) - 4;
		Ui.text(g, font, Ui.fit(font, place(ship).getString(), labelX - 4 - (x + 6)), x + 6, row, UiColors.TEXT_MUTED, alpha);
		Ui.text(g, font, hullLabel, labelX, row, UiColors.TEXT_MUTED, alpha);
		Ui.bar(g, barX, row + 1, HULL_W, 6, hull / (float) max, hullColor, Ui.a(hullColor, 0.6f), 5, alpha);
		Ui.text(g, font, value, valueX, row, hullColor, alpha);

		// The throttle along the bottom edge: the speed against the top speed (boosted while the boost runs).
		FlightModel.Profile profile = status.profile();
		float top = profile.top() * (status.boosting() ? profile.boost() : 1f);
		float throttle = top > 0f ? Mth.clamp(speed / top, 0f, 1f) : 0f;
		int lineW = Math.round((W - 8) * throttle);
		g.fill(x + 4, y + H - 4, x + W - 4, y + H - 3, Ui.a(UiColors.HOLO_DEEP, alpha));
		g.fill(x + 4, y + H - 4, x + 4 + lineW, y + H - 3, Ui.a(status.boosting() ? UiColors.AMBER : UiColors.HOLO, alpha));
	}

	/** Line 1's words: the speed while the pilot flies, else the step the server is flying. */
	private static Component stage(int helm, float speed) {
		return switch (helm) {
			case FlightStatusPayload.HELM_SPOOL -> Component.translatable("hud.oldrepublic.flight.stage.spool");
			case FlightStatusPayload.HELM_RISE -> Component.translatable("hud.oldrepublic.flight.stage.rise");
			case FlightStatusPayload.HELM_LANDING -> Component.translatable("hud.oldrepublic.flight.stage.landing");
			case FlightStatusPayload.HELM_TUMBLE -> Component.translatable("hud.oldrepublic.flight.stage.tumble");
			case FlightStatusPayload.HELM_HOVER -> Component.translatable("hud.oldrepublic.flight.stage.hover");
			default -> Component.translatable("hud.oldrepublic.flight.speed", Math.round(speed * TICKS_PER_SECOND));
		};
	}

	/** The altitude in a sky; the planet below in an orbit. */
	private static Component place(ShipBody ship) {
		Planets.Planet orbit = Planets.orbitOf(ship.level().dimension());

		if (orbit != null) {
			return Component.translatable("hud.oldrepublic.flight.orbit", Component.translatable("ship.oldrepublic.planet." + orbit.id()));
		}

		return Component.translatable("hud.oldrepublic.flight.altitude", Mth.floor(ship.getY()));
	}

	// ---------------------------------------------------------------- the guidance line

	/** One line under the strip: the edge warning first, then the landing hint, then the orbit cue. */
	private static void hint(GuiGraphicsExtractor g, Font font, FlightStatusPayload status, ShipBody ship, float time, float alpha) {
		Component line = null;
		int color = UiColors.HOLO;
		float lineAlpha = alpha;

		if (status.edge() >= 0) {
			line = Component.translatable("hud.oldrepublic.flight.edge", status.edge());
			color = UiColors.DANGER;
			lineAlpha = alpha * (0.55f + 0.45f * UiAnim.pulse(time, 24f));
		} else if (status.helm() == FlightStatusPayload.HELM_PILOTED) {
			line = landing(FlightRules.Landing.of(status.landing()));

			if (line != null) {
				color = FlightRules.Landing.of(status.landing()).allowed() ? UiColors.OK : UiColors.TEXT_MUTED;
			} else if (cue(status, ship)) {
				line = Component.translatable("hud.oldrepublic.flight.cue", Math.max(0, status.ceiling() - Mth.floor(ship.getY())));
			}
		}

		if (line == null) {
			return;
		}

		int w = font.width(line) + 12;
		int x = (g.guiWidth() - w) / 2;
		Ui.plate(g, x, HINT_Y, w, 14, alpha * 0.85f);
		Ui.text(g, font, line, x + 6, HINT_Y + 3, color, lineAlpha);
	}

	/** The landing hint: "Y: Land" (or onto a hub's bay) when the spot passes; once the ship is low, why it does not; else nothing. */
	private static @Nullable Component landing(FlightRules.Landing landing) {
		return switch (landing) {
			case OK -> Component.translatable("hud.oldrepublic.flight.hint.land", FlightClient.HELM.getTranslatedKeyMessage());
			case HUB -> Component.translatable("hud.oldrepublic.flight.hint.land_bay", FlightClient.HELM.getTranslatedKeyMessage());
			case TOO_HIGH -> null;
			default -> Component.translatable(landing.langKey());
		};
	}

	/** The orbit cue: a sky with an orbit above, the ship within {@link #CUE_BAND} of its ceiling, and no transition cooling down. */
	private static boolean cue(FlightStatusPayload status, ShipBody ship) {
		int below = status.ceiling() - Mth.floor(ship.getY());
		return status.ceiling() >= 0 && status.cooldown() <= 0 && below >= 0 && below <= CUE_BAND;
	}

	/** How far the ship moved over the last tick, in blocks per tick. */
	private static double speed(ShipBody ship) {
		double dx = ship.getX() - ship.xo;
		double dy = ship.getY() - ship.yo;
		double dz = ship.getZ() - ship.zo;
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	private HelmHud() {
	}
}
