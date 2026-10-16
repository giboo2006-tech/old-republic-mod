package com.oldrepublic.client.hud;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.ability.AbilityInput;
import com.oldrepublic.client.ability.ClientAbilityInfo;
import com.oldrepublic.client.config.ClientConfig;
import com.oldrepublic.client.force.ForceKeys;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.client.quest.QuestClient;
import com.oldrepublic.client.ui.Ui;
import com.oldrepublic.client.ui.UiAnim;
import com.oldrepublic.client.ui.UiColors;
import com.oldrepublic.progression.ForceAttachments;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.quest.QuestStage;
import com.oldrepublic.skill.SkillNode;
import com.oldrepublic.skill.SkillTrees;
import com.oldrepublic.skill.Skills;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/**
 * The Force cluster in the top-left corner (clear of every vanilla element; chat and the player
 * list draw over it): a glass plate holding the attunement emblem with its XP ring (from the
 * Awakening on), the framed
 * Force Energy bar and the Midi-chlorian Resonance readout, and under it a row of up to four
 * ability sockets (18x18), one per slot that holds a power. Each socket shows the power's icon, its
 * key under it, a cooldown sweep with the seconds left, an amber rim when the energy is below the
 * power's cost, a charge or channel arc while it runs, and a cross-hatch while the player's
 * alignment refuses it. While a hold-and-release power charges, two small brackets beside the
 * crosshair fill up; after the cast they drain with its cooldown. Elements appear only once they
 * mean something (nothing before the Awakening, a socket once its slot holds a power) and fade in.
 *
 * <p>Energy is eased toward the synced value and spent energy lingers as a pale "ghost" before it
 * drains away. A cost notch marks the cheapest slotted power; below it the bar turns amber, and
 * pressing a slot key without enough energy flashes the bar.
 */
final class ForceHud {
	private static final Identifier RESONANCE_ICON = OldRepublicMod.id("textures/gui/hud/resonance.png");
	private static final Identifier PUSH_ICON = Ui.sprite("push_icon");
	private static final int SOCKET = 18;
	private static final int SOCKET_GAP = 4;
	private static final int X = 4;
	private static final int Y = 4;
	private static final int BAR_W = 92;
	private static final int BAR_H = 7;
	private static final int EMBLEM = 20;
	private static final int FADE_TICKS = 30;
	private static final int GHOST_HOLD_TICKS = 10;
	private static final int FAIL_TICKS = 16;
	private static final int SHIMMER_TICKS = 40;
	private static final int LEVEL_GLOW_TICKS = 40;
	private static final int READY_GLINT_TICKS = 10;
	/** Energy units per bar segment. */
	private static final float SEGMENT_ENERGY = 10f;

	private static int lastStage = -1;
	private static boolean attunementShown;
	private static int panelAppear = Integer.MIN_VALUE;
	private static int attunementAppear = Integer.MIN_VALUE;
	/** Per slot: the power shown and when it appeared (sockets fade in when a slot fills). */
	private static final String[] SHOWN = {"", "", "", ""};
	private static final int[] SOCKET_APPEAR = new int[4];
	private static boolean slotsSeen;
	/** Per ability: the longest cooldown seen since it was last ready, and when it became ready. */
	private static final Map<String, Integer> COOLDOWN_TOTAL = new HashMap<>();
	private static final Map<String, Integer> READY_AT = new HashMap<>();

	private static float lastTime = Float.NaN;
	private static float shownEnergy = -1f;
	private static float ghostEnergy;
	private static float ghostHoldUntil;
	private static float lastActual = -1f;
	private static int failAt = Integer.MIN_VALUE;
	private static int lastResonance = -1;
	private static int resonanceDelta;
	private static int shimmerAt = Integer.MIN_VALUE;
	private static int lastLevel = -1;
	private static int levelGlowAt = Integer.MIN_VALUE;
	/** Lowest GUI y the cluster used last frame, so notices can stack under it. */
	private static int bottom = Y;

	static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();

		if (mc.player == null) {
			return;
		}

		ForceData data = mc.player.getAttached(ForceAttachments.FORCE);
		int now = QuestClient.ticks();
		track(data, now);

		if (data == null || !data.isAwakened() || !ClientConfig.get().showHud || mc.gui.hud.isHidden() || mc.player.isSpectator()) {
			bottom = Y;
			shownEnergy = -1f;
			return;
		}

