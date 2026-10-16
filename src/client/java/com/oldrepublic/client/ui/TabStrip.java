package com.oldrepublic.client.ui;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * A row of holo tabs over a lit rule, for screens with several pages (the Force screen's trees and
 * Standing tab). The open tab is a raised glass tab joined to the rule; the others are recessed,
 * brightening on hover. A tab may carry a side tint: a thin coloured cap that marks which side it
 * belongs to without hiding anything. Tabs shrink evenly, with ellipses, when the row is too narrow.
 *
 * <p>The host screen calls {@link #render} and forwards clicks to {@link #mouseClicked}, and plays
 * its own click sound when that returns true.
 */
public final class TabStrip {
	/** Height of the strip including the rule under it. */
	public static final int HEIGHT = 14;
	private static final int PAD = 7;
	private static final int GAP = 2;

	/**
	 * @param tint ARGB side tint of the cap, or 0 for none
	 */
	public record Tab(String id, Component label, int tint) {
		public Tab(String id, Component label) {
			this(id, label, 0);
		}
	}

	private final List<Tab> tabs = new ArrayList<>();
	private int[] bounds = new int[0];
	private int boundsY;
	private int selected;

	public TabStrip(List<Tab> tabs) {
		setTabs(tabs);
	}

	/** Replaces the tabs (for example when one unlocks), keeping the open tab when it still exists. */
	public void setTabs(List<Tab> newTabs) {
		String open = selectedId();
		tabs.clear();
		tabs.addAll(newTabs);
		selected = 0;

		if (open != null) {
			select(open);
		}
	}

	public List<Tab> tabs() {
		return List.copyOf(tabs);
	}

	/** Id of the open tab, or null when there are no tabs. */
	public @Nullable String selectedId() {
		return selected < tabs.size() ? tabs.get(selected).id() : null;
	}

	/** Opens the tab with this id; returns false if there is none. */
	public boolean select(String id) {
		for (int i = 0; i < tabs.size(); i++) {
			if (tabs.get(i).id().equals(id)) {
				selected = i;
				return true;
			}
		}

		return false;
	}

	/** Draws the strip with its top-left corner at x, y and the rule spanning {@code w}. */
	public void render(GuiGraphicsExtractor g, Font font, int x, int y, int w, int mouseX, int mouseY, float partialTick) {
		float time = UiAnim.time(partialTick);
		int n = tabs.size();
		bounds = new int[n * 2];
		boundsY = y;
		int natural = 0;

		for (Tab tab : tabs) {
			natural += font.width(tab.label()) + PAD * 2;
		}

		natural += GAP * Math.max(0, n - 1);
		int budget = n == 0 ? 0 : Math.max(0, w - GAP * (n - 1));
		int tx = x;

		for (int i = 0; i < n; i++) {
			Tab tab = tabs.get(i);
			int tw = font.width(tab.label()) + PAD * 2;

			if (natural > w) {
				tw = budget / n;
			}

			bounds[i * 2] = tx;
			bounds[i * 2 + 1] = tw;
			boolean open = i == selected;
			boolean hover = mouseX >= tx && mouseX < tx + tw && mouseY >= y && mouseY < y + HEIGHT - 1;
			drawTab(g, font, tab, tx, y, tw, open, hover, time);
			tx += tw + GAP;
		}

		// The rule the tabs sit on; the open tab breaks through it.
		g.fill(x, y + HEIGHT - 1, x + w, y + HEIGHT, Ui.a(UiColors.HOLO_DIM, 0.9f));

		if (selected < n) {
			g.fill(bounds[selected * 2] + 1, y + HEIGHT - 1, bounds[selected * 2] + bounds[selected * 2 + 1] - 1, y + HEIGHT, UiColors.STEEL_DARK);
		}
	}

	private static void drawTab(GuiGraphicsExtractor g, Font font, Tab tab, int x, int y, int w, boolean open, boolean hover, float time) {
		int bottom = y + HEIGHT - 1;

		if (open) {
			g.fill(x, y, x + w, bottom, UiColors.OUTLINE);
			g.fill(x + 1, y + 1, x + w - 1, bottom, UiColors.STEEL_DARK);
			g.fill(x + 1, y + 1, x + w - 1, y + 2, Ui.a(UiColors.HOLO, UiAnim.flicker(time)));
			Ui.scanlines(g, x + 1, y + 2, w - 2, bottom - y - 2, Ui.a(UiColors.HOLO, 0.05f), time);
		} else {
			g.fill(x, y + 2, x + w, bottom, Ui.a(UiColors.OUTLINE, 0.8f));
			g.fill(x + 1, y + 3, x + w - 1, bottom, hover ? Ui.a(UiColors.STEEL, 0.9f) : Ui.a(UiColors.STEEL_DARK, 0.9f));
		}

		if (tab.tint() != 0) {
			int capY = open ? y + 2 : y + 3;
			g.fill(x + 1, capY, x + w - 1, capY + 1, Ui.a(tab.tint(), open ? 1f : 0.7f));
		}

		int color = open ? UiColors.HOLO_BRIGHT : hover ? UiColors.TEXT : UiColors.TEXT_MUTED;
		String label = Ui.fit(font, tab.label().getString(), w - 6);
		int textX = x + (w - font.width(label)) / 2;
		g.text(font, label, textX, y + (open ? 4 : 5), color, true);
	}

	/**
	 * Opens the clicked tab. Uses the positions from the last {@link #render}. Returns true when a
	 * different tab was opened.
	 */
	public boolean mouseClicked(MouseButtonEvent event) {
		if (event.button() != 0 || event.y() < boundsY || event.y() >= boundsY + HEIGHT - 1) {
			return false;
		}

		for (int i = 0; i * 2 < bounds.length; i++) {
			int tx = bounds[i * 2];

			if (event.x() >= tx && event.x() < tx + bounds[i * 2 + 1]) {
				if (i == selected) {
					return false;
				}

				selected = i;
				return true;
			}
		}

		return false;
	}
}
