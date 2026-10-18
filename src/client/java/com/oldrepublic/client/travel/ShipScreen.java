package com.oldrepublic.client.travel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.ui.HoloButton;
import com.oldrepublic.client.ui.TabStrip;
import com.oldrepublic.client.ui.Ui;
import com.oldrepublic.client.ui.UiAnim;
import com.oldrepublic.client.ui.UiColors;
import com.oldrepublic.registry.M3Items;
import com.oldrepublic.registry.M4Items;
import com.oldrepublic.travel.Destination;
import com.oldrepublic.travel.NavData;
import com.oldrepublic.travel.ShipMenu;
import com.oldrepublic.travel.ShipRules;
import com.oldrepublic.travel.ShipState;
import com.oldrepublic.travel.TravelService.Refusal;
import com.oldrepublic.travel.network.ShipActionPayload;
import com.oldrepublic.travel.network.ShipStatusPayload;
import com.oldrepublic.travel.network.ShipStatusPayload.Member;
import com.oldrepublic.travel.network.ShipStatusPayload.Row;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The ship screen (spec MILESTONE_4.md 4.10, 11.3), in the workbench's durasteel style and size.
 * Tabs: <b>Status</b> (state, hull, fuel, seats, hold, crew and the upgrades), <b>Repairs</b> (the
 * four sockets while the ship is a hulk, then the hull repair hint), <b>Nav</b> (the destinations,
 * greyed rows saying why, the warnings and Launch, or the same choice on the star chart),
 * <b>Hold</b> and <b>Crew</b>. A crew member sees the Status tab alone, read-only.
 *
 * <p>Everything shown comes from the server's {@code ship_status}; every button sends a
 * {@code ship_action} that the server checks again. The hold's slots are the menu's, shown on the
 * Hold tab only.
 */
public class ShipScreen extends AbstractContainerScreen<ShipMenu> {
	private static final int WIDTH = 272;
	private static final int HEIGHT = 204;
	private static final String STATUS = "status";
	private static final String REPAIRS = "repairs";
	private static final String NAV = "nav";
	private static final String HOLD = "hold";
	private static final String CREW = "crew";

	/** Status tab columns. */
	private static final int LEFT_X = 12;
	private static final int LEFT_W = 120;
	private static final int RIGHT_X = 140;
	private static final int RIGHT_W = 122;
	private static final int UPGRADE_Y = 58;
	private static final int UPGRADE_STEP = 30;
	/** Repairs tab rows. */
	private static final int SOCKET_Y = 42;
	private static final int SOCKET_STEP = 32;
	/** Nav tab rows and the star chart. */
	private static final int ROW_X = 12;
	private static final int ROW_W = 248;
	private static final int ROW_Y = 42;
	private static final int ROW_H = 22;
	private static final int ROW_STEP = 24;
	private static final int CHART_X = 14;
	private static final int CHART_Y = 42;
	private static final int CHART_W = 244;
	private static final int CHART_H = 100;
	private static final Identifier CHART = OldRepublicMod.id("textures/gui/destination_map.png");
	private static final int CHART_TEX_W = 256;
	private static final int CHART_TEX_H = 128;
	/** Where each destination sits on the chart (fractions of its size), and its light. */
	private static final float[][] POINTS = {{0.16f, 0.58f}, {0.56f, 0.26f}, {0.80f, 0.68f}, {0.42f, 0.84f}};
	private static final int[] POINT_COLORS = {0xFFFFE9B0, 0xFF9FE8C8, 0xFFFF9A6A, UiColors.AMBER};
	/** The faint lanes between the systems: home to each world, between the worlds, home to the camp. */
	private static final int[][] LANES = {{0, 1}, {0, 2}, {1, 2}, {0, 3}};
	private static final int POINT_HIT = 9;
	/** Crew tab rows. */
	private static final int CREW_ROWS = 3;
	private static final int NEARBY_ROWS = 3;
	private static final int CREW_Y = 56;
	private static final int NEARBY_Y = 136;
	private static final int MEMBER_STEP = 20;
	/** Bottom button row. */
	private static final int BUTTON_Y = 178;

	private static final Item[] SOCKET_ITEMS = {M3Items.DURASTEEL_PLATE, M4Items.POWER_CORE, M4Items.HYPERDRIVE_MOTIVATOR, M4Items.NAV_COMPUTER};
	private static final Item[] UPGRADE_ITEMS = {M4Items.PASSENGER_SEAT, M4Items.CARGO_BAY_EXTENSION, M4Items.REINFORCED_PLATING, M4Items.FUEL_TANK_EXTENSION};

	/** A line of text with its colour. */
	private record Line(Component text, int color) {
	}

	private final TabStrip tabs;
	private @Nullable ShipStatusPayload status;
	/** The chosen destination's ordinal, or −1. */
	private int selected = -1;
	private boolean chart;

	private HoloButton fuel;
	private final HoloButton[] upgradeButtons = new HoloButton[UPGRADE_ITEMS.length];
	private final HoloButton[] socketButtons = new HoloButton[ShipRules.SOCKETS];
	private HoloButton chartToggle;
	private HoloButton launch;
	private final HoloButton[] removeButtons = new HoloButton[CREW_ROWS];
	private final HoloButton[] addButtons = new HoloButton[NEARBY_ROWS];

