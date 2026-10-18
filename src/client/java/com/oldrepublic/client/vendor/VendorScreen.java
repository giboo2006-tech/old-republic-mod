package com.oldrepublic.client.vendor;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.dialogue.DialogueClient;
import com.oldrepublic.client.ui.HoloButton;
import com.oldrepublic.client.ui.Ui;
import com.oldrepublic.client.ui.UiAnim;
import com.oldrepublic.client.ui.UiColors;
import com.oldrepublic.economy.Credits;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.vendor.VendorBalance;
import com.oldrepublic.vendor.network.VendorBuyPayload;
import com.oldrepublic.vendor.network.VendorStockPayload;
import com.oldrepublic.vendor.network.VendorStockPayload.Line;
import com.oldrepublic.vendor.network.VendorStockPayload.Sell;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

/**
 * The vendor screen (spec MILESTONE_4.md 7.8), opened and refreshed by the server's
 * {@code vendor_stock} payload. A Buy tab lists the stock: the item, its price for this player
 * (with the Honoured discount when it applies), the purchases left today, and for a locked line
 * the reason in amber ("Requires Trusted with the Watch", "Requires Armstech 3", "Sold out
 * today"). A Sell tab lists what the vendor would take from the inventory and at what price.
 * The footer holds the quantity, the Buy or Sell button with its total, the chit and bundle
 * exchange, and the player's credits are in the header. The game is not paused.
 *
 * <p>The screen only sends intents ({@code vendor_buy}); the server checks reach, the gate, the
 * day's limit, the price, credits and space, then sends the stock again, which refreshes this
 * screen in place. It closes itself when the vendor is gone or out of reach.
 */
public final class VendorScreen extends Screen {
	private static final int MAX_W = 360;
	private static final int MAX_H = 240;
	private static final int PAD = 8;
	private static final int HEADER_H = 14;
	private static final int TAB_H = 14;
	private static final int TAB_W = 64;
	private static final int ROW_H = 20;
	private static final int BUTTON_H = 16;
	private static final int SMALL_W = 16;
	private static final int FOOTER_H = BUTTON_H * 2 + 6;
	private static final int SCROLL_W = 3;
	/** The client reads no balance file: the default purchase cap, only to clamp the counter (the server clamps again). */
	private static final int MAX_PURCHASE = new VendorBalance().maxPurchase;
	/** Close when the vendor is this far (the server's reach is 8). */
	private static final double CLOSE_DISTANCE = 10;
	private static final int REPUBLIC = 0xFF8FB8E8;
	private static final int IMPERIAL = 0xFFD0463A;
	private static final int FRONTIER = 0xFFC9A66B;

	private VendorStockPayload stock;
	private boolean selling;
	/** The selected line's stock or sell index, or -1. */
	private int selected = -1;
	private int count = 1;
	private int scroll;
	private final float openedAt;
	private int panelX;
	private int panelY;
	private int panelW;
	private int panelH;
	private int listTop;
	private int listBottom;

	VendorScreen(VendorStockPayload stock) {
		super(Component.translatable("vendor.oldrepublic.stock." + stock.stock()));
		this.stock = stock;
		this.openedAt = UiAnim.time(0f);
		selectFirst();
	}

	/** Whether {@code next} belongs to this screen's vendor and stock. */
	boolean isFor(VendorStockPayload next) {
		return next.vendor() == stock.vendor() && next.stock().equals(stock.stock());
	}

	/** Shows a newer stock of the same vendor in place, keeping the tab and the selection where it still exists. */
	void show(VendorStockPayload next) {
		stock = next;

		if (selling ? sellAt(selected) == null : lineAt(selected) == null) {
			selectFirst();
		}

		count = Mth.clamp(count, 1, Math.max(1, maxCount()));
		rebuildWidgets();
	}

	private void selectFirst() {
		selected = -1;

		if (selling) {
			if (!stock.sells().isEmpty()) {
				selected = stock.sells().getFirst().index();
			}

			return;
		}

		for (Line line : stock.lines()) {
			if (line.lock().isEmpty()) {
				selected = line.index();
				return;
			}
		}

		if (!stock.lines().isEmpty()) {
			selected = stock.lines().getFirst().index();
		}
	}

	// ---------------------------------------------------------------- layout

