package com.oldrepublic.client.hud;

import java.util.Locale;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.config.ClientConfig;
import com.oldrepublic.client.quest.QuestClient;
import com.oldrepublic.progression.ForceAttachments;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.quest.QuestStage;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/**
 * The Force panel in the top-left corner: Force Energy meter, Midi-chlorian Resonance, attunement
 * level with its XP bar and the Force Push cooldown. Elements appear only once they mean something:
 * nothing before the Awakening, energy and resonance from it, the attunement bar once XP has been
 * earned, the cooldown once Push is learned. Newly revealed parts fade in.
 */
final class ForceHud {
	private static final Identifier RESONANCE_ICON = OldRepublicMod.id("textures/gui/hud/resonance.png");
	private static final Identifier PUSH_ICON = OldRepublicMod.id("textures/gui/hud/force_push.png");
	private static final int X = 6;
	private static final int Y = 6;
	private static final int BAR_W = 84;
	private static final int FADE_TICKS = 30;

	private static int lastStage = -1;
	private static boolean pushKnown;
	private static boolean attunementShown;
	private static int panelAppear = Integer.MIN_VALUE;
	private static int pushAppear = Integer.MIN_VALUE;
	private static int attunementAppear = Integer.MIN_VALUE;
	private static int cooldownTotal;

	static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();

		if (mc.player == null) {
			return;
		}

		ForceData data = mc.player.getAttached(ForceAttachments.FORCE);
		int now = QuestClient.ticks();
		track(data, now);

		if (data == null || !data.isAwakened() || !ClientConfig.get().showHud || mc.gui.hud.isHidden() || mc.player.isSpectator()) {
			return;
		}

		float partial = deltaTracker.getGameTimeDeltaPartialTick(true);
		float panelAlpha = fade(panelAppear, now, partial);
		Font font = mc.font;
		int slide = (int) ((1f - panelAlpha) * -6f);

		drawEnergy(graphics, font, data, panelAlpha, slide, now + partial);
		drawResonance(graphics, font, data, panelAlpha, slide);

		if (attunementShown) {
			drawAttunement(graphics, font, data, Math.min(panelAlpha, fade(attunementAppear, now, partial)), slide);
		}

		if (pushKnown) {
			drawPushCooldown(graphics, font, data, Math.min(panelAlpha, fade(pushAppear, now, partial)), slide);
		}
	}

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

		boolean push = data.hasAbility("force_push");

		if (push != pushKnown) {
			pushKnown = push;
			pushAppear = first ? now - FADE_TICKS : now;
		}

		boolean attunement = data.xp > 0 || data.level > 1 || data.questStage >= QuestStage.PRACTICE.ordinal();

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

	private static int a(int color, float alpha) {
		return ARGB.multiplyAlpha(color, alpha);
	}

	private static void drawEnergy(GuiGraphicsExtractor g, Font font, ForceData data, float alpha, int slide, float time) {
		int x = X + slide;
		int y = Y;
		float ratio = data.maxEnergy <= 0 ? 0f : Mth.clamp(data.energy / data.maxEnergy, 0f, 1f);
		int fillW = Math.round(BAR_W * ratio);

		// Durasteel frame with darker corner studs.
		g.fill(x - 1, y - 1, x + BAR_W + 3, y + 8, a(0xC0141A20, alpha));
		g.fill(x, y, x + BAR_W + 2, y + 7, a(0xFF3A4048, alpha));
		g.fill(x + 1, y + 1, x + BAR_W + 1, y + 6, a(0xFF101820, alpha));
		g.fill(x - 1, y - 1, x + 1, y + 1, a(0xFF5E6670, alpha));
		g.fill(x + BAR_W + 1, y + 6, x + BAR_W + 3, y + 8, a(0xFF5E6670, alpha));

		if (fillW > 0) {
			g.fillGradient(x + 1, y + 1, x + 1 + fillW, y + 6, a(0xFFDCF1FF, alpha), a(0xFF5E9CCF, alpha));
			// A slow band of light travelling along the charge.
			int band = (int) ((time % 60f) / 60f * (fillW + 10)) - 5;
			int b0 = Math.max(x + 1, x + 1 + band);
			int b1 = Math.min(x + 1 + fillW, x + 1 + band + 5);

			if (b1 > b0) {
				g.fill(b0, y + 1, b1, y + 6, a(0x40FFFFFF, alpha));
			}
		}

		String text = String.format(Locale.ROOT, "%d/%d", (int) Math.floor(data.energy), Math.round(data.maxEnergy));
		g.text(font, text, x + BAR_W + 6, y, a(0xFFBFD8EA, alpha), true);
	}

	private static void drawResonance(GuiGraphicsExtractor g, Font font, ForceData data, float alpha, int slide) {
		int x = X + slide;
		int y = Y + 11;
		// API-CHECK: the last int of this blit overload is the ARGB tint.
		g.blit(RenderPipelines.GUI_TEXTURED, RESONANCE_ICON, x, y, 0f, 0f, 9, 9, 9, 9, a(0xFFFFFFFF, alpha));
		g.text(font, String.format(Locale.ROOT, "%,d", data.resonance), x + 12, y + 1, a(0xFFA9D6F2, alpha), true);
	}

	private static void drawAttunement(GuiGraphicsExtractor g, Font font, ForceData data, float alpha, int slide) {
		int x = X + slide + 52;
		int y = Y + 11;
		g.text(font, Component.translatable("hud.oldrepublic.level", data.level), x, y + 1, a(0xFFE6CF94, alpha), true);

		int barX = X + slide;
		int barY = Y + 22;
		float ratio = data.xpToNext <= 0 ? 1f : Mth.clamp((float) data.xp / data.xpToNext, 0f, 1f);
		g.fill(barX, barY, barX + BAR_W + 2, barY + 2, a(0xA0101418, alpha));
		g.fill(barX, barY, barX + Math.round((BAR_W + 2) * ratio), barY + 2, a(0xFFE3C67A, alpha));
	}

	private static void drawPushCooldown(GuiGraphicsExtractor g, Font font, ForceData data, float alpha, int slide) {
		int x = X + slide;
		int y = Y + 27;
		int remaining = data.cooldown("force_push");

		if (remaining <= 0) {
			cooldownTotal = 0;
		} else if (remaining > cooldownTotal) {
			cooldownTotal = remaining;
		}

		g.fill(x - 1, y - 1, x + 17, y + 17, a(0xC0141A20, alpha));
		g.blit(RenderPipelines.GUI_TEXTURED, PUSH_ICON, x, y, 0f, 0f, 16, 16, 16, 16, a(remaining > 0 ? 0xFF8C8C8C : 0xFFFFFFFF, alpha));

		if (remaining > 0 && cooldownTotal > 0) {
			int h = Math.round(16f * remaining / cooldownTotal);
			g.fill(x, y, x + 16, y + h, a(0x90000000, alpha));
			g.text(font, String.format(Locale.ROOT, "%.1f", remaining / 20f), x + 20, y + 4, a(0xFFD0D0D0, alpha), true);
		}
	}

	static void reset() {
		lastStage = -1;
		pushKnown = false;
		attunementShown = false;
		panelAppear = Integer.MIN_VALUE;
		pushAppear = Integer.MIN_VALUE;
		attunementAppear = Integer.MIN_VALUE;
		cooldownTotal = 0;
	}

	private ForceHud() {
	}
}