	public ShipScreen(ShipMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, WIDTH, HEIGHT);
		this.titleLabelX = 10;
		this.titleLabelY = 6;
		this.tabs = new TabStrip(tabsFor(false));
	}

	private static List<TabStrip.Tab> tabsFor(boolean owner) {
		List<TabStrip.Tab> list = new ArrayList<>();
		list.add(new TabStrip.Tab(STATUS, Component.translatable("screen.oldrepublic.ship.tab.status")));

		if (owner) {
			for (String id : new String[] {REPAIRS, NAV, HOLD, CREW}) {
				list.add(new TabStrip.Tab(id, Component.translatable("screen.oldrepublic.ship.tab." + id)));
			}
		}

		return list;
	}

	/** A fresh view from the server (the screen's own ship; notices go to the toasts instead). */
	void onStatus(ShipStatusPayload payload) {
		boolean wasOwner = status != null && status.viewerIsOwner();
		status = payload;
		menu.setHoldSize(payload.ship().holdSize());

		if (payload.viewerIsOwner() != wasOwner) {
			tabs.setTabs(tabsFor(payload.viewerIsOwner()));
		}

		if (selected < 0 || refusal(selected) == Refusal.LOCKED || refusal(selected) == Refusal.UNAVAILABLE) {
			selected = firstChoice(payload);
		}

		tabChanged();
	}

	/** The first destination that can be launched to, else the first one not refused for good. */
	private static int firstChoice(ShipStatusPayload payload) {
		for (Row row : payload.destinations()) {
			if (row.refusal() == Refusal.NONE.ordinal()) {
				return row.destination();
			}
		}

		for (Row row : payload.destinations()) {
			Refusal r = refusalOf(row.refusal());

			if (r != Refusal.HERE && r != Refusal.LOCKED && r != Refusal.UNAVAILABLE) {
				return row.destination();
			}
		}

		return -1;
	}

	// ---------------------------------------------------------------- buttons

	@Override
	protected void init() {
		super.init();
		int x0 = leftPos;
		int y0 = topPos;
		fuel = addRenderableWidget(new HoloButton(x0 + LEFT_X, y0 + BUTTON_Y, LEFT_W, 16, Component.translatable("screen.oldrepublic.ship.fuel_button"),
				b -> send(ShipActionPayload.FUEL, 0, "")).withTooltip(Component.translatable("screen.oldrepublic.ship.fuel_button.tooltip")));

		for (int i = 0; i < upgradeButtons.length; i++) {
			int index = i;
			upgradeButtons[i] = addRenderableWidget(new HoloButton(x0 + RIGHT_X + 84, y0 + UPGRADE_Y + i * UPGRADE_STEP + 2, 38, 14,
					Component.translatable("screen.oldrepublic.ship.upgrade.fit"), b -> toggleUpgrade(index)));
		}

		for (int i = 0; i < socketButtons.length; i++) {
			int socket = i;
			socketButtons[i] = addRenderableWidget(new HoloButton(x0 + 214, y0 + SOCKET_Y + i * SOCKET_STEP + 3, 46, 14,
					Component.translatable("screen.oldrepublic.ship.socket.fit"), b -> send(ShipActionPayload.FIT_SOCKET, socket, "")));
		}

		chartToggle = addRenderableWidget(new HoloButton(x0 + ROW_X, y0 + BUTTON_Y, 72, 16, Component.translatable("screen.oldrepublic.ship.nav.chart"), b -> {
			chart = !chart;
			tabChanged();
		}));
		launch = addRenderableWidget(new HoloButton(x0 + ROW_X + ROW_W - 72, y0 + BUTTON_Y, 72, 16, Component.translatable("screen.oldrepublic.ship.nav.launch"),
				b -> send(ShipActionPayload.LAUNCH, selected, ""), true));

		for (int i = 0; i < CREW_ROWS; i++) {
			int row = i;
			removeButtons[i] = addRenderableWidget(new HoloButton(x0 + 214, y0 + CREW_Y + i * MEMBER_STEP, 46, 14,
					Component.translatable("screen.oldrepublic.ship.crew.remove"), b -> crewAction(ShipActionPayload.REMOVE_CREW, row)));
		}

		for (int i = 0; i < NEARBY_ROWS; i++) {
			int row = i;
			addButtons[i] = addRenderableWidget(new HoloButton(x0 + 214, y0 + NEARBY_Y + i * MEMBER_STEP, 46, 14,
					Component.translatable("screen.oldrepublic.ship.crew.add"), b -> crewAction(ShipActionPayload.ADD_CREW, row)));
		}

		tabChanged();
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		updateButtons();
	}

	/** The open tab changed (or the view did): the hold's slots and the buttons follow it. */
	private void tabChanged() {
		menu.showHold(HOLD.equals(tabs.selectedId()));

		if (fuel != null) {
			updateButtons();
		}
	}

	private void updateButtons() {
		String tab = tabs.selectedId();
		ShipStatusPayload view = status;
		boolean owner = view != null && view.viewerIsOwner();
		ShipStatusPayload.Ship ship = view == null ? ShipStatusPayload.Ship.EMPTY : view.ship();
		ShipState state = stateOf(ship.state());
		boolean flying = state.flying();

		fuel.visible = owner && STATUS.equals(tab);
		fuel.active = !flying && state != ShipState.HULK && ship.fuel() < ship.tank() && has(stack -> stack.is(M4Items.HYPERFUEL_CELL));

		for (int i = 0; i < upgradeButtons.length; i++) {
			int bit = ShipRules.UPGRADES[i];
			boolean fitted = (ship.upgrades() & bit) != 0;
			HoloButton button = upgradeButtons[i];
			button.visible = owner && STATUS.equals(tab);
			button.setMessage(Component.translatable(fitted ? "screen.oldrepublic.ship.upgrade.remove" : "screen.oldrepublic.ship.upgrade.fit"));
			Item item = UPGRADE_ITEMS[i];
			button.active = !flying && state != ShipState.HULK && (fitted || has(stack -> stack.is(item)));
		}

		boolean hulk = state == ShipState.HULK;

		for (int i = 0; i < socketButtons.length; i++) {
			HoloButton button = socketButtons[i];
			button.visible = owner && REPAIRS.equals(tab) && hulk && (ship.sockets() & 1 << i) == 0;
			button.active = has(socketPart(i));
		}

		boolean nav = owner && NAV.equals(tab);
		chartToggle.visible = nav;
		chartToggle.setMessage(Component.translatable(chart ? "screen.oldrepublic.ship.nav.list" : "screen.oldrepublic.ship.nav.chart"));
		launch.visible = nav;
		launch.active = view != null && view.aboard() && selected >= 0 && refusal(selected) == Refusal.NONE;

		boolean crewTab = owner && CREW.equals(tab);
		List<Member> crew = view == null ? List.of() : view.crew();
		List<Member> nearby = view == null ? List.of() : view.nearby();

		for (int i = 0; i < CREW_ROWS; i++) {
			removeButtons[i].visible = crewTab && i < crew.size();
			removeButtons[i].active = !flying || i < crew.size() && !crew.get(i).aboard();
		}

		for (int i = 0; i < NEARBY_ROWS; i++) {
			addButtons[i].visible = crewTab && i < nearby.size();
			addButtons[i].active = crew.size() < ship.crewLimit();
		}
	}

	/**
	 * Opens tab {@code id} ({@code status}, {@code repairs}, {@code nav}, {@code hold} or {@code crew}),
	 * with the Nav tab's star chart when {@code chartView}, as a click on the tab would. The client
	 * test's screenshots use it. False when this viewer has no such tab.
	 */
	public boolean openTab(String id, boolean chartView) {
		if (!tabs.select(id)) {
			return false;
		}

		chart = chartView;
		tabChanged();
		return true;
	}

	private void toggleUpgrade(int index) {
		if (status == null) {
			return;
		}

		int bit = ShipRules.UPGRADES[index];
		boolean fitted = (status.ship().upgrades() & bit) != 0;
		send(fitted ? ShipActionPayload.REMOVE_UPGRADE : ShipActionPayload.FIT_UPGRADE, bit, "");
	}

	private void crewAction(int button, int row) {
		if (status == null) {
			return;
		}

		List<Member> list = button == ShipActionPayload.REMOVE_CREW ? status.crew() : status.nearby();

		if (row < list.size()) {
			send(button, 0, list.get(row).id().toString());
		}
	}

	private static void send(int button, int arg, String target) {
		ClientPlayNetworking.send(new ShipActionPayload(button, arg, target));
	}

	/** The part a repair socket takes, as the server checks it: a loaded nav computer for the nav socket. */
	private static Predicate<ItemStack> socketPart(int socket) {
		Item item = SOCKET_ITEMS[socket];
		return socket == ShipRules.SOCKET_NAV_INDEX ? NavData::isLoaded : stack -> stack.is(item);
	}

	/** Whether the player carries a matching stack (a creative player always does). */
	private boolean has(Predicate<ItemStack> matches) {
		if (minecraft.player == null) {
			return false;
		}

		if (minecraft.player.isCreative()) {
			return true;
		}

		Inventory inventory = minecraft.player.getInventory();

		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (matches.test(inventory.getItem(i))) {
				return true;
			}
		}

		return false;
	}

	// ---------------------------------------------------------------- clicks

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (tabs.mouseClicked(event)) {
			AbstractWidget.playButtonClickSound(minecraft.getSoundManager());
			tabChanged();
			return true;
		}

		if (event.button() == 0 && status != null && status.viewerIsOwner() && NAV.equals(tabs.selectedId())) {
			int picked = chart ? pointAt(event.x(), event.y()) : rowAt(event.x(), event.y());

			if (picked >= 0) {
				AbstractWidget.playButtonClickSound(minecraft.getSoundManager());
				selected = picked;
				updateButtons();
				return true;
			}
		}

		return super.mouseClicked(event, doubleClick);
	}

	/** The destination row under the mouse (list view), or −1. */
	private int rowAt(double mouseX, double mouseY) {
		if (status == null) {
			return -1;
		}

		List<Row> rows = status.destinations();

		for (int i = 0; i < rows.size(); i++) {
			int x = leftPos + ROW_X;
			int y = topPos + ROW_Y + i * ROW_STEP;

			if (mouseX >= x && mouseX < x + ROW_W && mouseY >= y && mouseY < y + ROW_H) {
				return rows.get(i).destination();
			}
		}

		return -1;
	}

	/** The destination point under the mouse (chart view), or −1. */
	private int pointAt(double mouseX, double mouseY) {
		if (status == null) {
			return -1;
		}

		for (Row row : status.destinations()) {
			int d = row.destination();

			if (d < POINTS.length && Math.abs(mouseX - pointX(d)) <= POINT_HIT && Math.abs(mouseY - pointY(d)) <= POINT_HIT) {
				return d;
			}
		}

		return -1;
	}

	private int pointX(int destination) {
		return leftPos + CHART_X + Math.round(POINTS[destination][0] * CHART_W);
	}

	private int pointY(int destination) {
		return topPos + CHART_Y + Math.round(POINTS[destination][1] * CHART_H);
	}

	// ---------------------------------------------------------------- tooltips

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);

		if (status == null) {
			return;
		}

		String tab = tabs.selectedId();

		if (NAV.equals(tab) && status.viewerIsOwner()) {
			int hovered = chart ? pointAt(mouseX, mouseY) : rowAt(mouseX, mouseY);

			if (hovered >= 0) {
				graphics.setComponentTooltipForNextFrame(font, destinationTooltip(hovered), mouseX, mouseY);
			}
		} else if (STATUS.equals(tab)) {
			for (int i = 0; i < UPGRADE_ITEMS.length; i++) {
				if (isHovering(RIGHT_X, UPGRADE_Y + i * UPGRADE_STEP, 18, 18, mouseX, mouseY)) {
					graphics.setComponentTooltipForNextFrame(font, List.of(new ItemStack(UPGRADE_ITEMS[i]).getHoverName(),
							Component.translatable("screen.oldrepublic.ship.upgrade." + i + ".effect").withColor(UiColors.TEXT_MUTED)), mouseX, mouseY);
				}
			}
		} else if (REPAIRS.equals(tab) && stateOf(status.ship().state()) == ShipState.HULK) {
			for (int i = 0; i < ShipRules.SOCKETS; i++) {
				if (isHovering(LEFT_X, SOCKET_Y + i * SOCKET_STEP, 20, 20, mouseX, mouseY)) {
					graphics.setComponentTooltipForNextFrame(font, List.of(Component.translatable("screen.oldrepublic.ship.socket." + i),
							Component.translatable("screen.oldrepublic.ship.socket." + i + ".hint").withColor(UiColors.TEXT_MUTED)), mouseX, mouseY);
				}
			}
		}
	}

	private List<Component> destinationTooltip(int destination) {
		String id = Destination.values()[destination].id();
		Refusal refusal = refusal(destination);
		List<Component> lines = new ArrayList<>();
		lines.add(FlightHud.destinationName(destination).copy().withColor(UiColors.HOLO_BRIGHT));
		lines.add(Component.translatable("destination.oldrepublic." + id + ".desc").withColor(UiColors.TEXT_MUTED));
		lines.add(Component.translatable("destination.oldrepublic." + id + ".pad").withColor(UiColors.TEXT_DIM));
		lines.add(refusalText(refusal).copy().withColor(refusalColor(refusal)));
		return lines;
	}

	// ---------------------------------------------------------------- background

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractBackground(g, mouseX, mouseY, delta);
		float time = UiAnim.time(delta);
		int x0 = leftPos;
		int y0 = topPos;
		Ui.panel(g, x0, y0, imageWidth, imageHeight);
		tabs.render(g, font, x0 + 8, y0 + 20, 256, mouseX, mouseY, delta);

		if (status == null) {
			Ui.text(g, font, Component.translatable("screen.oldrepublic.ship.waiting"), x0 + LEFT_X, y0 + 44, UiColors.TEXT_DIM, UiAnim.flicker(time));
			return;
		}

		switch (tabs.selectedId() == null ? STATUS : tabs.selectedId()) {
			case REPAIRS -> drawRepairs(g, x0, y0, time);
			case NAV -> drawNav(g, x0, y0, mouseX, mouseY, time);
			case HOLD -> drawHold(g, x0, y0);
			case CREW -> drawCrew(g, x0, y0);
			default -> drawStatus(g, x0, y0, time);
		}
	}

	// Label coordinates are relative to the panel (leftPos, topPos), as vanilla's titleLabelX/Y are.
	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		Component heading = title;

		if (status != null && !status.ship().hullCode().isEmpty()) {
			heading = title.copy().append(Component.literal("  " + status.ship().hullCode()).withColor(UiColors.BRASS_LIGHT));
		}

		Ui.header(graphics, font, heading, titleLabelX, titleLabelY, imageWidth - 2 * titleLabelX, UiAnim.time(0f));
	}

	// ---------------------------------------------------------------- Status

	private void drawStatus(GuiGraphicsExtractor g, int x0, int y0, float time) {
		ShipStatusPayload.Ship ship = status.ship();
		ShipState state = stateOf(ship.state());
		int x = x0 + LEFT_X;
		int y = y0 + 42;
		Ui.inset(g, x - 4, y - 4, LEFT_W + 8, 98, 1f);
		Ui.scanlines(g, x - 2, y - 2, LEFT_W + 4, 94, Ui.a(UiColors.HOLO, 0.04f), time);

		Ui.labelValue(g, font, Component.translatable("screen.oldrepublic.ship.state"), Component.translatable("ship.oldrepublic.state." + state.id()), x, y,
				LEFT_W, UiColors.TEXT_MUTED, stateColor(state));
		y += 13;
		int hullColor = hullColor(ship.hull());
		Ui.labelValue(g, font, Component.translatable("screen.oldrepublic.ship.hull"), Component.literal(ship.hull() + "/100"), x, y, LEFT_W, UiColors.TEXT_MUTED,
				hullColor);
		Ui.bar(g, x, y + 10, LEFT_W, 6, ship.hull() / 100f, hullColor, Ui.a(hullColor, 0.55f), 5, 1f);
		y += 22;
		Ui.labelValue(g, font, Component.translatable("screen.oldrepublic.ship.fuel"), Component.literal(ship.fuel() + "/" + ship.tank()), x, y, LEFT_W,
				UiColors.TEXT_MUTED, ship.fuel() > 0 ? UiColors.HOLO : UiColors.AMBER);
		Ui.pips(g, x, y + 11, ship.tank(), ship.fuel(), UiColors.HOLO, 1f);
		y += 21;
		Ui.labelValue(g, font, Component.translatable("screen.oldrepublic.ship.seats"), Component.literal(String.valueOf(ship.seats())), x, y, LEFT_W,
				UiColors.TEXT_MUTED, UiColors.TEXT);
		y += 11;
		Ui.labelValue(g, font, Component.translatable("screen.oldrepublic.ship.hold_size"),
				Component.translatable("screen.oldrepublic.ship.slots", ship.holdSize()), x, y, LEFT_W, UiColors.TEXT_MUTED, UiColors.TEXT);
		y += 11;
		Ui.labelValue(g, font, Component.translatable("screen.oldrepublic.ship.crew_count"),
				Component.translatable("screen.oldrepublic.ship.of", status.crew().size(), ship.crewLimit()), x, y, LEFT_W, UiColors.TEXT_MUTED, UiColors.TEXT);

		if (!status.viewerIsOwner()) {
			int ly = y0 + 146;

			for (String line : Ui.wrap(font, Component.translatable("screen.oldrepublic.ship.crew_view").getString(), LEFT_W, 3)) {
				g.text(font, line, x, ly, UiColors.TEXT_DIM, false);
				ly += 10;
			}
		}

		drawUpgrades(g, x0, y0, ship);
	}

	private void drawUpgrades(GuiGraphicsExtractor g, int x0, int y0, ShipStatusPayload.Ship ship) {
		int x = x0 + RIGHT_X;
		Ui.header(g, font, Component.translatable("screen.oldrepublic.ship.upgrades"), x, y0 + 40, RIGHT_W, UiAnim.time(0f));

		for (int i = 0; i < UPGRADE_ITEMS.length; i++) {
			int y = y0 + UPGRADE_Y + i * UPGRADE_STEP;
			boolean fitted = (ship.upgrades() & ShipRules.UPGRADES[i]) != 0;
			Ui.inset(g, x, y, 18, 18, 1f);
			g.item(new ItemStack(UPGRADE_ITEMS[i]), x + 1, y + 1);

			if (!fitted) {
				g.fill(x + 1, y + 1, x + 17, y + 17, Ui.a(UiColors.OUTLINE, 0.55f));
			}

			int textW = status.viewerIsOwner() ? 60 : RIGHT_W - 22;
			String name = new ItemStack(UPGRADE_ITEMS[i]).getHoverName().getString();
			g.text(font, Ui.fit(font, name, textW), x + 22, y + 1, fitted ? UiColors.TEXT : UiColors.TEXT_MUTED, false);
			Component line = fitted ? Component.translatable("screen.oldrepublic.ship.upgrade.fitted") : Component.translatable("screen.oldrepublic.ship.upgrade." + i + ".effect");
			g.text(font, Ui.fit(font, line.getString(), textW), x + 22, y + 11, fitted ? UiColors.OK : UiColors.TEXT_DIM, false);
		}
	}

	// ---------------------------------------------------------------- Repairs

	private void drawRepairs(GuiGraphicsExtractor g, int x0, int y0, float time) {
		ShipStatusPayload.Ship ship = status.ship();

		if (stateOf(ship.state()) != ShipState.HULK) {
			drawRepairHint(g, x0, y0, ship);
			return;
		}

		int done = 0;

		for (int i = 0; i < ShipRules.SOCKETS; i++) {
			int y = y0 + SOCKET_Y + i * SOCKET_STEP;
			int x = x0 + LEFT_X;
			boolean fitted = (ship.sockets() & 1 << i) != 0;
			done += fitted ? 1 : 0;
			Ui.sprite(g, Ui.SOCKET, x, y, 20, 20, 1f);
			g.item(new ItemStack(SOCKET_ITEMS[i]), x + 2, y + 2);

			if (!fitted) {
				g.fill(x + 2, y + 2, x + 18, y + 18, Ui.a(UiColors.OUTLINE, 0.6f));
			} else {
				g.fill(x + 20, y + 9, x + 26, y + 10, Ui.a(UiColors.HOLO, 0.8f * UiAnim.flicker(time + i * 7)));
			}

			g.text(font, Component.translatable("screen.oldrepublic.ship.socket." + i), x + 30, y + 1, fitted ? UiColors.TEXT : UiColors.TEXT_MUTED, false);
			Line detail;

			if (fitted) {
				detail = new Line(Component.translatable("screen.oldrepublic.ship.socket.done"), UiColors.OK);
			} else if (i == ShipRules.SOCKET_HULL) {
				detail = new Line(Component.translatable("screen.oldrepublic.ship.socket.plates", ship.plates(), ship.platesNeeded()), UiColors.AMBER);
			} else if (i == ShipRules.SOCKET_NAV_INDEX && !has(NavData::isLoaded) && has(stack -> stack.is(M4Items.NAV_COMPUTER))) {
				detail = new Line(Component.translatable("ship.oldrepublic.socket.nav_blank"), UiColors.AMBER);
			} else {
				detail = new Line(Component.translatable("screen.oldrepublic.ship.socket.needed"), UiColors.AMBER);
			}

			g.text(font, Ui.fit(font, detail.text().getString(), 172), x + 30, y + 11, detail.color(), false);
		}

		int by = y0 + SOCKET_Y + ShipRules.SOCKETS * SOCKET_STEP;
		Ui.bar(g, x0 + LEFT_X, by, ROW_W, 6, done / (float) ShipRules.SOCKETS, UiColors.HOLO, UiColors.HOLO_DIM, ShipRules.SOCKETS, 1f);
		g.text(font, Ui.fit(font, Component.translatable("screen.oldrepublic.ship.repairs.footer").getString(), ROW_W), x0 + LEFT_X, by + 10,
				UiColors.TEXT_DIM, false);
	}

	/** After the hulk stands up: the hull and how to weld it. */
	private void drawRepairHint(GuiGraphicsExtractor g, int x0, int y0, ShipStatusPayload.Ship ship) {
		int x = x0 + LEFT_X;
		int y = y0 + 42;
		int hullColor = hullColor(ship.hull());
		Ui.labelValue(g, font, Component.translatable("screen.oldrepublic.ship.hull"), Component.literal(ship.hull() + "/100"), x, y, ROW_W, UiColors.TEXT_MUTED,
				hullColor);
		Ui.bar(g, x, y + 11, ROW_W, 7, ship.hull() / 100f, hullColor, Ui.a(hullColor, 0.55f), 10, 1f);
		y += 28;

		for (String line : Ui.wrap(font, Component.translatable("screen.oldrepublic.ship.repairs.hint").getString(), ROW_W, 8)) {
			g.text(font, line, x, y, UiColors.TEXT_MUTED, false);
			y += 10;
		}

		// The four parts, fitted.
		int iy = y0 + 160;

		for (int i = 0; i < SOCKET_ITEMS.length; i++) {
			Ui.inset(g, x + i * 22, iy, 18, 18, 1f);
			g.item(new ItemStack(SOCKET_ITEMS[i]), x + i * 22 + 1, iy + 1);
		}

		g.text(font, Ui.fit(font, Component.translatable("screen.oldrepublic.ship.repairs.all_fitted").getString(), ROW_W - 94), x + 94, iy + 5, UiColors.OK, false);
	}

	// ---------------------------------------------------------------- Nav

	private void drawNav(GuiGraphicsExtractor g, int x0, int y0, int mouseX, int mouseY, float time) {
		int warnY;

		if (chart) {
			drawChart(g, x0, y0, mouseX, mouseY, time);
			warnY = y0 + CHART_Y + CHART_H + 6;

			if (selected >= 0) {
				Refusal r = refusal(selected);
				g.text(font, FlightHud.destinationName(selected), x0 + ROW_X, warnY, UiColors.HOLO_BRIGHT, false);
				Component reason = refusalText(r);
				g.text(font, reason, x0 + ROW_X + ROW_W - font.width(reason), warnY, refusalColor(r), false);
				warnY += 11;
			}
		} else {
			List<Row> rows = status.destinations();

			for (int i = 0; i < rows.size() && i < Destination.values().length; i++) {
				drawRow(g, x0 + ROW_X, y0 + ROW_Y + i * ROW_STEP, rows.get(i), mouseX, mouseY, time);
			}

			warnY = y0 + ROW_Y + Destination.values().length * ROW_STEP + 2;
		}

		for (Line warning : warnings()) {
			if (warnY > y0 + BUTTON_Y - 10) {
				break;
			}

			g.text(font, Ui.fit(font, warning.text().getString(), ROW_W), x0 + ROW_X, warnY, warning.color(), false);
			warnY += 10;
		}
	}

	/** What the nav console warns about before a launch (4.6). */
	private List<Line> warnings() {
		List<Line> list = new ArrayList<>();

		if (!status.aboard()) {
			list.add(new Line(Component.translatable("screen.oldrepublic.ship.nav.board_first"), UiColors.AMBER));
		}

		if (status.hullWarning()) {
			list.add(new Line(Component.translatable("screen.oldrepublic.ship.nav.hull_warning"), UiColors.AMBER));
		}

		if (!status.crew().isEmpty()) {
			long aboard = status.crew().stream().filter(Member::aboard).count();
			list.add(new Line(Component.translatable("screen.oldrepublic.ship.nav.crew_aboard", aboard, status.crew().size()),
					aboard < status.crew().size() ? UiColors.AMBER : UiColors.TEXT_MUTED));
		}

		return list;
	}

	private void drawRow(GuiGraphicsExtractor g, int x, int y, Row row, int mouseX, int mouseY, float time) {
		Refusal r = refusalOf(row.refusal());
		boolean dim = r == Refusal.LOCKED || r == Refusal.UNAVAILABLE;
		boolean isSelected = row.destination() == selected;
		boolean hover = mouseX >= x && mouseX < x + ROW_W && mouseY >= y && mouseY < y + ROW_H;
		Identifier card = isSelected ? Ui.CARD_SELECTED : dim ? Ui.CARD_LOCKED : Ui.CARD;
		Ui.sprite(g, card, x, y, ROW_W, ROW_H, hover && !isSelected ? 0.85f : 1f);

		if (isSelected) {
			g.fill(x + 3, y + 4, x + 5, y + ROW_H - 4, Ui.a(UiColors.HOLO, UiAnim.flicker(time)));
		}

		int color = row.destination() < POINT_COLORS.length ? POINT_COLORS[row.destination()] : UiColors.HOLO;
		g.fill(x + 9, y + 9, x + 13, y + 13, Ui.a(color, dim ? 0.35f : 0.9f));
		Component name = FlightHud.destinationName(row.destination());
		Component reason = refusalText(r);
		int nameW = ROW_W - 30 - font.width(reason) - 8;
		g.text(font, Ui.fit(font, name.getString(), nameW), x + 18, y + 7, dim ? UiColors.TEXT_DIM : UiColors.TEXT, false);
		g.text(font, reason, x + ROW_W - 8 - font.width(reason), y + 7, refusalColor(r), false);
	}

	/** The destination map: a dark chart with the systems as soft lights joined by faint lanes. */
	private void drawChart(GuiGraphicsExtractor g, int x0, int y0, int mouseX, int mouseY, float time) {
		int cx = x0 + CHART_X;
		int cy = y0 + CHART_Y;
		Ui.inset(g, cx - 2, cy - 2, CHART_W + 4, CHART_H + 4, 1f);
		g.blit(RenderPipelines.GUI_TEXTURED, CHART, cx, cy, 0f, 0f, CHART_W, CHART_H, CHART_TEX_W, CHART_TEX_H);

		for (int[] lane : LANES) {
			boolean open = !closed(lane[0]) && !closed(lane[1]);
			dottedLine(g, pointX(lane[0]), pointY(lane[0]), pointX(lane[1]), pointY(lane[1]), Ui.a(UiColors.HOLO, open ? 0.35f : 0.12f));
		}

		int hovered = pointAt(mouseX, mouseY);

		for (Row row : status.destinations()) {
			int d = row.destination();

			if (d >= POINTS.length) {
				continue;
			}

			drawPoint(g, d, refusalOf(row.refusal()), d == selected, d == hovered, time);
		}

		Ui.scanlines(g, cx, cy, CHART_W, CHART_H, Ui.a(UiColors.HOLO, 0.03f), time);
	}

	private void drawPoint(GuiGraphicsExtractor g, int d, Refusal r, boolean isSelected, boolean hovered, float time) {
		int px = pointX(d);
		int py = pointY(d);
		boolean dim = r == Refusal.LOCKED || r == Refusal.UNAVAILABLE;
		float glow = dim ? 0.3f : 0.75f + 0.25f * UiAnim.pulse(time + d * 17, 70f);
		int color = POINT_COLORS[d];

		// A soft light: stacked squares, faint and wide to bright and small.
		g.fill(px - 4, py - 4, px + 5, py + 5, Ui.a(color, 0.10f * glow));
		g.fill(px - 3, py - 2, px + 4, py + 3, Ui.a(color, 0.22f * glow));
		g.fill(px - 2, py - 3, px + 3, py + 4, Ui.a(color, 0.22f * glow));
		g.fill(px - 1, py - 1, px + 2, py + 2, Ui.a(color, 0.85f * glow));
		g.fill(px, py, px + 1, py + 1, Ui.a(0xFFFFFFFF, glow));

		if (isSelected || hovered) {
			// Bracket corners, as a targeting reticle.
			int c = isSelected ? UiColors.BRASS_LIGHT : Ui.a(UiColors.HOLO, 0.8f);
			int s = 7;
			g.fill(px - s, py - s, px - s + 3, py - s + 1, c);
			g.fill(px - s, py - s, px - s + 1, py - s + 3, c);
			g.fill(px + s - 2, py - s, px + s + 1, py - s + 1, c);
			g.fill(px + s, py - s, px + s + 1, py - s + 3, c);
			g.fill(px - s, py + s, px - s + 3, py + s + 1, c);
			g.fill(px - s, py + s - 2, px - s + 1, py + s + 1, c);
			g.fill(px + s - 2, py + s, px + s + 1, py + s + 1, c);
			g.fill(px + s, py + s - 2, px + s + 1, py + s + 1, c);
		}

		String label = Ui.fit(font, FlightHud.destinationName(d).getString(), 84);
		int lx = Mth.clamp(px - font.width(label) / 2, leftPos + CHART_X + 2, leftPos + CHART_X + CHART_W - 2 - font.width(label));
		int ly = py + 8 > topPos + CHART_Y + CHART_H - 10 ? py - 17 : py + 8;
		g.text(font, label, lx, ly, dim ? UiColors.TEXT_DIM : r == Refusal.HERE ? UiColors.HOLO : UiColors.TEXT_MUTED, true);
	}

	private boolean closed(int destination) {
		Refusal r = refusal(destination);
		return r == Refusal.LOCKED || r == Refusal.UNAVAILABLE;
	}

	/** A faint dotted line between two points, a dot every third pixel. */
	private static void dottedLine(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
		int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1)) / 3;

		for (int i = 1; i < steps; i++) {
			float t = (float) i / steps;
			int x = Math.round(Mth.lerp(t, x1, x2));
			int y = Math.round(Mth.lerp(t, y1, y2));
			g.fill(x, y, x + 1, y + 1, color);
		}
	}

	// ---------------------------------------------------------------- Hold

	private void drawHold(GuiGraphicsExtractor g, int x0, int y0) {
		int size = status.ship().holdSize();
		g.text(font, Ui.fit(font, Component.translatable("screen.oldrepublic.ship.hold.title").getString(), ShipMenu.HOLD_X - 14), x0 + 10, y0 + ShipMenu.HOLD_Y,
				UiColors.TEXT_MUTED, true);
		g.text(font, Ui.fit(font, Component.translatable("screen.oldrepublic.ship.slots", size).getString(), ShipMenu.HOLD_X - 14), x0 + 10,
				y0 + ShipMenu.HOLD_Y + 10, UiColors.TEXT_DIM, false);
		g.text(font, Ui.fit(font, playerInventoryTitle.getString(), ShipMenu.HOLD_X - 14), x0 + 10, y0 + ShipMenu.INV_Y, UiColors.TEXT_MUTED, true);

		for (int i = 0; i < ShipMenu.HOLD_SLOTS; i++) {
			int sx = x0 + ShipMenu.HOLD_X + i % 9 * 18 - 1;
			int sy = y0 + ShipMenu.HOLD_Y + i / 9 * 18 - 1;

			if (i < size) {
				Ui.sprite(g, Ui.SLOT, sx, sy, 18, 18, 1f);
			} else {
				// The cargo bay's slots, shuttered until the extension is fitted.
				g.fill(sx, sy, sx + 18, sy + 18, Ui.a(UiColors.STEEL_DARK, 0.9f));
				g.fill(sx + 2, sy + 5, sx + 16, sy + 6, Ui.a(UiColors.OUTLINE, 0.9f));
				g.fill(sx + 2, sy + 11, sx + 16, sy + 12, Ui.a(UiColors.OUTLINE, 0.9f));
			}
		}

		for (int row = 0; row < 4; row++) {
			int sy = y0 + (row == 3 ? ShipMenu.HOTBAR_Y : ShipMenu.INV_Y + row * 18) - 1;

			for (int col = 0; col < 9; col++) {
				Ui.sprite(g, Ui.SLOT, x0 + ShipMenu.INV_X + col * 18 - 1, sy, 18, 18, 1f);
			}
		}

		if (size < ShipMenu.HOLD_SLOTS) {
			g.text(font, Ui.fit(font, Component.translatable("screen.oldrepublic.ship.hold.locked").getString(), ShipMenu.HOLD_X - 14), x0 + 10,
					y0 + ShipMenu.HOLD_Y + 56, UiColors.TEXT_DIM, false);
		}
	}

	// ---------------------------------------------------------------- Crew

	private void drawCrew(GuiGraphicsExtractor g, int x0, int y0) {
		int x = x0 + LEFT_X;
		ShipStatusPayload.Ship ship = status.ship();
		Ui.labelValue(g, font, Component.translatable("screen.oldrepublic.ship.crew.title"),
				Component.translatable("screen.oldrepublic.ship.of", status.crew().size(), ship.crewLimit()), x, y0 + 42, ROW_W, UiColors.HOLO_BRIGHT,
				UiColors.TEXT);
		List<Member> crew = status.crew();

		if (crew.isEmpty()) {
			g.text(font, Ui.fit(font, Component.translatable("screen.oldrepublic.ship.crew.none").getString(), ROW_W), x, y0 + CREW_Y + 3, UiColors.TEXT_DIM,
					false);
		}

		for (int i = 0; i < crew.size() && i < CREW_ROWS; i++) {
			Member m = crew.get(i);
			int y = y0 + CREW_Y + i * MEMBER_STEP;
			Ui.inset(g, x - 2, y - 2, ROW_W + 4, 18, 0.8f);
			g.text(font, Ui.fit(font, m.name().isEmpty() ? Component.translatable("screen.oldrepublic.ship.crew.unknown").getString() : m.name(), 110), x + 4,
					y + 3, UiColors.TEXT, false);
			String key = m.aboard() ? "aboard" : m.online() ? "ashore" : "offline";
			int color = m.aboard() ? UiColors.HOLO : m.online() ? UiColors.TEXT_MUTED : UiColors.TEXT_DIM;
			g.text(font, Component.translatable("screen.oldrepublic.ship.crew." + key), x + 120, y + 3, color, false);
		}

		Ui.header(g, font, Component.translatable("screen.oldrepublic.ship.crew.nearby"), x, y0 + NEARBY_Y - 15, ROW_W, UiAnim.time(0f));
		List<Member> nearby = status.nearby();

		if (nearby.isEmpty()) {
			g.text(font, Ui.fit(font, Component.translatable("screen.oldrepublic.ship.crew.nobody").getString(), ROW_W), x, y0 + NEARBY_Y + 3, UiColors.TEXT_DIM,
					false);
		} else if (crew.size() >= ship.crewLimit()) {
			g.text(font, Ui.fit(font, Component.translatable("screen.oldrepublic.ship.crew.full").getString(), 120), x + 90, y0 + NEARBY_Y - 15,
					UiColors.AMBER, false);
		}

		for (int i = 0; i < nearby.size() && i < NEARBY_ROWS; i++) {
			int y = y0 + NEARBY_Y + i * MEMBER_STEP;
			g.text(font, Ui.fit(font, nearby.get(i).name(), 190), x + 4, y + 3, UiColors.TEXT_MUTED, false);
		}
	}

	// ---------------------------------------------------------------- helpers

	private Refusal refusal(int destination) {
		if (status != null) {
			for (Row row : status.destinations()) {
				if (row.destination() == destination) {
					return refusalOf(row.refusal());
				}
			}
		}

		return Refusal.UNAVAILABLE;
	}

	private static Refusal refusalOf(int ordinal) {
		Refusal[] all = Refusal.values();
		return ordinal >= 0 && ordinal < all.length ? all[ordinal] : Refusal.UNAVAILABLE;
	}

	private static Component refusalText(Refusal r) {
		return Component.translatable(r == Refusal.NONE ? "screen.oldrepublic.ship.nav.ready" : r.langKey());
	}

	private static int refusalColor(Refusal r) {
		return switch (r) {
			case NONE -> UiColors.OK;
			case HERE -> UiColors.HOLO;
			case LOCKED, UNAVAILABLE -> UiColors.TEXT_DIM;
			default -> UiColors.AMBER;
		};
	}

	private static ShipState stateOf(int ordinal) {
		ShipState[] all = ShipState.values();
		return ordinal >= 0 && ordinal < all.length ? all[ordinal] : ShipState.HULK;
	}

	private static int stateColor(ShipState state) {
		return switch (state) {
			case READY -> UiColors.OK;
			case DISABLED -> UiColors.DANGER;
			case HULK, GROUNDED -> UiColors.AMBER;
			default -> UiColors.HOLO;
		};
	}

	private static int hullColor(int hull) {
		return hull < 20 ? UiColors.DANGER : hull < 40 ? UiColors.AMBER : UiColors.OK;
	}
}
