package com.oldrepublic.client.enemy;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.config.ClientConfig;
import com.oldrepublic.client.quest.QuestClient;
import com.oldrepublic.client.ui.Ui;
import com.oldrepublic.client.ui.UiAnim;
import com.oldrepublic.client.ui.UiColors;
import com.oldrepublic.enemy.trooper.BarkCategory;
import com.oldrepublic.enemy.trooper.TrooperEntity;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Trooper radio barks on the HUD (spec 7.1, 11.2): one line low in the centre, above the hotbar and
 * the action bar, for 60 ticks, led by a faction-tinted speaker tag ("Republic Sergeant:"). A new
 * bark replaces the line on screen. Range (24 blocks) is checked by the effect handler; the client
 * option {@link ClientConfig#showBarks} hides the line (the radio click still plays).
 *
 * <p>The text is resolved when the bark arrives, so the line stays readable if the speaker dies or
 * unloads while it is shown.
 */
final class BarkHud {
	/** How long a line stays up, fades included. */
	static final int SHOW_TICKS = 60;
	private static final int FADE_IN = 4;
	private static final int FADE_OUT = 12;
	/** Distance of the line's baseline above the bottom of the screen: clear of the hotbar, bars and action bar. */
	private static final int BOTTOM_OFFSET = 86;
	private static final int REPUBLIC_TAG = 0xFF9CCBFF;
	private static final int IMPERIAL_TAG = 0xFFF0606A;

	private record Line(Component tag, Component text, int tint, int start) {
	}

	private static @Nullable Line current;

	static void init() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.OVERLAY_MESSAGE, OldRepublicMod.id("trooper_bark"), BarkHud::extract);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.level == null) {
				current = null;
			}
		});
	}

	/** Shows {@code speaker}'s line {@code line} of {@code category}, replacing any bark on screen. */
	static void show(TrooperEntity speaker, BarkCategory category, int line) {
		if (!ClientConfig.get().showBarks) {
			return;
		}

		String army = speaker.armyKey();
		String role = speaker.role().name().toLowerCase(Locale.ROOT);
		int index = Math.clamp(line, 0, BarkCategory.LINES - 1);
		Component tag = Component.translatable("trooper.oldrepublic.tag." + army + "." + role);
		Component text = Component.translatable("trooper.oldrepublic.bark." + army + "." + category.id() + "." + index);
		current = new Line(Component.translatable("trooper.oldrepublic.bark.format", tag, ""), text,
				speaker.isRepublic() ? REPUBLIC_TAG : IMPERIAL_TAG, QuestClient.ticks());
	}

	/** The line on screen, for client tests; null when none is shown. */
	static @Nullable Component currentText() {
		Line line = current;
		return line != null && QuestClient.ticks() - line.start() <= SHOW_TICKS ? line.text() : null;
	}

	private static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		Line line = current;

		if (line == null || mc.player == null || mc.gui.hud.isHidden() || !ClientConfig.get().showBarks) {
			return;
		}

		float now = UiAnim.time(deltaTracker.getGameTimeDeltaPartialTick(true));
		float alpha = UiAnim.envelope(now, line.start(), SHOW_TICKS, FADE_IN, FADE_OUT);

		if (alpha <= 0f) {
			if (now - line.start() > SHOW_TICKS) {
				current = null;
			}

			return;
		}

		Font font = mc.font;
		int tagWidth = font.width(line.tag());
		int width = tagWidth + font.width(line.text());
		int cx = graphics.guiWidth() / 2;
		int x = cx - width / 2;
		int y = graphics.guiHeight() - BOTTOM_OFFSET;

		// A soft dark well behind the text so it reads over snow and sky, edged by a tinted rule.
		graphics.fill(x - 6, y - 3, x + width + 6, y + font.lineHeight + 2, Ui.a(UiColors.WELL, 0.55f * alpha));
		Ui.fadeLine(graphics, cx, y + font.lineHeight + 2, width / 2 + 10, line.tint(), 0.6f * alpha);
		Ui.text(graphics, font, line.tag(), x, y, line.tint(), alpha);
		Ui.text(graphics, font, line.text(), x + tagWidth, y, UiColors.TEXT, alpha);
	}

	private BarkHud() {
	}
}
