package com.oldrepublic.client.hud;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.quest.QuestClient;
import com.oldrepublic.client.quest.QuestSounds;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.quest.QuestItems;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

/**
 * Short moments of feedback: a centred banner for level-ups and Resonance gains (handlers for the
 * foundation's {@code level_up} and {@code resonance_up} effects) and a "Journal updated" notice
 * under the Force panel. Both fade in and out and never cover the crosshair or the hotbar.
 */
public final class FlourishHud {
	private static final int BANNER_TICKS = 70;
	private static final int NOTICE_TICKS = 100;
	private static final int FADE = 10;

	private static @Nullable Component bannerTitle;
	private static @Nullable Component bannerValue;
	private static int bannerColor;
	private static int bannerStart = Integer.MIN_VALUE;
	private static boolean noticeCodex;
	private static int noticeStart = Integer.MIN_VALUE;

	static void onLevelUp(Minecraft client, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		showBanner(Component.translatable("hud.oldrepublic.level_up.title"), Component.translatable("hud.oldrepublic.level_up", (int) payload.intensity()), 0xE8D5A0);
		QuestSounds.ui(ModSounds.MEDITATION_COMPLETE, 1.2f, 0.7f);
	}

	static void onResonanceUp(Minecraft client, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		showBanner(Component.translatable("hud.oldrepublic.resonance_up.title"), Component.translatable("hud.oldrepublic.resonance_up", (int) payload.intensity()), 0xA8D8F0);
		QuestSounds.ui(ModSounds.CRYSTAL_CHIME, 0.8f, 0.8f);
	}

	private static void showBanner(Component title, Component value, int rgb) {
		bannerTitle = title;
		bannerValue = value;
		bannerColor = rgb;
		bannerStart = QuestClient.ticks();
	}

	/** Shows the "Journal updated" notice (the sound is played by the quest client). */
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

	private static float envelope(int start, int length, float now) {
		if (start == Integer.MIN_VALUE) {
			return 0f;
		}

		float t = now - start;

		if (t < 0 || t > length) {
			return 0f;
		}

		return Math.min(Mth.clamp(t / FADE, 0f, 1f), Mth.clamp((length - t) / FADE, 0f, 1f));
	}

	static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();

		if (mc.player == null || mc.gui.hud.isHidden()) {
			return;
		}

		float now = QuestClient.ticks() + deltaTracker.getGameTimeDeltaPartialTick(true);
		drawBanner(graphics, mc, envelope(bannerStart, BANNER_TICKS, now));
		drawNotice(graphics, mc, envelope(noticeStart, NOTICE_TICKS, now));
	}

	private static void drawBanner(GuiGraphicsExtractor g, Minecraft mc, float alpha) {
		if (alpha <= 0.02f || bannerTitle == null || bannerValue == null) {
			return;
		}

		int cx = g.guiWidth() / 2;
		int y = (int) (g.guiHeight() * 0.26f);
		int textColor = ARGB.multiplyAlpha(0xFF000000 | bannerColor, alpha);
		int dim = ARGB.multiplyAlpha(0xFF000000 | bannerColor, alpha * 0.55f);
		int half = Math.max(mc.font.width(bannerValue) * 3 / 4 + 24, mc.font.width(bannerTitle) / 2 + 18);

		g.fillGradient(cx - half - 30, y - 4, cx - half, y - 3, 0x00000000, dim);
		g.fillGradient(cx + half, y - 4, cx + half + 30, y - 3, dim, 0x00000000);
		g.fill(cx - half, y - 4, cx + half, y - 3, dim);
		g.centeredText(mc.font, bannerTitle, cx, y, ARGB.multiplyAlpha(0xFFD8D8D8, alpha));

		g.pose().pushMatrix();
		g.pose().translate(cx, y + 12);
		g.pose().scale(1.5f);
		g.centeredText(mc.font, bannerValue, 0, 0, textColor);
		g.pose().popMatrix();
	}

	private static void drawNotice(GuiGraphicsExtractor g, Minecraft mc, float alpha) {
		if (alpha <= 0.02f) {
			return;
		}

		int x = 6 + (int) ((1f - alpha) * -10f);
		int y = 52;
		Component title = Component.translatable(noticeCodex ? "hud.oldrepublic.codex_updated" : "hud.oldrepublic.journal_updated");
		Component hint = Component.translatable("hud.oldrepublic.journal_hint", QuestClient.journalKey().getTranslatedKeyMessage());
		int w = Math.max(mc.font.width(title), mc.font.width(hint)) + 26;

		g.fill(x - 2, y - 2, x + w, y + 20, ARGB.multiplyAlpha(0xB0141A20, alpha));
		g.fill(x - 2, y - 2, x, y + 20, ARGB.multiplyAlpha(0xFFB08A4E, alpha));

		if (alpha > 0.35f) {
			g.item(new ItemStack(QuestItems.WANDERERS_JOURNAL), x + 2, y);
		}

		g.text(mc.font, title, x + 22, y + 1, ARGB.multiplyAlpha(0xFFE8DCC0, alpha), true);
		g.text(mc.font, hint, x + 22, y + 10, ARGB.multiplyAlpha(0xFF9FA8B0, alpha), true);
	}

	private FlourishHud() {
	}
}