	@Override
	protected void init() {
		panelW = Math.min(width - 16, MAX_W);
		panelH = Math.min(height - 8, MAX_H);
		panelX = (width - panelW) / 2;
		panelY = Math.max(4, (height - panelH) / 2);
		listTop = panelY + PAD + HEADER_H + 4 + TAB_H + 4;
		listBottom = panelY + panelH - PAD - FOOTER_H - 4;
		clampScroll();
		int tabY = panelY + PAD + HEADER_H + 4;
		addRenderableWidget(new HoloButton(panelX + PAD, tabY, TAB_W, TAB_H, Component.translatable("screen.oldrepublic.vendor.tab.buy"),
				b -> switchTab(false), !selling));
		addRenderableWidget(new HoloButton(panelX + PAD + TAB_W + 4, tabY, TAB_W, TAB_H, Component.translatable("screen.oldrepublic.vendor.tab.sell"),
				b -> switchTab(true), selling));

		// Footer row one: the quantity and the action.
		int rowOne = panelY + panelH - PAD - FOOTER_H;
		int x = panelX + PAD;
		addRenderableWidget(new HoloButton(x, rowOne, SMALL_W, BUTTON_H, Component.literal("-"), b -> changeCount(-1)));
		addRenderableWidget(new HoloButton(x + SMALL_W + 30, rowOne, SMALL_W, BUTTON_H, Component.literal("+"), b -> changeCount(1)));
		addRenderableWidget(new HoloButton(x + SMALL_W * 2 + 34, rowOne, 34, BUTTON_H, Component.translatable("screen.oldrepublic.vendor.max"),
				b -> changeCount(Integer.MAX_VALUE)).withTooltip(Component.translatable("screen.oldrepublic.vendor.max.hint")));

		int actionW = 110;
		HoloButton actionButton = addRenderableWidget(new HoloButton(panelX + panelW - PAD - actionW, rowOne, actionW, BUTTON_H, actionLabel(), b -> act(), true));
		actionButton.active = actionAllowed();

		// Footer row two: the exchange and Done.
		int rowTwo = rowOne + BUTTON_H + 6;
		int exchangeW = 96;
		HoloButton toBundle = addRenderableWidget(new HoloButton(x, rowTwo, exchangeW, BUTTON_H, Component.translatable("screen.oldrepublic.vendor.to_bundle"),
				b -> send(VendorBuyPayload.TO_BUNDLE, 0, 1)));
		toBundle.active = stock.chits() >= Credits.BUNDLE;
		toBundle.withTooltip(Component.translatable("screen.oldrepublic.vendor.to_bundle.hint", Credits.BUNDLE));
		HoloButton toChits = addRenderableWidget(new HoloButton(x + exchangeW + 4, rowTwo, exchangeW, BUTTON_H,
				Component.translatable("screen.oldrepublic.vendor.to_chits"), b -> send(VendorBuyPayload.TO_CHITS, 0, 1)));
		toChits.active = stock.bundles() > 0;
		toChits.withTooltip(Component.translatable("screen.oldrepublic.vendor.to_chits.hint", Credits.BUNDLE));

		int doneW = 60;
		addRenderableWidget(new HoloButton(panelX + panelW - PAD - doneW, rowTwo, doneW, BUTTON_H, Component.translatable("gui.done"), b -> onClose()));
	}

	private void switchTab(boolean sell) {
		if (selling == sell) {
			return;
		}

		selling = sell;
		scroll = 0;
		count = 1;
		selectFirst();
		rebuildWidgets();
	}

	private void changeCount(int delta) {
		long next = (long) count + delta;
		count = (int) Mth.clamp(next, 1, Math.max(1, maxCount()));
		rebuildWidgets();
	}

	/** The most the counter allows for the selection: the cap, the day's limit and what the credits cover; or what is held. */
	private int maxCount() {
		if (selling) {
			Sell sell = sellAt(selected);
			return sell == null ? 1 : sell.item().getCount();
		}

		Line line = lineAt(selected);

		if (line == null) {
			return 1;
		}

		int max = MAX_PURCHASE;

		if (line.dailyLeft() >= 0) {
			max = Math.min(max, line.dailyLeft());
		}

		if (line.price() > 0) {
			max = Math.min(max, stock.credits() / line.price());
		}

		return Math.max(1, max);
	}

