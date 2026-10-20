package com.oldrepublic.client.travel;

import com.oldrepublic.client.ui.Ui;
import com.oldrepublic.client.ui.UiAnim;
import com.oldrepublic.client.ui.UiColors;
import com.oldrepublic.travel.Destination;
import com.oldrepublic.travel.ShipBody;
import com.oldrepublic.travel.ShipRules;
import com.oldrepublic.travel.network.FlightPhasePayload;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * The flight HUD (spec MILESTONE_4.md 11.2): during the take-off and the landing, a cockpit strip at
 * the top of the screen with the destination's name, the phase, the altitude to the pad, the hull
 * bar (out of the class's most, MILESTONE_5.md 4.9) and the fuel cells, and a thin line running through the phase. It slides down at the spool,
 * fades under the hyperspace overlay and is gone once the riders are put off at the Settle.
 */
final class FlightHud {
	private static final int W = 232;
	private static final int H = 34;
	private static final int TOP = 4;
	private static final int SLIDE_TICKS = 8;
	private static final String[] PHASES = {"spool", "climb", "jump", "hyperspace", "descent", "settle"};
	/** Hull below these shows amber and then red (the jump minimum). */
	private static final int HULL_WARN = 40;
	private static final int HULL_LOW = 20;

	/** The HUD element. */
	static void extract(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		FlightPhasePayload flight = ShipClientState.flight();

		if (flight == null || mc.player == null || mc.gui.hud.isHidden() || flight.phase() == ShipRules.PHASE_HYPERSPACE) {
			return;
		}

		float partial = deltaTracker.getGameTimeDeltaPartialTick(true);
		float shown = flight.phase() == ShipRules.PHASE_SPOOL ? UiAnim.easeOutCubic(ShipClientState.phaseTime(partial) / SLIDE_TICKS) : 1f;

		if (flight.phase() == ShipRules.PHASE_SETTLE) {
			shown = 1f - UiAnim.easeOutCubic(Math.max(0f, ShipClientState.phaseTime(partial) - flight.length() + SLIDE_TICKS) / SLIDE_TICKS);
		}

		float alpha = shown * (1f - HyperspaceOverlay.intensity(partial));

		if (alpha <= 0.02f) {
			return;
		}

		draw(g, mc, flight, partial, shown, alpha);
	}

	private static void draw(GuiGraphicsExtractor g, Minecraft mc, FlightPhasePayload flight, float partial, float shown, float alpha) {
		Font font = mc.font;
		float time = UiAnim.time(partial);
		int x = (g.guiWidth() - W) / 2;
		int y = TOP - Math.round((1f - shown) * (H + TOP));

		Ui.plate(g, x, y, W, H, alpha * 0.95f);
		Ui.scanlines(g, x + 2, y + 2, W - 4, H - 4, Ui.a(UiColors.HOLO, 0.04f * alpha), time);

		// Line 1: the destination and the phase.
		Component destination = destinationName(flight.destination());
		g.fill(x + 5, y + 5, x + 7, y + 12, Ui.a(UiColors.HOLO, alpha * UiAnim.flicker(time)));
		Ui.text(g, font, Ui.fit(font, destination.getString(), W - 90), x + 10, y + 5, UiColors.HOLO_BRIGHT, alpha);
		Component phase = Component.translatable("screen.oldrepublic.ship.flight.phase." + PHASES[Mth.clamp(flight.phase(), 0, PHASES.length - 1)]);
		Ui.text(g, font, phase, x + W - 6 - font.width(phase), y + 5, UiColors.AMBER, alpha);

		// Line 2: altitude, hull and fuel.
		int row = y + 18;
		Component altitude = Component.translatable("screen.oldrepublic.ship.flight.altitude", altitude(mc, flight));
		Ui.text(g, font, altitude, x + 6, row, UiColors.TEXT_MUTED, alpha);

		int hull = flight.hull();
		int hullColor = hull < HULL_LOW ? UiColors.DANGER : hull < HULL_WARN ? UiColors.AMBER : UiColors.OK;
		int barX = x + 66;
		Ui.text(g, font, Component.translatable("screen.oldrepublic.ship.flight.hull"), barX, row, UiColors.TEXT_MUTED, alpha);
		int labelW = font.width(Component.translatable("screen.oldrepublic.ship.flight.hull")) + 4;
		Ui.bar(g, barX + labelW, row + 1, 56, 6, hull / (float) maxHull(mc), hullColor, Ui.a(hullColor, 0.6f), 5, alpha);
		Ui.text(g, font, String.valueOf(hull), barX + labelW + 60, row, hullColor, alpha);

		int fuelX = x + W - 6 - flight.tank() * 6;
		Component fuel = Component.translatable("screen.oldrepublic.ship.flight.fuel");
		Ui.text(g, font, fuel, fuelX - font.width(fuel) - 4, row, UiColors.TEXT_MUTED, alpha);
		Ui.pips(g, fuelX, row + 2, flight.tank(), flight.fuel(), UiColors.HOLO, alpha);

		// The phase running out along the bottom edge.
		float progress = ShipClientState.phaseProgress(partial);
		int lineW = Math.round((W - 8) * progress);
		g.fill(x + 4, y + H - 4, x + W - 4, y + H - 3, Ui.a(UiColors.HOLO_DEEP, alpha));
		g.fill(x + 4, y + H - 4, x + 4 + lineW, y + H - 3, Ui.a(UiColors.HOLO, alpha));
	}

	/** The most hull the ship ridden holds (MILESTONE_5.md 4.9: the interceptor's is less), 100 off a ship. */
	private static int maxHull(Minecraft mc) {
		return mc.player.getVehicle() instanceof ShipBody ship ? Math.max(1, ship.maxHull()) : 100;
	}

	/** Whole blocks from the ship (or the rider) down to the pad, never below 0. */
	private static int altitude(Minecraft mc, FlightPhasePayload flight) {
		Entity body = mc.player.getVehicle() != null ? mc.player.getVehicle() : mc.player;
		return Math.max(0, Mth.floor(body.getY()) - flight.padY());
	}

	/** A destination's display name ({@code destination.oldrepublic.<id>}). */
	static Component destinationName(int ordinal) {
		Destination[] all = Destination.values();
		return ordinal >= 0 && ordinal < all.length ? Component.translatable("destination.oldrepublic." + all[ordinal].id())
				: Component.translatable("destination.oldrepublic.unknown");
	}

	private FlightHud() {
	}
}
