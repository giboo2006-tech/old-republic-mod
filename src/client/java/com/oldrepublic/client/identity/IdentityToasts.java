package com.oldrepublic.client.identity;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.config.ClientConfig;
import com.oldrepublic.client.quest.QuestClient;
import com.oldrepublic.client.ui.Ui;
import com.oldrepublic.client.ui.UiAnim;
import com.oldrepublic.client.ui.UiColors;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Alignment and reputation toasts, top left under the Force HUD cluster. A major shift shows a
 * framed toast for 3 seconds; a small contextual shift shows only a short text line. Toasts stack
 * downward, newest at the bottom, at most {@link #MAX_SHOWN} at once.
 */
final class IdentityToasts {
	private static final int X = 4;
	/**
	 * Top of the stack. The Force cluster's real bottom is package-private in {@code client.hud};
	 * until the HUD exposes it, this clears the energy plate and the first ability socket row.
	 */
	private static final int TOP = 60;
	private static final int TOAST_TICKS = 60;
	private static final int LINE_TICKS = 50;
	private static final int FADE_IN = 6;
	private static final int FADE_OUT = 10;
	private static final int MAX_SHOWN = 4;
	/** Reputation toasts wait this long so the synced data (sent at the end of the tick) is current. */
	private static final int SYNC_DELAY = 3;

	private static final class Toast {
		final Supplier<Component> text;
		final int accent;
		final boolean framed;
		final int start;
		final int length;
		@Nullable Component resolved;

		Toast(Supplier<Component> text, int accent, boolean framed, int start, int length) {
			this.text = text;
			this.accent = accent;
			this.framed = framed;
			this.start = start;
			this.length = length;
		}
	}

	private static final Deque<Toast> TOASTS = new ArrayDeque<>();

	/** A framed toast (a major shift or a reputation change). */
	static void framed(Component text, int accent) {
		add(new Toast(() -> text, accent, true, QuestClient.ticks(), TOAST_TICKS));
	}

	/** A framed toast whose text is built a few ticks later, once the synced data has arrived. */
	static void framedAfterSync(Supplier<Component> text, int accent) {
		add(new Toast(text, accent, true, QuestClient.ticks() + SYNC_DELAY, TOAST_TICKS));
	}

	/** A small unframed line (contextual shifts); skipped when the player turned them off. */
	static void line(Component text, int accent) {
		if (ClientConfig.get().showMinorAlignmentToasts) {
			add(new Toast(() -> text, accent, false, QuestClient.ticks(), LINE_TICKS));
		}
	}

	private static void add(Toast toast) {
		TOASTS.addLast(toast);

		while (TOASTS.size() > MAX_SHOWN) {
			TOASTS.removeFirst();
		}
	}

	static void reset() {
		TOASTS.clear();
	}

	static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();

		if (TOASTS.isEmpty() || mc.player == null || mc.gui.hud.isHidden()) {
			return;
		}

		float partial = deltaTracker.getGameTimeDeltaPartialTick(true);
		float now = QuestClient.ticks() + partial;
		Font font = mc.font;
		int y = TOP;

		for (Iterator<Toast> it = TOASTS.iterator(); it.hasNext();) {
			Toast toast = it.next();

			if (now > toast.start + toast.length) {
				it.remove();
				continue;
			}

			if (now < toast.start) {
				continue;
			}

			if (toast.resolved == null) {
				toast.resolved = toast.text.get();
			}

			float alpha = UiAnim.envelope(now, toast.start, toast.length, FADE_IN, FADE_OUT);
			int slide = Math.round((1f - UiAnim.easeOutCubic(Math.min(1f, (now - toast.start) / FADE_IN))) * -8f);
			y += toast.framed ? drawFramed(graphics, font, toast, X + slide, y, alpha, now) : drawLine(graphics, font, toast, X + slide, y, alpha);
		}
	}

	private static int drawFramed(GuiGraphicsExtractor graphics, Font font, Toast toast, int x, int y, float alpha, float time) {
		int w = font.width(toast.resolved) + 14;
		int h = 16;
		Ui.plate(graphics, x, y, w, h, alpha * 0.95f);
		graphics.fill(x + 3, y + 3, x + 4, y + h - 3, Ui.a(toast.accent, alpha * UiAnim.flicker(time)));
		Ui.text(graphics, font, toast.resolved, x + 8, y + 4, UiColors.TEXT, alpha);
		return h + 2;
	}

	private static int drawLine(GuiGraphicsExtractor graphics, Font font, Toast toast, int x, int y, float alpha) {
		Ui.text(graphics, font, toast.resolved, x + 2, y + 1, toast.accent, alpha * 0.9f);
		return 11;
	}

	private IdentityToasts() {
	}
}