	private Component actionLabel() {
		if (selling) {
			Sell sell = sellAt(selected);
			return Component.translatable("screen.oldrepublic.vendor.sell", sell == null ? 0 : (long) sell.each() * count);
		}

		Line line = lineAt(selected);
		return Component.translatable("screen.oldrepublic.vendor.buy", line == null ? 0 : (long) line.price() * count);
	}

	private boolean actionAllowed() {
		if (selling) {
			Sell sell = sellAt(selected);
			return sell != null && sell.item().getCount() >= count;
		}

		Line line = lineAt(selected);
		return line != null && line.lock().isEmpty() && line.dailyLeft() != 0 && (long) line.price() * count <= stock.credits();
	}

	private void act() {
		if (actionAllowed()) {
			send(selling ? VendorBuyPayload.SELL : VendorBuyPayload.BUY, selected, count);
		}
	}

	private void send(int action, int index, int amount) {
		ClientPlayNetworking.send(new VendorBuyPayload(stock.vendor(), action, index, amount));
	}

	private @Nullable Line lineAt(int index) {
		for (Line line : stock.lines()) {
			if (line.index() == index) {
				return line;
			}
		}

		return null;
	}

	private @Nullable Sell sellAt(int index) {
		for (Sell sell : stock.sells()) {
			if (sell.index() == index) {
				return sell;
			}
		}

		return null;
	}

	private int rows() {
		return selling ? stock.sells().size() : stock.lines().size();
	}

	private int visibleRows() {
		return Math.max(1, (listBottom - listTop) / ROW_H);
	}

	private void clampScroll() {
		scroll = Mth.clamp(scroll, 0, Math.max(0, rows() - visibleRows()));
	}

	// ---------------------------------------------------------------- input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0) {
			int row = rowAt(event.x(), event.y());

