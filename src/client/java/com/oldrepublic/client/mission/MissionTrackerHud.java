package com.oldrepublic.client.mission;

import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.config.ClientConfig;
import com.oldrepublic.client.ui.Ui;
import com.oldrepublic.client.ui.UiAnim;
import com.oldrepublic.client.ui.UiColors;
import com.oldrepublic.mission.MissionData;
import com.oldrepublic.mission.MissionRules;
import com.oldrepublic.mission.MissionService;
import com.oldrepublic.mission.network.TrackerHintPayload;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * The mission tracker (spec 5.7, 11.2): top right, under the vanilla status effect icons, at most
 * {@value #MAX_W} px wide. It shows the active mission's title, its objective with the counter
 * ("2/3"), a chevron turned toward the target and the distance band: Far (over
 * {@code missions.trackerNear}), Near, or Here (under {@code missions.trackerHere}); from 100 blocks
 * out the rough distance ("~550m", rounded to 50) takes the band's place (docs/MILESTONE_6.md 3.3),
 * also while a far camp is still being placed. Direction, band, distance and counter come from the
 * server's {@code tracker_hint} every {@code missions.trackerHintTicks}; coordinates are never shown. Hidden while a screen is open, when the HUD is hidden, when
 * {@code showHud} is off, or with {@code /oldrepublicfx tracker off}.
 *
 * <p>A mission update flashes the frame (an accept also slides the tracker in); with flashing
 * effects off the flash is a steady glow that fades.
 */
final class MissionTrackerHud {
	static final int MAX_W = 160;
	private static final int MIN_W = 96;
	private static final int PAD = 4;
	private static final int CHEVRON_BOX = 14;
	private static final int LINE_H = 10;
	private static final int OBJECTIVE_LINES = 2;
	private static final int RIGHT_MARGIN = 2;
	/** Vanilla draws beneficial effect icons in a row at the top and harmful ones in a second row, 25 px apart. */
	private static final int EFFECT_ROW = 25;
	private static final int SLIDE_TICKS = 8;

	/** The chevron's shown angle (degrees), eased toward the hinted one so it turns smoothly. */
	private static float shownAngle;
	private static float lastTime = Float.NaN;

	static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;

		if (player == null || mc.gui.screen() != null || mc.gui.hud.isHidden() || !ClientConfig.get().showHud || !ClientConfig.get().showMissionTracker) {
			return;
		}

		MissionData data = MissionService.view(player);
		MissionData.Active active = data == null ? null : data.active;

		if (active == null) {
			return;
		}

		float partial = deltaTracker.getGameTimeDeltaPartialTick(true);
		float now = UiAnim.time(partial);
		draw(graphics, mc.font, player, active, ClientMissionState.freshHint(), now, partial);
	}

	private static void draw(GuiGraphicsExtractor g, Font font, LocalPlayer player, MissionData.Active active, @Nullable TrackerHintPayload hint,
			float now, float partial) {
		MissionRules.Band band = hint == null ? MissionRules.Band.NONE : hint.bandValue();
		Component title = Component.translatable("mission.oldrepublic." + active.id + ".title");
		Component objective = Component.translatable(MissionRules.objectiveKey(active.base, active.stage));
		String counter = counter(active, hint);
		Component bandLabel = bandLabel(active, band, hint);

		int counterW = counter.isEmpty() ? 0 : font.width(counter) + 4;
		int bandW = bandLabel == null ? 0 : font.width(bandLabel) + 4;
		int textX = PAD + CHEVRON_BOX + 3;
		int natural = textX + Math.max(font.width(title) + bandW, font.width(objective) + counterW) + PAD;
		int w = Mth.clamp(natural, MIN_W, MAX_W);
		int textW = w - textX - PAD;
		List<String> lines = Ui.wrap(font, objective.getString(), Math.max(20, textW - counterW), OBJECTIVE_LINES);
		int h = PAD + LINE_H + lines.size() * LINE_H + PAD - 1;

		float slide = ClientMissionState.lastWasAccept() ? UiAnim.easeOutCubic(Mth.clamp(ClientMissionState.sinceUpdate(now) / SLIDE_TICKS, 0f, 1f)) : 1f;
		int x = g.guiWidth() - w - RIGHT_MARGIN + Math.round((1f - slide) * (w + RIGHT_MARGIN));
		int y = top(player);
		float flash = ClientMissionState.flash(now);

		Ui.plate(g, x, y, w, h, 0.92f);

		if (flash > 0.02f) {
			int glow = Ui.a(UiColors.HOLO, flash * UiAnim.flicker(now));
			g.fill(x + 1, y, x + w - 1, y + 1, glow);
			g.fill(x + 1, y + h - 1, x + w - 1, y + h, glow);
			g.fill(x, y + 1, x + 1, y + h - 1, glow);
			g.fill(x + w - 1, y + 1, x + w, y + h - 1, glow);
		}

		// Title row: the mission's name and the band at the right.
		int tx = x + textX;
		Ui.text(g, font, Ui.fit(font, title.getString(), textW - bandW), tx, y + PAD, UiColors.TEXT_WARM, 1f);

		if (bandLabel != null) {
			Ui.text(g, font, bandLabel, x + w - PAD - font.width(bandLabel), y + PAD, bandColor(band, now), 1f);
		}

		// Objective rows, the counter at the right of the first.
		int oy = y + PAD + LINE_H;

		for (String line : lines) {
			Ui.text(g, font, line, tx, oy, UiColors.TEXT_MUTED, 1f);
			oy += LINE_H;
		}

		if (!counter.isEmpty()) {
			Ui.text(g, font, counter, x + w - PAD - font.width(counter), y + PAD + LINE_H, flash > 0.02f ? UiColors.HOLO_BRIGHT : UiColors.HOLO, 1f);
		}

		drawPointer(g, x + PAD, y + (h - CHEVRON_BOX) / 2, player, hint, band, now, partial);
	}

	/** The top of the tracker: under the status effect icons when any are shown. */
	private static int top(LocalPlayer player) {
		boolean icons = false;

		for (MobEffectInstance effect : player.getActiveEffects()) {
			if (effect.showIcon()) {
				icons = true;
				break;
			}
		}

		// Whether an icon is beneficial (top row) or harmful (second row) is not read here, so any icon
		// moves the tracker below both rows.
		return icons ? 2 + EFFECT_ROW * 2 + 1 : 2;
	}

	/** "2/3" while the objective counts several things at the site; empty otherwise. */
	private static String counter(MissionData.Active active, @Nullable TrackerHintPayload hint) {
		if (active.stage != MissionData.Stage.ACTIVE && active.stage != MissionData.Stage.DECIDE) {
			return "";
		}

		int progress = hint != null ? hint.progress() : active.progress;
		int goal = hint != null ? hint.target() : active.target;
		return goal > 1 ? progress + "/" + goal : "";
	}

	/**
	 * The rough distance ("~550m") from 100 blocks out, else the band's word; nothing while the site is
	 * still being found and no far target is chosen yet.
	 */
	static @Nullable Component bandLabel(MissionData.Active active, MissionRules.Band band, @Nullable TrackerHintPayload hint) {
		if (hint != null && band != MissionRules.Band.NONE && hint.distance() > 0) {
			return Component.translatable("mission.oldrepublic.tracker.distance", hint.distance());
		}

		if (active.stage == MissionData.Stage.PLACING) {
			return null;
		}

		return Component.translatable("mission.oldrepublic.tracker.band." + band.name().toLowerCase(Locale.ROOT));
	}

	private static int bandColor(MissionRules.Band band, float now) {
		return switch (band) {
			case FAR -> UiColors.HOLO_DIM;
			case NEAR -> UiColors.HOLO;
			case HERE -> Ui.a(UiColors.AMBER_BRIGHT, 0.75f + 0.25f * UiAnim.pulse(now, 30f));
			case NONE -> UiColors.TEXT_DIM;
		};
	}

	/**
	 * The chevron in its recessed box, turned toward the target relative to where the player looks
	 * (up is straight ahead). Without a direction (no target in this dimension, or the site is still
	 * being found) the box holds a dim dot.
	 */
	private static void drawPointer(GuiGraphicsExtractor g, int bx, int by, LocalPlayer player, @Nullable TrackerHintPayload hint, MissionRules.Band band,
			float now, float partial) {
		Ui.inset(g, bx, by, CHEVRON_BOX, CHEVRON_BOX, 0.9f);
		int cx = bx + CHEVRON_BOX / 2;
		int cy = by + CHEVRON_BOX / 2;

		if (hint == null || band == MissionRules.Band.NONE) {
			g.fill(cx - 1, cy - 1, cx + 1, cy + 1, Ui.a(UiColors.TEXT_DIM, 0.8f));
			lastTime = Float.NaN;
			return;
		}

		float wanted = Mth.wrapDegrees(hint.yaw() - player.getViewYRot(partial));
		float dt = Float.isNaN(lastTime) ? 100f : Math.min(5f, Math.max(0f, now - lastTime));
		lastTime = now;
		shownAngle = Mth.wrapDegrees(shownAngle + Mth.wrapDegrees(wanted - shownAngle) * (1f - (float) Math.pow(0.55f, dt)));
		int color = band == MissionRules.Band.HERE ? Ui.a(UiColors.AMBER_BRIGHT, 0.7f + 0.3f * UiAnim.pulse(now, 30f))
				: band == MissionRules.Band.NEAR ? UiColors.HOLO_BRIGHT : UiColors.HOLO;

		g.pose().pushMatrix();
		g.pose().rotateAbout(shownAngle * Mth.DEG_TO_RAD, cx, cy);

		// A "^" of two 2-px strokes, its point 4 px above the centre.
		for (int i = 0; i < 4; i++) {
			int y = cy - 4 + i;
			g.fill(cx - i - 1, y, cx - i + 1, y + 2, color);
			g.fill(cx + i - 1, y, cx + i + 1, y + 2, color);
		}

		g.pose().popMatrix();
	}

	static void reset() {
		shownAngle = 0f;
		lastTime = Float.NaN;
	}

	private MissionTrackerHud() {
	}
}