		float partial = deltaTracker.getGameTimeDeltaPartialTick(true);
		float time = now + partial;
		float dt = Float.isNaN(lastTime) ? 0f : Mth.clamp(time - lastTime, 0f, 5f);
		lastTime = time;

		float panelAlpha = fade(panelAppear, now, partial);
		float attAlpha = attunementShown ? Math.min(panelAlpha, fade(attunementAppear, now, partial)) : 0f;
		Font font = mc.font;
		int slide = (int) ((1f - panelAlpha) * -6f);
		trackSlots(mc.player, now);
		trackCooldowns(data, now);
		float minCost = cheapestSlottedCost(mc.player, data);
		watchResonance(data, now);
		watchLevel(data, now);

		int barX = X + slide + (attunementShown ? EMBLEM + 7 : 5);
		int barY = Y + 5;
		String energyText = String.format(Locale.ROOT, "%d/%d", (int) Math.floor(data.energy), Math.round(data.maxEnergy));
		String resonanceText = String.format(Locale.ROOT, "%,d", data.resonance);
		// Sized for a full readout so the plate does not twitch as the number changes.
		String widest = Math.round(data.maxEnergy) + "/" + Math.round(data.maxEnergy);
		int plateW = barX - (X + slide) + BAR_W + 5 + Math.max(font.width(energyText), font.width(widest)) + 5;
		int plateH = EMBLEM + 6;
		Ui.plate(graphics, X + slide, Y, plateW, plateH, panelAlpha * 0.95f);

		if (attunementShown) {
			drawEmblem(graphics, font, data, X + slide + 3, Y + 3, attAlpha, time);
		}

		drawEnergy(graphics, font, data, barX, barY, energyText, minCost, panelAlpha, time, dt);
		drawResonance(graphics, font, resonanceText, barX, barY + BAR_H + 4, panelAlpha, time);
		bottom = Y + plateH;

		if (drawSockets(graphics, font, mc.player, data, X + slide, bottom + 3, panelAlpha, partial, time)) {
			bottom += 3 + SOCKET + 11;
		}

