package com.oldrepublic.client.travel;

import java.util.ArrayDeque;
import java.util.Deque;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.quest.QuestClient;
import com.oldrepublic.client.quest.QuestSounds;
import com.oldrepublic.client.ui.Ui;
import com.oldrepublic.client.ui.UiAnim;
import com.oldrepublic.client.ui.UiColors;
import com.oldrepublic.registry.M4Items;
import com.oldrepublic.registry.ModSounds;
import com.oldrepublic.travel.network.ShipStatusPayload;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * The ship's toasts (spec MILESTONE_4.md 11.2): "Ship repaired" when the hulk stands up and
 * "Destination unlocked" with the destination's name. They slide in at the top right, in the third
 * row (under the set bonus and crafting toasts, so the three never overlap), one at a time.
 */
final class ShipToasts {
	private static final int TOAST_TICKS = 80;
	private static final int SLIDE = 8;
	private static final int HEIGHT = 26;
	private static final int MARGIN = 4;
	/** The third toast row at the top right. */
	private static final int ROW = 2;
	private static final int MAX_QUEUE = 4;

	private record Toast(ItemStack icon, Component title, Component line, int accent) {
	}

	private static final Deque<Toast> QUEUE = new ArrayDeque<>();
	private static @Nullable Toast showing;
	private static int start;

	/** A {@code ship_status} carrying a notice. */
	static void onNotice(ShipStatusPayload payload) {
		switch (payload.notice()) {
			case ShipStatusPayload.NOTICE_REPAIRED -> add(new Toast(new ItemStack(M4Items.POWER_CORE), Component.translatable("screen.oldrepublic.ship.toast.repaired"),
					Component.translatable("ship.oldrepublic.name." + payload.ship().paint()), UiColors.OK));
			case ShipStatusPayload.NOTICE_UNLOCKED -> {
				add(new Toast(new ItemStack(M4Items.NAV_COMPUTER), Component.translatable("screen.oldrepublic.ship.toast.unlocked"),
						FlightHud.destinationName(payload.noticeArg()), UiColors.HOLO));
				Minecraft.getInstance().getSoundManager().play(QuestSounds.ui(ModSounds.BEACON_SET, 1.25f, 0.6f));
			}
			default -> {
			}
		}
	}

	private static void add(Toast toast) {
		if (QUEUE.size() < MAX_QUEUE) {
			QUEUE.add(toast);
		}
	}

	/** The HUD element. */
	static void extract(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();

		if (mc.level == null) {
			return;
		}

		int now = QuestClient.ticks();

		if (showing == null || now - start > TOAST_TICKS) {
			showing = QUEUE.poll();
			start = now;
		}

		if (showing == null || mc.gui.hud.isHidden()) {
			return;
		}

		float time = UiAnim.time(deltaTracker.getGameTimeDeltaPartialTick(true));
		float t = time - start;
		float alpha = UiAnim.envelope(time, start, TOAST_TICKS, 3, SLIDE + 2) * (1f - HyperspaceOverlay.intensity(deltaTracker.getGameTimeDeltaPartialTick(true)));

		if (alpha > 0.02f) {
			draw(g, mc.font, showing, t, alpha);
		}
	}

	private static void draw(GuiGraphicsExtractor g, Font font, Toast toast, float t, float alpha) {
		int w = Math.max(font.width(toast.title()), font.width(toast.line())) + 36;
		float in = UiAnim.easeOutCubic(Mth.clamp(t / SLIDE, 0f, 1f));
		float out = UiAnim.easeOutCubic(Mth.clamp((TOAST_TICKS - t) / SLIDE, 0f, 1f));
		int x = g.guiWidth() - MARGIN - w + Math.round((w + MARGIN + 4) * (1f - Math.min(in, out)));
		int y = MARGIN + ROW * (HEIGHT + 4);

		Ui.plate(g, x, y, w, HEIGHT, alpha);
		g.fill(x + 2, y + 3, x + 4, y + HEIGHT - 3, Ui.a(toast.accent(), alpha));
		Ui.inset(g, x + 6, y + 4, 18, 18, alpha);

		if (alpha > 0.35f) {
			g.item(toast.icon(), x + 7, y + 5);
		}

		Ui.text(g, font, toast.title(), x + 28, y + 4, UiColors.TEXT_MUTED, alpha);
		Ui.text(g, font, toast.line(), x + 28, y + 14, toast.accent(), alpha);
		float left = 1f - Mth.clamp(t / TOAST_TICKS, 0f, 1f);
		g.fill(x + 5, y + HEIGHT - 2, x + 5 + Math.round((w - 10) * left), y + HEIGHT - 1, Ui.a(toast.accent(), alpha * 0.6f));
	}

	/** Forgets everything (disconnect). */
	static void clear() {
		QUEUE.clear();
		showing = null;
	}

	private ShipToasts() {
	}
}
