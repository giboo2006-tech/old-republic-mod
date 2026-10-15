package com.oldrepublic.client.hud;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.quest.QuestClient;
import com.oldrepublic.client.quest.QuestSounds;
import com.oldrepublic.client.ui.Ui;
import com.oldrepublic.client.ui.UiAnim;
import com.oldrepublic.client.ui.UiColors;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.quest.QuestItems;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

/**
 * Short moments of feedback. A holo banner above the centre for level-ups (Force blue-white) and
 * Resonance gains (amber), handling the foundation's {@code level_up} and {@code resonance_up}
 * effects: its rules draw outward, a scan line sweeps through and the value fades in. And a toast
 * that slides in under the Force cluster for "Journal updated" / "Codex updated", with a timer line
 * running out along its bottom. Neither covers the crosshair or the hotbar.
 */
public final class FlourishHud {
	private static final int BANNER_TICKS = 70;
	private static final int BANNER_OPEN = 8;
	private static final int TOAST_TICKS = 110;
	private static final int TOAST_IN = 8;
	private static final int TOAST_OUT = 10;
	private static final int FADE = 10;

	private static @Nullable Component bannerTitle;
	private static @Nullable Component bannerValue;
	private static int bannerAccent;
	private static int bannerStart = Integer.MIN_VALUE;
	private static boolean noticeCodex;
	private static int noticeStart = Integer.MIN_VALUE;

	static void onLevelUp(Minecraft client, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		showBanner(Component.translatable("hud.oldrepublic.level_up.title"), Component.translatable("hud.oldrepublic.level_up", (int) payload.intensity()), UiColors.HOLO);
		QuestSounds.ui(ModSounds.MEDITATION_COMPLETE, 1.2f, 0.7f);
	}

	static void onResonanceUp(Minecraft client, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		showBanner(Component.translatable("hud.oldrepublic.resonance_up.title"), Component.translatable("hud.oldrepublic.resonance_up", (int) payload.intensity()), UiColors.AMBER);
		QuestSounds.ui(ModSounds.CRYSTAL_CHIME, 0.8f, 0.8f);
	}

	private static void showBanner(Component title, Component value, int accent) {
		bannerTitle = title;
		bannerValue = value;
		bannerAccent = accent;
		bannerStart = QuestClient.ticks();
	}

	/** Shows the "Journal updated" toast (the sound is played by the quest client). */
	public static void showJournalNotice(boolean codex) {
		noticeCodex = codex;
		noticeStart = QuestClient.ticks();
	}

	public static void reset() {
		bannerStart = Integer.MIN_VALUE;
		noticeStart = Integer.MIN_VALUE;
		bannerTitle = null;
		bannerValue = null;
	}

	static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();

		if (mc.player == null || mc.gui.hud.isHidden()) {
			return;
		}