			if (row >= 0) {
				int index = selling ? stock.sells().get(row).index() : stock.lines().get(row).index();

				if (index != selected) {
					selected = index;
					count = 1;
					rebuildWidgets();
				} else if (doubleClick) {
					act();
				}

				return true;
			}
		}

		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (mouseY >= listTop && mouseY < listBottom) {
			scroll -= (int) Math.signum(scrollY);
			clampScroll();
			return true;
		}

		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	/** The list row under the mouse (an index into the shown list), or -1. */
	private int rowAt(double mx, double my) {
		if (mx < panelX + PAD || mx >= panelX + panelW - PAD || my < listTop || my >= listTop + visibleRows() * ROW_H) {
			return -1;
		}

		int row = scroll + (int) ((my - listTop) / ROW_H);
		return row < rows() ? row : -1;
	}

	@Override
	public void tick() {
		super.tick();
		Entity vendor = minecraft == null || minecraft.level == null ? null : minecraft.level.getEntity(stock.vendor());

		if (vendor == null || !vendor.isAlive() || minecraft.player == null || minecraft.player.distanceTo(vendor) > CLOSE_DISTANCE) {
			onClose();
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ---------------------------------------------------------------- drawing

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		float time = UiAnim.time(partialTick);
		float open = UiAnim.easeOutCubic(Math.min(1f, (time - openedAt) / 6f));
		Faction faction = faction(stock.faction());
		int accent = tint(faction);

		Ui.panel(graphics, panelX, panelY, panelW, panelH);
		Ui.scanlines(graphics, panelX + 3, panelY + 3, panelW - 6, panelH - 6, Ui.a(UiColors.HOLO, 0.03f * UiAnim.flicker(time)), time);
		drawHeader(graphics, faction, accent, time);
		Ui.inset(graphics, panelX + PAD - 2, listTop - 2, panelW - PAD * 2 + 4, listBottom - listTop + 4, open);

		if (rows() == 0) {
			Component none = Component.translatable(selling ? "screen.oldrepublic.vendor.sell.none" : "screen.oldrepublic.vendor.buy.none");
			Ui.text(graphics, font, none, panelX + (panelW - font.width(none)) / 2, (listTop + listBottom) / 2 - 4, UiColors.TEXT_MUTED, open);
		} else {
			drawRows(graphics, mouseX, mouseY, open, accent);
		}

		drawFooter(graphics, open);
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	private void drawHeader(GuiGraphicsExtractor g, @Nullable Faction faction, int accent, float time) {
		int x = panelX + PAD;
		int y = panelY + PAD;
		Ui.sprite(g, faction == null ? Ui.EMBLEM : emblem(faction), x, y, 9, 9, 1f);
		Component credits = Component.translatable("screen.oldrepublic.vendor.credits", stock.credits());
		int creditsW = font.width(credits);
		Ui.header(g, font, title, x + 13, y, panelW - PAD * 2 - 13 - creditsW - 8, time);
		Ui.text(g, font, credits, panelX + panelW - PAD - creditsW, y, UiColors.BRASS_LIGHT, 1f);
		g.fill(x, y + 12, x + 13, y + 13, Ui.a(accent, 0.9f));

		if (stock.honoured() && !selling) {
			Component honoured = Component.translatable("screen.oldrepublic.vendor.honoured");
			int hx = panelX + PAD + TAB_W * 2 + 12;
			Ui.text(g, font, Ui.fit(font, honoured.getString(), panelX + panelW - PAD - hx), hx, panelY + PAD + HEADER_H + 7, UiColors.OK, 1f);
		}
	}

	private void drawRows(GuiGraphicsExtractor g, int mouseX, int mouseY, float alpha, int accent) {
		int x = panelX + PAD;
		int w = panelW - PAD * 2 - SCROLL_W - 2;
		int hovered = rowAt(mouseX, mouseY);
		int end = Math.min(rows(), scroll + visibleRows());

		for (int row = scroll; row < end; row++) {
			int y = listTop + (row - scroll) * ROW_H;

			if (selling) {
				drawSell(g, stock.sells().get(row), x, y, w, row == hovered, alpha, accent, mouseX, mouseY);
			} else {
				drawLine(g, stock.lines().get(row), x, y, w, row == hovered, alpha, accent, mouseX, mouseY);
			}
		}

		if (rows() > visibleRows()) {
			int trackH = visibleRows() * ROW_H;
			int thumbH = Math.max(8, trackH * visibleRows() / rows());
			int thumbY = listTop + (trackH - thumbH) * scroll / Math.max(1, rows() - visibleRows());
			int sx = panelX + panelW - PAD - SCROLL_W;
			g.fill(sx, listTop, sx + SCROLL_W, listTop + trackH, Ui.a(UiColors.STEEL_DARK, alpha));
			g.fill(sx, thumbY, sx + SCROLL_W, thumbY + thumbH, Ui.a(UiColors.HOLO_DIM, alpha));
		}
	}

	private void drawLine(GuiGraphicsExtractor g, Line line, int x, int y, int w, boolean hover, float alpha, int accent, int mouseX, int mouseY) {
		boolean locked = !line.lock().isEmpty();
		boolean chosen = line.index() == selected;
		rowBackground(g, x, y, w, chosen, hover, accent, alpha);
		g.item(line.item(), x + 2, y + 2);

		float textAlpha = locked ? alpha * 0.6f : alpha;
		Component price = Component.translatable("screen.oldrepublic.vendor.price", line.price());
		int priceW = font.width(price);
		int nameW = w - 24 - priceW - 6;
		Ui.text(g, font, Ui.fit(font, name(line.item()), nameW), x + 22, y + 2, locked ? UiColors.TEXT_MUTED : UiColors.TEXT_WARM, textAlpha);
		boolean affordable = line.price() <= stock.credits();
		Ui.text(g, font, price, x + w - priceW - 2, y + 2, locked ? UiColors.TEXT_DIM : affordable ? UiColors.BRASS_LIGHT : UiColors.DANGER, textAlpha);

		Component sub = locked ? DialogueClient.reason(line.lock())
				: line.dailyLeft() >= 0 ? Component.translatable("screen.oldrepublic.vendor.daily_left", line.dailyLeft()) : null;

		if (sub != null) {
			Ui.text(g, font, Ui.fit(font, sub.getString(), w - 24), x + 22, y + 11, locked ? UiColors.AMBER : UiColors.TEXT_DIM, alpha);
		}

		if (mouseX >= x + 2 && mouseX < x + 18 && mouseY >= y + 2 && mouseY < y + 18) {
			tooltip(g, line.item(), mouseX, mouseY);
		}
	}

	private void drawSell(GuiGraphicsExtractor g, Sell sell, int x, int y, int w, boolean hover, float alpha, int accent, int mouseX, int mouseY) {
		boolean chosen = sell.index() == selected;
		rowBackground(g, x, y, w, chosen, hover, accent, alpha);
		g.item(sell.item(), x + 2, y + 2);

		Component each = Component.translatable("screen.oldrepublic.vendor.each", sell.each());
		int eachW = font.width(each);
		Ui.text(g, font, Ui.fit(font, name(sell.item()), w - 24 - eachW - 6), x + 22, y + 2, UiColors.TEXT_WARM, alpha);
		Ui.text(g, font, each, x + w - eachW - 2, y + 2, UiColors.BRASS_LIGHT, alpha);
		Ui.text(g, font, Component.translatable("screen.oldrepublic.vendor.held", sell.item().getCount()), x + 22, y + 11, UiColors.TEXT_DIM, alpha);

		if (mouseX >= x + 2 && mouseX < x + 18 && mouseY >= y + 2 && mouseY < y + 18) {
			tooltip(g, sell.item(), mouseX, mouseY);
		}
	}

	private void rowBackground(GuiGraphicsExtractor g, int x, int y, int w, boolean chosen, boolean hover, int accent, float alpha) {
		if (chosen) {
			g.fill(x, y, x + w, y + ROW_H - 1, Ui.a(accent, 0.22f * alpha));
			g.fill(x, y, x + 1, y + ROW_H - 1, Ui.a(accent, 0.9f * alpha));
		} else if (hover) {
			g.fill(x, y, x + w, y + ROW_H - 1, Ui.a(UiColors.HOLO, 0.08f * alpha));
		}
	}

	/** The item's name, with the count when one purchase gives more than one. */
	private static String name(ItemStack stack) {
		String name = stack.getHoverName().getString();
		return stack.getCount() > 1 ? Component.translatable("screen.oldrepublic.vendor.count", stack.getCount(), name).getString() : name;
	}

	/** The hovered item's name as a tooltip. */
	private void tooltip(GuiGraphicsExtractor g, ItemStack stack, int mouseX, int mouseY) {
		g.setTooltipForNextFrame(font, stack.getHoverName(), mouseX, mouseY);
	}

	private void drawFooter(GuiGraphicsExtractor g, float alpha) {
		int rowOne = panelY + panelH - PAD - FOOTER_H;
		int x = panelX + PAD;
		String amount = Integer.toString(count);
		Ui.text(g, font, amount, x + SMALL_W + 2 + (28 - font.width(amount)) / 2, rowOne + 4, UiColors.TEXT, alpha);

		Component status = status();

		if (status != null) {
			int sx = x + SMALL_W * 2 + 74;
			int sw = panelX + panelW - PAD - 110 - 6 - sx;
			Ui.text(g, font, Ui.fit(font, status.getString(), sw), sx, rowOne + 4, UiColors.AMBER, alpha);
		}
	}

	/** Why the action button is off, or null when it is on. */
	private @Nullable Component status() {
		if (selling) {
			return sellAt(selected) == null ? Component.translatable("screen.oldrepublic.vendor.pick") : null;
		}

		Line line = lineAt(selected);

		if (line == null) {
			return Component.translatable("screen.oldrepublic.vendor.pick");
		}

		if (!line.lock().isEmpty()) {
			return Component.translatable("screen.oldrepublic.vendor.locked");
		}

		if (line.dailyLeft() == 0) {
			return Component.translatable("vendor.oldrepublic.locked.sold_out");
		}

		if ((long) line.price() * count > stock.credits()) {
			return Component.translatable("screen.oldrepublic.vendor.short", (long) line.price() * count - stock.credits());
		}

		return null;
	}

	// ---------------------------------------------------------------- styles

	private static @Nullable Faction faction(int ordinal) {
		Faction[] factions = Faction.values();
		return ordinal >= 0 && ordinal < factions.length ? factions[ordinal] : null;
	}

	private static int tint(@Nullable Faction faction) {
		if (faction == null) {
			return UiColors.BRASS;
		}

		return switch (faction) {
			case REPUBLIC -> REPUBLIC;
			case SITH_EMPIRE -> IMPERIAL;
			case FRONTIER -> FRONTIER;
			case JEDI_ORDER -> UiColors.RESONANCE;
		};
	}

	private static Identifier emblem(Faction faction) {
		return OldRepublicMod.id("identity/faction_" + faction.id());
	}
}