		drawCrosshairBrackets(graphics, data, panelAlpha, partial, time);
	}

	/** Notices and toasts start below this GUI y so they never overlap the cluster. */
	static int bottom() {
		return bottom;
	}

	/** Flashes the energy bar as "not enough Force Energy" (also triggered by the power key). */
	static void energyFail() {
		int now = QuestClient.ticks();

		if (now - failAt > 4) {
			failAt = now;
		}
	}

	// ---------------------------------------------------------------- state tracking

	/** Remembers what was visible, so things revealed during play fade in instead of popping. */
	private static void track(ForceData data, int now) {
		if (data == null) {
			return;
		}

		boolean first = lastStage < 0;

		if (data.isAwakened() && (first || lastStage == QuestStage.STIRRING.ordinal()) && panelAppear == Integer.MIN_VALUE) {
			panelAppear = first ? now - FADE_TICKS : now;
		} else if (!data.isAwakened()) {
			panelAppear = Integer.MIN_VALUE;
		}

		boolean attunement = data.isAwakened();

		if (attunement != attunementShown) {
			attunementShown = attunement;
			attunementAppear = first ? now - FADE_TICKS : now;
		}

		lastStage = data.questStage;
	}

	private static float fade(int appear, int now, float partial) {
		if (appear == Integer.MIN_VALUE) {
			return 1f;
		}

		return Mth.clamp((now - appear + partial) / FADE_TICKS, 0f, 1f);
	}

	/** Notes which power each slot holds, so a newly filled socket fades in. */
	private static void trackSlots(LocalPlayer player, int now) {
		// Sockets already filled when the HUD first sees them (joining a world) do not fade in.
		boolean first = !slotsSeen;
		slotsSeen = true;

		for (int i = 0; i < SHOWN.length; i++) {
			String ability = Skills.slot(player, i);

			if (!ability.equals(SHOWN[i])) {
				SOCKET_APPEAR[i] = first || ability.isEmpty() ? now - FADE_TICKS : now;
				SHOWN[i] = ability;
			}
		}
	}

	/** Remembers each cooldown's length (for the sweep) and when it ended (for the ready glint). */
	private static void trackCooldowns(ForceData data, int now) {
		for (String ability : SHOWN) {
			if (ability.isEmpty()) {
				continue;
			}

			int remaining = data.cooldown(ability);
			int total = COOLDOWN_TOTAL.getOrDefault(ability, 0);

			if (remaining <= 0) {
				if (total > 0) {
					READY_AT.put(ability, now);
				}

				COOLDOWN_TOTAL.remove(ability);
			} else if (remaining > total) {
				COOLDOWN_TOTAL.put(ability, remaining);
			}
		}
	}

	/** Energy needed for the cheapest power in a slot, or 0 when no slot holds a power. */
	private static float cheapestSlottedCost(LocalPlayer player, ForceData data) {
		float cheapest = 0f;

		for (String ability : SHOWN) {
			if (ability.isEmpty()) {
				continue;
			}

			float cost = AbilityInput.cost(player, data, ability);

			if (cost > 0f && (cheapest <= 0f || cost < cheapest)) {
				cheapest = cost;
			}
		}

		return cheapest;
	}

	private static void watchResonance(ForceData data, int now) {
		if (lastResonance >= 0 && data.resonance > lastResonance) {
			resonanceDelta = data.resonance - lastResonance;
			shimmerAt = now;
		}

		lastResonance = data.resonance;
	}

	private static void watchLevel(ForceData data, int now) {
		if (lastLevel >= 0 && data.level > lastLevel) {
			levelGlowAt = now;
		}

		lastLevel = data.level;
	}

	// ---------------------------------------------------------------- emblem

	private static void drawEmblem(GuiGraphicsExtractor g, Font font, ForceData data, int x, int y, float alpha, float time) {
		float glow = levelGlowAt == Integer.MIN_VALUE ? 0f : 1f - Mth.clamp((time - levelGlowAt) / LEVEL_GLOW_TICKS, 0f, 1f);
		float xp = data.xpToNext <= 0 ? 1f : Mth.clamp((float) data.xp / data.xpToNext, 0f, 1f);
		Ui.sprite(g, Ui.EMBLEM, x, y, EMBLEM, EMBLEM, alpha);
		// XP fills the ring around the badge, clockwise from the top.
		Ui.ring(g, x - 1, y - 1, EMBLEM + 2, EMBLEM + 2, 7, glow > 0f ? 1f : xp,
				ARGB.srgbLerp(glow, UiColors.BRASS_LIGHT, 0xFFFFF6DC), 0x70101418, alpha);
		String level = Integer.toString(data.level);
		int color = ARGB.srgbLerp(glow, UiColors.BRASS_LIGHT, 0xFFFFFFFF);
		Ui.text(g, font, level, x + (EMBLEM - font.width(level)) / 2 + 1, y + 6, color, alpha);
	}

	// ---------------------------------------------------------------- energy

	private static void drawEnergy(GuiGraphicsExtractor g, Font font, ForceData data, int x, int y, String text, float minCost,
			float alpha, float time, float dt) {
		float max = Math.max(1f, data.maxEnergy);
		float actual = Mth.clamp(data.energy / max, 0f, 1f);

		if (shownEnergy < 0f) {
			shownEnergy = actual;
			ghostEnergy = actual;
			lastActual = actual;
		}

		if (actual < lastActual - 0.004f) {
			// Energy was spent: hold the ghost where it was, then let it drain.
			ghostEnergy = Math.max(ghostEnergy, shownEnergy);
			ghostHoldUntil = time + GHOST_HOLD_TICKS;
		}

		lastActual = actual;
		shownEnergy = UiAnim.approach(shownEnergy, actual, 0.35f, dt);

		if (time > ghostHoldUntil) {
			ghostEnergy = UiAnim.approach(ghostEnergy, shownEnergy, 0.16f, dt);
		}

		ghostEnergy = Math.max(ghostEnergy, shownEnergy);

		boolean low = minCost > 0f && data.energy < minCost;
		// The inner glow brightens as the bar fills; amber when too low to cast.
		int top = low ? ARGB.srgbLerp(shownEnergy, UiColors.AMBER_DIM, UiColors.AMBER_BRIGHT)
				: ARGB.srgbLerp(shownEnergy, 0xFF4E8FBF, 0xFFE4F6FF);
		int bottomColor = low ? ARGB.srgbLerp(shownEnergy, 0xFF4A3010, UiColors.AMBER_DIM)
				: ARGB.srgbLerp(shownEnergy, UiColors.HOLO_DEEP, 0xFF6FB6E6);
		int segments = Math.max(1, Math.round(max / SEGMENT_ENERGY));

		Ui.frame(g, x, y, BAR_W, BAR_H, alpha);
		Ui.span(g, x, y, BAR_W, BAR_H, shownEnergy, ghostEnergy, 0xB0F2E2C0, alpha * Mth.clamp((ghostEnergy - shownEnergy) * 12f, 0f, 1f));
		int fillW = Math.round((BAR_W - 2) * shownEnergy);

		if (fillW > 0) {
			Ui.fillInner(g, x + 1, y + 1, fillW, BAR_H - 2, top, bottomColor, alpha);
			// A slow band of light travelling along the charge.
			int band = (int) ((time % 60f) / 60f * (fillW + 10)) - 5;
			int b0 = Math.max(x + 1, x + 1 + band);
			int b1 = Math.min(x + 1 + fillW, x + 1 + band + 5);

			if (b1 > b0) {
				g.fill(b0, y + 1, b1, y + BAR_H - 1, Ui.a(0x40FFFFFF, alpha));
			}
		}

		Ui.segments(g, x + 1, y + 1, BAR_W - 2, BAR_H - 2, segments, alpha);

		if (minCost > 0f && minCost < max) {
			// Cost notch: the cheapest slotted power. Pulses gently while the bar is below it.
			int nx = x + 1 + Math.round((BAR_W - 2) * (minCost / max));
			float notch = low ? 0.55f + 0.45f * UiAnim.pulse(time, 24f) : 0.55f;
			g.fill(nx, y - 2, nx + 1, y, Ui.a(UiColors.AMBER, alpha * notch));
			g.fill(nx, y + BAR_H, nx + 1, y + BAR_H + 2, Ui.a(UiColors.AMBER, alpha * notch));
		}

		float fail = UiAnim.envelope(time, failAt, Vfx.flashing() ? FAIL_TICKS : FAIL_TICKS + 8, 1, Vfx.flashing() ? FAIL_TICKS - 1 : FAIL_TICKS);

		if (fail > 0f) {
			// One sharp flash (or, with flashing off, a soft steady glow that fades).
			float peak = Vfx.flashing() ? 1f : 0.6f;
			int c = Ui.a(UiColors.DANGER, alpha * fail * peak);
			g.fill(x - 2, y - 2, x + BAR_W + 2, y - 1, c);
			g.fill(x - 2, y + BAR_H + 1, x + BAR_W + 2, y + BAR_H + 2, c);
			g.fill(x - 2, y - 1, x - 1, y + BAR_H + 1, c);
			g.fill(x + BAR_W + 1, y - 1, x + BAR_W + 2, y + BAR_H + 1, c);
			Ui.span(g, x, y, BAR_W, BAR_H, 0f, 1f, 0x60E05A4A, alpha * fail * peak);
		}

		int textColor = low ? UiColors.AMBER : UiColors.TEXT_MUTED;
		Ui.text(g, font, text, x + BAR_W + 5, y, fail > 0f ? ARGB.srgbLerp(fail, textColor, UiColors.DANGER) : textColor, alpha);
	}

	// ---------------------------------------------------------------- resonance

	private static void drawResonance(GuiGraphicsExtractor g, Font font, String text, int x, int y, float alpha, float time) {
		// API-CHECK: the last int of this blit overload is the ARGB tint.
		g.blit(RenderPipelines.GUI_TEXTURED, RESONANCE_ICON, x, y, 0f, 0f, 9, 9, 9, 9, Ui.a(0xFFFFE8C0, alpha));
		float shimmer = shimmerAt == Integer.MIN_VALUE ? 0f : Mth.clamp((time - shimmerAt) / SHIMMER_TICKS, 0f, 1f);
		int tx = x + 12;

		if (shimmer <= 0f || shimmer >= 1f) {
			Ui.text(g, font, text, tx, y + 1, UiColors.RESONANCE, alpha);
			return;
		}

		// An amber glint sweeps across the digits, character by character.
		float sweep = shimmer * (text.length() + 4) - 2;
		int cx = tx;

		for (int i = 0; i < text.length(); i++) {
			String ch = text.substring(i, i + 1);
			float near = Math.max(0f, 1f - Math.abs(i - sweep) / 2.5f);
			int color = ARGB.srgbLerp(Math.min(1f, near + (1f - shimmer) * 0.35f), UiColors.RESONANCE, UiColors.AMBER_BRIGHT);
			Ui.text(g, font, ch, cx, y + 1, color, alpha);
			cx += font.width(ch);
		}

		// The gain floats up beside the number and fades.
		float rise = UiAnim.easeOutCubic(shimmer);
		Ui.text(g, font, "+" + resonanceDelta, cx + 4, y + 1 - Math.round(rise * 3f), UiColors.AMBER, alpha * (1f - shimmer));
	}

	// ---------------------------------------------------------------- ability sockets

	/** Draws the filled slots in slot order; returns false when no slot holds a power. */
	private static boolean drawSockets(GuiGraphicsExtractor g, Font font, LocalPlayer player, ForceData data, int x, int y, float alpha,
			float partial, float time) {
		boolean any = false;
		int open = Skills.slots(player);

		for (int i = 0; i < SHOWN.length; i++) {
			String ability = SHOWN[i];

			if (ability.isEmpty() || i >= open) {
				continue;
			}

			any = true;
			float a = Math.min(alpha, fade(SOCKET_APPEAR[i], QuestClient.ticks(), partial));
			int sx = x + i * (SOCKET + SOCKET_GAP);
			// A new socket drops in from just above as it fades in.
			int sy = y - Math.round((1f - a) * 4f);
			drawSocket(g, font, player, data, i, ability, sx, sy, a, partial, time);
		}

		return any;
	}

	private static void drawSocket(GuiGraphicsExtractor g, Font font, LocalPlayer player, ForceData data, int slot, String ability, int x, int y,
			float alpha, float partial, float time) {
		int remaining = data.cooldown(ability);
		int total = COOLDOWN_TOTAL.getOrDefault(ability, remaining);
		boolean refused = ClientAbilityInfo.refused(player, ability);
		boolean low = !player.isCreative() && remaining <= 0 && data.energy < AbilityInput.cost(player, data, ability);

		Ui.plate(g, x, y, SOCKET, SOCKET, alpha * 0.95f);
		int tint = remaining > 0 || refused ? 0xFF7C8088 : 0xFFFFFFFF;
		Ui.sprite(g, icon(ability), x + 1, y + 1, 16, 16, Ui.a(tint, alpha));

		if (remaining > 0 && total > 0) {
			// Sweep: the dark cover shrinks from the top as the cooldown runs out, and the frame
			// relights clockwise.
			float left = Mth.clamp((float) remaining / total, 0f, 1f);
			int h = Math.round(16f * left);
			g.fill(x + 1, y + 1, x + 17, y + 1 + h, Ui.a(0x90000000, alpha));
			Ui.ring(g, x, y, SOCKET, SOCKET, 3, 1f - left, UiColors.HOLO_DIM, 0, alpha);
			String secs = remaining >= 20 ? Integer.toString((remaining + 19) / 20) : String.format(Locale.ROOT, ".%d", Math.max(1, remaining / 2));
			Ui.text(g, font, secs, x + (SOCKET - font.width(secs)) / 2 + 1, y + 5, UiColors.TEXT, alpha);
		} else {
			int readyAt = READY_AT.getOrDefault(ability, Integer.MIN_VALUE);
			float glint = UiAnim.envelope(time, readyAt, READY_GLINT_TICKS, 1, READY_GLINT_TICKS - 1);

			if (glint > 0f) {
				Ui.ring(g, x, y, SOCKET, SOCKET, 3, 1f, UiColors.HOLO_BRIGHT, 0, alpha * glint);
			}
		}

		if (low && !refused) {
			Ui.ring(g, x, y, SOCKET, SOCKET, 3, 1f, UiColors.AMBER, 0, alpha * (0.6f + 0.4f * UiAnim.pulse(time, 24f)));
		}

		float charge = AbilityInput.charge(slot, partial);
		float channel = AbilityInput.channel(slot, partial, ability);

		if (charge >= 0f || channel >= 0f) {
			// Charge fills the outer arc; a channel drains it as its time runs out.
			float fill = charge >= 0f ? charge : 1f - channel;
			boolean full = charge >= 1f;
			int lit = full ? UiColors.HOLO_BRIGHT : UiColors.HOLO;
			float strength = full ? 0.75f + 0.25f * UiAnim.pulse(time, 10f) : 1f;
			Ui.ring(g, x - 2, y - 2, SOCKET + 4, SOCKET + 4, 4, fill, lit, Ui.a(UiColors.OUTLINE, 0.6f), alpha * strength);
		}

		if (refused) {
			crossHatch(g, x + 1, y + 1, 16, Ui.a(UiColors.DANGER, alpha * 0.7f));
		}

		String key = ForceKeys.SLOTS.get(slot).getTranslatedKeyMessage().getString();
		key = Ui.fit(font, key, SOCKET + SOCKET_GAP - 1);
		Ui.text(g, font, key, x + (SOCKET - font.width(key)) / 2 + 1, y + SOCKET + 2, low ? UiColors.AMBER : UiColors.TEXT_DIM, alpha);
	}

	/** The power's node icon from the synced trees (Push falls back to the Milestone 1 icon). */
	private static Identifier icon(String ability) {
		SkillNode node = SkillTrees.client().abilityNode(ability);
		return node != null ? node.icon() : PUSH_ICON;
	}

	/** Thin diagonal hatching over a square: the power is refused by the player's alignment. */
	private static void crossHatch(GuiGraphicsExtractor g, int x, int y, int size, int color) {
		for (int i = 0; i < size; i++) {
			for (int d = 0; d < size * 2; d += 5) {
				int px = d - i;

				if (px >= 0 && px < size) {
					g.fill(x + px, y + i, x + px + 1, y + i + 1, color);
				}
			}
		}
	}

	/**
	 * Two small bowed brackets 10 to 15 px either side of the crosshair: they fill bottom-up with the
	 * charge of a held hold-and-release power, glow when it is full, and after the cast drain with
	 * that power's cooldown. They never touch the 15 px crosshair itself.
	 */
	private static void drawCrosshairBrackets(GuiGraphicsExtractor g, ForceData data, float alpha, float partial, float time) {
		int slot = AbilityInput.activeSlot();
		float charge = slot >= 0 ? AbilityInput.charge(slot, partial) : -1f;
		String last = AbilityInput.lastCharged();
		int remaining = last.isEmpty() ? 0 : data.cooldown(last);
		int total = COOLDOWN_TOTAL.getOrDefault(last, 0);
		float fill;
		int lit;
		float strength;

		if (charge >= 0f) {
			fill = charge;
			boolean full = fill >= 1f;
			lit = full ? UiColors.HOLO_BRIGHT : UiColors.HOLO;
			strength = full ? 0.75f + 0.25f * UiAnim.pulse(time, 10f) : 0.85f;
		} else if (remaining > 0 && total > 0) {
			fill = (float) remaining / total;
			lit = UiColors.TEXT_DIM;
			strength = 0.55f;
		} else {
			return;
		}

		int cx = g.guiWidth() / 2;
		int cy = g.guiHeight() / 2;
		int ticks = 5;
		int on = Math.round(ticks * Mth.clamp(fill, 0f, 1f));

		for (int i = 0; i < ticks; i++) {
			// i = 0 is the bottom tick; brackets bow outward like ") (" mirrored.
			int dy = (2 - i) * 3;
			int bow = (i == 2) ? 1 : (i == 1 || i == 3) ? 0 : -1;
			boolean onTick = i < on;
			int color = onTick ? lit : 0x50000000;
			float a = alpha * (onTick ? strength : 0.6f);
			int lx = cx - 13 - bow;
			int rx = cx + 12 + bow;
			g.fill(lx, cy + dy - 1, lx + 2, cy + dy + 1, Ui.a(color, a));
			g.fill(rx, cy + dy - 1, rx + 2, cy + dy + 1, Ui.a(color, a));
		}
	}

	static void reset() {
		lastStage = -1;
		attunementShown = false;
		panelAppear = Integer.MIN_VALUE;
		attunementAppear = Integer.MIN_VALUE;

		for (int i = 0; i < SHOWN.length; i++) {
			SHOWN[i] = "";
			SOCKET_APPEAR[i] = 0;
		}

		slotsSeen = false;
		COOLDOWN_TOTAL.clear();
		READY_AT.clear();
		lastTime = Float.NaN;
		shownEnergy = -1f;
		failAt = Integer.MIN_VALUE;
		lastResonance = -1;
		shimmerAt = Integer.MIN_VALUE;
		lastLevel = -1;
		levelGlowAt = Integer.MIN_VALUE;
		bottom = Y;
	}

	private ForceHud() {
	}
}