		float now = UiAnim.time(deltaTracker.getGameTimeDeltaPartialTick(true));
		drawBanner(graphics, mc.font, now);
		drawToast(graphics, mc.font, now);
	}

	// ---------------------------------------------------------------- banner

	private static void drawBanner(GuiGraphicsExtractor g, Font font, float now) {
		float alpha = UiAnim.envelope(now, bannerStart, BANNER_TICKS, 4, FADE);

		if (alpha <= 0.02f || bannerTitle == null || bannerValue == null) {
			return;
		}

		float t = now - bannerStart;
		float open = UiAnim.easeOutCubic(t / BANNER_OPEN);
		float flicker = UiAnim.flicker(now);
		int cx = g.guiWidth() / 2;
		int y = (int) (g.guiHeight() * 0.24f);
		int full = Math.max(font.width(bannerValue) * 3 / 4 + 40, font.width(bannerTitle) / 2 + 34);
		int half = Math.max(6, Math.round(full * open));
		int top = y - 6;
		int bottom = y + 27;

		// Translucent glass band, fading toward its ends.
		int fade = Math.min(24, half / 2);
		g.fill(cx - half + fade, top, cx + half - fade, bottom, Ui.a(0x900A0F15, alpha));

		for (int i = 0; i < fade; i += 2) {
			float k = (float) (i + 1) / fade;
			g.fill(cx - half + i, top, cx - half + i + 2, bottom, Ui.a(0x900A0F15, alpha * k));
			g.fill(cx + half - i - 2, top, cx + half - i, bottom, Ui.a(0x900A0F15, alpha * k));
		}

		// Rules top and bottom with bright end caps.
		Ui.fadeLine(g, cx, top, half, bannerAccent, alpha * 0.8f * flicker);
		Ui.fadeLine(g, cx, bottom - 1, half, bannerAccent, alpha * 0.5f * flicker);
		g.fill(cx - 1, top - 1, cx + 1, top + 2, Ui.a(UiColors.BRASS_LIGHT, alpha));

		// A scan line sweeps down through the band as it opens.
		if (t < BANNER_OPEN + 6) {
			int sy = top + Math.round((bottom - top) * Mth.clamp(t / (BANNER_OPEN + 6), 0f, 1f));
			Ui.fadeLine(g, cx, sy, half, 0xFFFFFFFF, alpha * 0.6f);
		}

		float textAlpha = alpha * Mth.clamp((t - 3) / 6f, 0f, 1f);
		int titleW = font.width(bannerTitle);
		Ui.text(g, font, bannerTitle, cx - titleW / 2, y, 0xFFD8DEE4, textAlpha);

		// Notch-script flourishes either side of the title.
		int glyphW = Math.max(0, half - titleW / 2 - 14);

		if (glyphW > 8 && textAlpha > 0.05f) {
			int color = Ui.a(bannerAccent, textAlpha * 0.4f * flicker);
			Ui.glyphs(g, cx - titleW / 2 - 6 - glyphW, y + 1, glyphW, color, (int) (now / 5f));
			Ui.glyphs(g, cx + titleW / 2 + 6, y + 1, glyphW, color, 64 - (int) (now / 5f));
		}

		g.pose().pushMatrix();
		g.pose().translate(cx, y + 12);
		g.pose().scale(1.5f);
		int valueW = font.width(bannerValue);
		Ui.text(g, font, bannerValue, -valueW / 2, 0, brighten(bannerAccent), textAlpha * flicker);
		g.pose().popMatrix();
	}

	private static int brighten(int accent) {
		return ARGB.srgbLerp(0.45f, accent, 0xFFFFFFFF);
	}

	// ---------------------------------------------------------------- toast

	private static void drawToast(GuiGraphicsExtractor g, Font font, float now) {
		float alpha = UiAnim.envelope(now, noticeStart, TOAST_TICKS, 3, TOAST_OUT);

		if (alpha <= 0.02f) {
			return;
		}

		float t = now - noticeStart;
		Component title = Component.translatable(noticeCodex ? "hud.oldrepublic.codex_updated" : "hud.oldrepublic.journal_updated");
		Component hint = Component.translatable("hud.oldrepublic.journal_hint", QuestClient.journalKey().getTranslatedKeyMessage());
		int w = Math.max(font.width(title), font.width(hint)) + 34;
		int h = 26;
		// Slides in from the left edge, and back out at the end.
		float in = UiAnim.easeOutCubic(t / TOAST_IN);
		float out = UiAnim.easeOutCubic((TOAST_TICKS - t) / TOAST_OUT);
		int x = 4 - Math.round((w + 8) * (1f - Math.min(in, out)));
		int y = ForceHud.bottom() + 4;
		int accent = noticeCodex ? UiColors.HOLO : UiColors.BRASS_LIGHT;

		Ui.plate(g, x, y, w, h, alpha);
		g.fill(x + 2, y + 3, x + 4, y + h - 3, Ui.a(accent, alpha));
		Ui.inset(g, x + 6, y + 4, 18, 18, alpha);

		if (alpha > 0.35f) {
			g.item(new ItemStack(QuestItems.WANDERERS_JOURNAL), x + 7, y + 5);
		}

		Ui.text(g, font, title, x + 28, y + 4, noticeCodex ? UiColors.HOLO_BRIGHT : UiColors.TEXT_WARM, alpha);
		Ui.text(g, font, hint, x + 28, y + 14, UiColors.TEXT_MUTED, alpha);

		// Timer line running out along the bottom edge.
		float left = 1f - Mth.clamp(t / TOAST_TICKS, 0f, 1f);
		int lw = Math.round((w - 10) * left);
		g.fill(x + 5, y + h - 2, x + 5 + lw, y + h - 1, Ui.a(accent, alpha * 0.6f));
	}

	private FlourishHud() {
	}
}
