package com.oldrepublic.client.quest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * Extra tabs of the Wanderer's Journal (spec section 11.3): features register a {@link Tab} and the
 * journal shows a strip of bookmark tabs above the book, the journal's own pages first. With no
 * visible extra tab the strip is hidden and the journal looks as it always has.
 */
public final class JournalTabs {
	/**
	 * One journal tab.
	 *
	 * @param id unique id, also used to remember the open tab
	 * @param label the bookmark's text
	 * @param order position in the strip, lowest first (the journal's own pages always come first)
	 * @param panel makes the tab's panel; called once each time the journal opens on or switches to the tab
	 * @param visible whether the tab is shown for the player now
	 */
	public record Tab(String id, Component label, int order, Supplier<Panel> panel, Predicate<LocalPlayer> visible) {
	}

	/**
	 * The contents of a tab, drawn on the open book. Coordinates are the book's own (unscaled)
	 * coordinates, for drawing and for the mouse alike; the journal applies the book's position and
	 * scale. The box covers both pages, with the spine in the middle.
	 */
	public interface Panel {
		/** Draws the panel in the box {@code x, y, w, h}; {@code mouseX, mouseY} are in the same coordinates. */
		void render(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mouseX, int mouseY, float partialTick);

		/** A click, with the event's position in book coordinates. Returns true if the panel used it. */
		default boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
			return false;
		}

		/** A scroll, with the position in book coordinates. Returns true if the panel used it. */
		default boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
			return false;
		}
	}

	private static final List<Tab> TABS = new ArrayList<>();

	/** Adds a tab. Call during client initialisation; ids must be unique. */
	public static void register(Tab tab) {
		if (TABS.stream().anyMatch(t -> t.id().equals(tab.id()))) {
			throw new IllegalArgumentException("Duplicate journal tab " + tab.id());
		}

		TABS.add(tab);
		TABS.sort(Comparator.comparingInt(Tab::order));
	}

	/** The registered tabs visible to the player now, in strip order. */
	static List<Tab> visible(LocalPlayer player) {
		return TABS.stream().filter(tab -> tab.visible().test(player)).toList();
	}

	private JournalTabs() {
	}
}
