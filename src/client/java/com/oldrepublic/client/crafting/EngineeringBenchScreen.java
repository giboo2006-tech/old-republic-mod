package com.oldrepublic.client.crafting;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.ui.HoloButton;
import com.oldrepublic.client.ui.TabStrip;
import com.oldrepublic.client.ui.Ui;
import com.oldrepublic.client.ui.UiAnim;
import com.oldrepublic.client.ui.UiColors;
import com.oldrepublic.crafting.Crafting;
import com.oldrepublic.crafting.CraftingAttachments;
import com.oldrepublic.crafting.CraftingBalance;
import com.oldrepublic.crafting.CraftingData;
import com.oldrepublic.crafting.CraftingData.Progress;
import com.oldrepublic.crafting.Discipline;
import com.oldrepublic.crafting.Quality;
import com.oldrepublic.crafting.Salvage;
import com.oldrepublic.crafting.Schematic;
import com.oldrepublic.crafting.Schematics;
import com.oldrepublic.crafting.bench.BenchResult;
import com.oldrepublic.crafting.bench.BenchService;
import com.oldrepublic.crafting.bench.EngineeringBenchMenu;
import com.oldrepublic.crafting.bench.Ingredients;
import com.oldrepublic.crafting.network.BenchActionPayload;
import com.oldrepublic.equipment.mod.Mods;
import com.oldrepublic.equipment.mod.ModSlot;
import com.oldrepublic.registry.M4Items;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The Engineering Bench screen (spec 7.1), 272 x 204 in the style of the Lightsaber Workbench: a
 * framed durasteel panel with a tab strip.
 * <ul>
 * <li><b>Craft:</b> a filter chip and a grid of the schematics the player can make, the selected
 * schematic's inputs (have / need), output, quality odds, XP and the Masterwork guarantee, the
 * bench's 9-slot parts tray and the output slot, and Craft, Cancel and Break bundle beside the
 * inventory with the weld's progress bar.</li>
 * <li><b>Modify:</b> the gear and mod slots and the gear's sockets; a fitted mod is removed with its
 * socket's button.</li>
 * <li><b>Salvage:</b> the input slot and what salvaging it returns.</li>
 * <li><b>Disciplines:</b> the four disciplines with levels, XP bars and the next unlocks; choosing
 * (with a confirming second click) happens here only.</li>
 * </ul>
 * A trainer's commission opens the same screen with the Craft tab alone and no tray. Every action
 * is a {@code bench_action} payload validated on the server; odds, XP, pity, fee and salvage numbers
 * are the server's, through the menu's data slots.
 */
public class EngineeringBenchScreen extends AbstractContainerScreen<EngineeringBenchMenu> {
	private static final int WIDTH = 272;
	private static final int HEIGHT = 204;

	private static final int STRIP_X = 8;
	private static final int STRIP_Y = 17;
	private static final int STRIP_W = 256;

	private static final int FILTER_X = 8;
	private static final int FILTER_Y = 35;
	private static final int FILTER_W = 90;
	private static final int FILTER_H = 11;
	private static final int GRID_X = 8;
	private static final int GRID_Y = 48;
	private static final int COLS = 5;
	private static final int ROWS = 3;
	private static final int CELL = 18;

	private static final int DETAIL_X = 104;
	private static final int DETAIL_Y = 35;
	private static final int DETAIL_W = 96;
	private static final int DETAIL_H = 70;

	private static final int PANEL_X = 46;
	private static final int PANEL_W = 152;

	private static final int RIGHT_X = 202;
	private static final int RIGHT_W = 62;
	private static final int BUTTON_Y = 136;

	private static final int CARD_X = 8;
	private static final int CARD_W = 120;
	private static final int CARD_H = 16;
	private static final int CARD_STEP = 17;
	private static final int DISC_X = 132;
	private static final int DISC_W = 132;

	private static final int STATUS_TICKS = 100;
	private static final int CONFIRM_TICKS = 60;
	/** A legendary schematic's frame and lines (MILESTONE_5.md 11.1): the gold of a Legendary item's name. */
	private static final int LEGENDARY_GOLD = 0xFFFFAA00;
	private static final int LEGENDARY_GOLD_DIM = 0xFF8A5A0C;
	private static final String[] TAB_IDS = {"craft", "modify", "salvage", "disciplines"};

	private final TabStrip tabs;
	/** Whether the tabs were built for a commission (the mode arrives with the first data sync, after the screen opens). */
	private boolean builtCommission;
	private HoloButton craft;
	private HoloButton cancel;
	private HoloButton bundle;
	private HoloButton fit;
	private HoloButton salvage;
	private HoloButton choose;

	/** The craft filter: null for all, "common", or a discipline id. */
	private @Nullable String filter;
	private int scroll;
	private @Nullable Identifier selected;
	private Discipline discipline = Discipline.ARMSTECH;
	private int confirmUntil = Integer.MIN_VALUE;
	private int lastSeq = -1;
	private int statusUntil = Integer.MIN_VALUE;
	private BenchResult status = BenchResult.NONE;
	private int ticks;

	public EngineeringBenchScreen(EngineeringBenchMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, WIDTH, HEIGHT);
		this.titleLabelX = 10;
		this.titleLabelY = 6;
		this.inventoryLabelX = 34;
		this.inventoryLabelY = 107;
		this.tabs = new TabStrip(tabList(menu.isCommission()));
		this.builtCommission = menu.isCommission();
	}

	@Override
	protected void init() {
		super.init();
		int x = leftPos + RIGHT_X;
		craft = addRenderableWidget(new HoloButton(x, topPos + BUTTON_Y, RIGHT_W, 16,
				Component.translatable("screen.oldrepublic.bench.craft"),
				b -> craftSelected(), true));
		cancel = addRenderableWidget(new HoloButton(x, topPos + BUTTON_Y + 19, RIGHT_W, 16, Component.translatable("screen.oldrepublic.bench.cancel"),
				b -> send(EngineeringBenchMenu.TAB_CRAFT, "", BenchActionPayload.CANCEL)));
		bundle = addRenderableWidget(new HoloButton(x, topPos + BUTTON_Y + 38, RIGHT_W, 16, Component.translatable("screen.oldrepublic.bench.break_bundle"),
				b -> send(EngineeringBenchMenu.TAB_CRAFT, "", BenchActionPayload.BREAK_BUNDLE))
				.withTooltip(Component.translatable("screen.oldrepublic.bench.break_bundle.tip")));
		fit = addRenderableWidget(new HoloButton(x, topPos + BUTTON_Y, RIGHT_W, 16, Component.translatable("screen.oldrepublic.bench.fit"),
				b -> send(EngineeringBenchMenu.TAB_MODIFY, "", BenchActionPayload.FIT), true));
		salvage = addRenderableWidget(new HoloButton(x, topPos + BUTTON_Y, RIGHT_W, 16, Component.translatable("screen.oldrepublic.bench.salvage"),
				b -> send(EngineeringBenchMenu.TAB_SALVAGE, "", BenchActionPayload.SALVAGE), true));
		choose = addRenderableWidget(new HoloButton(x, topPos + BUTTON_Y, RIGHT_W, 16, Component.translatable("screen.oldrepublic.bench.choose"),
				b -> chooseDiscipline(), true));
		tabs.select(TAB_IDS[Mth.clamp(tab(), 0, TAB_IDS.length - 1)]);
		updateButtons();
	}

	private static List<TabStrip.Tab> tabList(boolean commission) {
		List<TabStrip.Tab> list = new ArrayList<>();

		if (commission) {
			list.add(new TabStrip.Tab(TAB_IDS[0], Component.translatable("screen.oldrepublic.bench.tab.commission")));
		} else {
			for (String id : TAB_IDS) {
				list.add(new TabStrip.Tab(id, Component.translatable("screen.oldrepublic.bench.tab." + id)));
			}
		}

		return list;
	}

	// ---------------------------------------------------------------- state and actions

	private CraftingData data() {
		return minecraft.player == null ? CraftingData.EMPTY : CraftingAttachments.get(minecraft.player);
	}

	private int tab() {
		return menu.isCommission() ? EngineeringBenchMenu.TAB_CRAFT : menu.tab();
	}

	private boolean crafting() {
		return menu.data(EngineeringBenchMenu.DATA_TOTAL) > 0;
	}

	private static void send(int tab, String id, int slot) {
		ClientPlayNetworking.send(new BenchActionPayload(tab, id, slot));
	}

	private void craftSelected() {
		if (selected != null) {
			send(EngineeringBenchMenu.TAB_CRAFT, selected.toString(), BenchActionPayload.CRAFT);
		}
	}

	private void select(@Nullable Schematic s) {
		selected = s == null ? null : s.id();
		send(EngineeringBenchMenu.TAB_CRAFT, s == null ? "" : s.id().toString(), BenchActionPayload.SELECT);
	}

	/** Test seam (the M4 client test steps): selects a schematic on the Craft tab, as a click on its cell does. */
	public void selectForTest(Identifier id) {
		select(Schematics.get(id));
	}

	private void chooseDiscipline() {
		if (ticks <= confirmUntil) {
			confirmUntil = Integer.MIN_VALUE;
			send(EngineeringBenchMenu.TAB_DISCIPLINES, discipline.id(), BenchActionPayload.CHOOSE);
		} else {
			confirmUntil = ticks + CONFIRM_TICKS;
		}
	}

	private void openTab(int index) {
		if (minecraft.player != null && minecraft.gameMode != null && menu.clickMenuButton(minecraft.player, index)) {
			minecraft.gameMode.handleInventoryButtonClick(menu.containerId, index);
		}
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		ticks++;
		int seq = menu.data(EngineeringBenchMenu.DATA_RESULT_SEQ);

		if (seq != lastSeq) {
			if (lastSeq != -1) {
				status = BenchResult.byOrdinal(menu.data(EngineeringBenchMenu.DATA_RESULT));
				statusUntil = ticks + STATUS_TICKS;
			}

			lastSeq = seq;
		}

		if (selected != null && Schematics.get(selected) == null) {
			selected = null;
		}

		updateButtons();
	}

	private void updateButtons() {
		int tab = tab();
		boolean commission = menu.isCommission();

		if (commission != builtCommission) {
			builtCommission = commission;
			tabs.setTabs(tabList(commission));
			tabs.select(TAB_IDS[0]);
			filter = null;
		}

		tabs.select(TAB_IDS[Mth.clamp(tab, 0, TAB_IDS.length - 1)]);

		craft.setMessage(Component.translatable(commission ? "screen.oldrepublic.bench.commission_button" : "screen.oldrepublic.bench.craft"));
		boolean busy = crafting();
		Schematic s = selected == null ? null : Schematics.get(selected);

		craft.visible = tab == EngineeringBenchMenu.TAB_CRAFT;
		craft.active = s != null && !busy && haveAll(s);
		cancel.visible = tab == EngineeringBenchMenu.TAB_CRAFT && !commission;
		cancel.active = busy;
		bundle.visible = tab == EngineeringBenchMenu.TAB_CRAFT && !commission;
		bundle.active = !busy;

		fit.visible = tab == EngineeringBenchMenu.TAB_MODIFY;
		fit.active = canFit();
		salvage.visible = tab == EngineeringBenchMenu.TAB_SALVAGE;
		salvage.active = Salvage.canSalvage(menu.getSlot(EngineeringBenchMenu.SALVAGE).getItem());

		CraftingData data = data();
		choose.visible = tab == EngineeringBenchMenu.TAB_DISCIPLINES && !data.has(discipline);
		choose.active = menu.data(EngineeringBenchMenu.DATA_SECOND_OPEN) != 0 && data.disciplines().size() < CraftingData.MAX_DISCIPLINES;
		choose.setMessage(Component.translatable(ticks <= confirmUntil ? "screen.oldrepublic.bench.confirm" : "screen.oldrepublic.bench.choose"));
	}

	private boolean canFit() {
		ItemStack gear = menu.getSlot(EngineeringBenchMenu.GEAR).getItem();
		ItemStack mod = menu.getSlot(EngineeringBenchMenu.MOD).getItem();

		if (gear.isEmpty() || mod.isEmpty()) {
			return false;
		}

		for (ModSlot slot : Mods.slotsOf(gear)) {
			if (Mods.fits(gear, slot, mod) && Mods.fitted(gear).get(slot).isEmpty()) {
				return true;
			}
		}

		return false;
	}

	/** The schematics listed on the Craft tab, after the filter. */
	private List<Schematic> listed() {
		List<Schematic> out = new ArrayList<>();
		CraftingData data = data();
		Discipline trainer = menu.commissionDiscipline();

		for (Schematic s : Schematics.all()) {
			boolean shown = menu.isCommission()
					? minecraft.player != null && BenchService.mayCommission(minecraft.player, s, trainer)
					: Crafting.craftRefusal(data, s) == null;

			if (shown && matchesFilter(s)) {
				out.add(s);
			}
		}

		return out;
	}

	private boolean matchesFilter(Schematic s) {
		if (filter == null) {
			return true;
		}

		return filter.equals(s.discipline().map(Discipline::id).orElse("common"));
	}

	/** The filter choices: all, common, and each discipline the player has (a commission: all only). */
	private List<@Nullable String> filters() {
		List<@Nullable String> out = new ArrayList<>();
		out.add(null);

		if (!menu.isCommission()) {
			out.add("common");

			for (Progress p : data().disciplines()) {
				out.add(p.discipline().id());
			}
		}

		return out;
	}

	private Component filterName(@Nullable String id) {
		if (id == null) {
			return Component.translatable("screen.oldrepublic.bench.filter.all");
		}

		return Component.translatable("discipline.oldrepublic." + id);
	}

	private List<ItemStack> sources() {
		return minecraft.player == null ? List.of() : Ingredients.sources(menu.isCommission() ? null : menu.trayContainer(), minecraft.player);
	}

	private boolean haveAll(Schematic s) {
		return Ingredients.has(sources(), s.inputs());
	}

	// ---------------------------------------------------------------- input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (tabs.mouseClicked(event)) {
			String id = tabs.selectedId();

			for (int i = 0; i < TAB_IDS.length; i++) {
				if (TAB_IDS[i].equals(id)) {
					AbstractWidget.playButtonClickSound(minecraft.getSoundManager());
					openTab(i);
				}
			}

			return true;
		}

		double mx = event.x() - leftPos;
		double my = event.y() - topPos;
		int tab = tab();

		if (tab == EngineeringBenchMenu.TAB_CRAFT) {
			if (inside(mx, my, FILTER_X, FILTER_Y, FILTER_W, FILTER_H)) {
				List<@Nullable String> choices = filters();
				int at = Math.max(0, choices.indexOf(filter));
				int step = event.button() == 1 ? -1 : 1;
				filter = choices.get(Math.floorMod(at + step, choices.size()));
				scroll = 0;
				AbstractWidget.playButtonClickSound(minecraft.getSoundManager());
				return true;
			}

			Schematic s = gridAt(mx, my);

			if (s != null && event.button() == 0) {
				AbstractWidget.playButtonClickSound(minecraft.getSoundManager());
				select(s);

				if (doubleClick && !crafting() && haveAll(s)) {
					craftSelected();
				}

				return true;
			}
		} else if (tab == EngineeringBenchMenu.TAB_MODIFY && event.button() == 0) {
			ModSlot remove = removeButtonAt(mx, my);

			if (remove != null) {
				AbstractWidget.playButtonClickSound(minecraft.getSoundManager());
				send(EngineeringBenchMenu.TAB_MODIFY, "", remove.ordinal());
				return true;
			}
		} else if (tab == EngineeringBenchMenu.TAB_DISCIPLINES && event.button() == 0) {
			for (Discipline d : Discipline.values()) {
				if (inside(mx, my, CARD_X, cardY(d), CARD_W, CARD_H)) {
					AbstractWidget.playButtonClickSound(minecraft.getSoundManager());
					discipline = d;
					confirmUntil = Integer.MIN_VALUE;
					return true;
				}
			}
		}

		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		double mx = mouseX - leftPos;
		double my = mouseY - topPos;

		if (tab() == EngineeringBenchMenu.TAB_CRAFT && inside(mx, my, GRID_X, GRID_Y, COLS * CELL + 4, ROWS * CELL)) {
			scroll = Mth.clamp(scroll - (int) Math.signum(scrollY), 0, maxScroll());
			return true;
		}

		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	private int maxScroll() {
		int rows = Mth.positiveCeilDiv(listed().size(), COLS);
		return Math.max(0, rows - ROWS);
	}

	private @Nullable Schematic gridAt(double mx, double my) {
		if (!inside(mx, my, GRID_X, GRID_Y, COLS * CELL, ROWS * CELL)) {
			return null;
		}

		int col = (int) (mx - GRID_X) / CELL;
		int row = (int) (my - GRID_Y) / CELL;
		int index = (scroll + row) * COLS + col;
		List<Schematic> list = listed();
		return index >= 0 && index < list.size() ? list.get(index) : null;
	}

	private @Nullable ModSlot removeButtonAt(double mx, double my) {
		ItemStack gear = menu.getSlot(EngineeringBenchMenu.GEAR).getItem();
		List<ModSlot> slots = gear.isEmpty() ? List.of() : Mods.slotsOf(gear);

		for (int i = 0; i < slots.size(); i++) {
			if (!Mods.fitted(gear).get(slots.get(i)).isEmpty() && inside(mx, my, PANEL_X + PANEL_W - 16, socketY(i) + 4, 10, 10)) {
				return slots.get(i);
			}
		}

		return null;
	}

	private static boolean inside(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	private static int cardY(Discipline d) {
		return 35 + d.ordinal() * CARD_STEP;
	}

	private static int socketY(int index) {
		return 50 + index * 18;
	}

	// ---------------------------------------------------------------- tooltips

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		double mx = mouseX - leftPos;
		double my = mouseY - topPos;
		Component tip = null;
		int tab = tab();

		if (hoveredSlot != null && !hoveredSlot.hasItem()) {
			String key = switch (hoveredSlot.index) {
				case EngineeringBenchMenu.OUTPUT -> "output";
				case EngineeringBenchMenu.GEAR -> "gear";
				case EngineeringBenchMenu.MOD -> "mod";
				case EngineeringBenchMenu.SALVAGE -> "salvage";
				default -> hoveredSlot.index < EngineeringBenchMenu.OUTPUT ? "tray" : null;
			};

			if (key != null) {
				tip = Component.translatable("screen.oldrepublic.bench.slot_hint." + key);
			}
		} else if (tab == EngineeringBenchMenu.TAB_CRAFT) {
			Schematic s = gridAt(mx, my);

			if (s != null) {
				tip = s.result().getHoverName().copy().append("\n").append(levelLine(s));

				if (Crafting.isLegendary(s)) {
					tip = tip.copy().append("\n").append(Component.translatable("screen.oldrepublic.bench.always_legendary").withStyle(ChatFormatting.GOLD));
				}
			} else if (inside(mx, my, FILTER_X, FILTER_Y, FILTER_W, FILTER_H)) {
				tip = Component.translatable("screen.oldrepublic.bench.filter.tip");
			} else {
				tip = inputTooltip(mx, my);
			}
		} else if (tab == EngineeringBenchMenu.TAB_MODIFY && removeButtonAt(mx, my) != null) {
			tip = Component.translatable("screen.oldrepublic.bench.remove");
		}

		if (tip != null) {
			graphics.setTooltipForNextFrame(font, Tooltip.splitTooltip(minecraft, tip), mouseX, mouseY);
		}
	}

	private @Nullable Component inputTooltip(double mx, double my) {
		Schematic s = selected == null ? null : Schematics.get(selected);

		if (s == null) {
			return null;
		}

		List<ItemStack> sources = sources();

		for (int i = 0; i < Math.min(4, s.inputs().size()); i++) {
			if (inside(mx, my, inputX(i), inputY(), 16, 16)) {
				Schematic.Input input = s.inputs().get(i);
				int have = Ingredients.count(sources, input);
				Component name = input.what().map(item -> new ItemStack(item).getHoverName(),
						tag -> Component.translatable("screen.oldrepublic.bench.any_of", Component.literal("#" + tag.location())));
				return name.copy().append("\n").append(Component.translatable("screen.oldrepublic.bench.have_need", have, input.count()));
			}
		}

		return null;
	}

	private static Component levelLine(Schematic s) {
		return s.discipline()
				.map(d -> Component.translatable("screen.oldrepublic.bench.level_of", Crafting.name(d), s.level()))
				.orElse(Component.translatable("discipline.oldrepublic.common"));
	}

	// ---------------------------------------------------------------- background

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractBackground(graphics, mouseX, mouseY, delta);
		float time = UiAnim.time(delta);
		int x0 = leftPos;
		int y0 = topPos;

		Ui.panel(graphics, x0, y0, imageWidth, imageHeight);
		tabs.render(graphics, font, x0 + STRIP_X, y0 + STRIP_Y, STRIP_W, mouseX, mouseY, delta);

		// Inventory slot recesses (rows at y 118, 136, 154 and the hotbar at 176).
		for (int row = 0; row < 4; row++) {
			int sy = y0 + 117 + row * 18 + (row == 3 ? 4 : 0);

			for (int col = 0; col < 9; col++) {
				Ui.sprite(graphics, Ui.SLOT, x0 + 33 + col * 18, sy, 18, 18, 1f);
			}
		}

		switch (tab()) {
			case EngineeringBenchMenu.TAB_MODIFY -> drawModify(graphics, x0, y0, time);
			case EngineeringBenchMenu.TAB_SALVAGE -> drawSalvage(graphics, x0, y0, time);
			case EngineeringBenchMenu.TAB_DISCIPLINES -> drawDisciplines(graphics, x0, y0, mouseX, mouseY, time);
			default -> drawCraft(graphics, x0, y0, mouseX, mouseY, time);
		}
	}

	// ---------------------------------------------------------------- craft tab

	private void drawCraft(GuiGraphicsExtractor g, int x0, int y0, int mouseX, int mouseY, float time) {
		List<Schematic> list = listed();
		scroll = Mth.clamp(scroll, 0, maxScroll());

		// Filter chip.
		boolean hoverFilter = inside(mouseX - x0, mouseY - y0, FILTER_X, FILTER_Y, FILTER_W, FILTER_H);
		g.fill(x0 + FILTER_X, y0 + FILTER_Y, x0 + FILTER_X + FILTER_W, y0 + FILTER_Y + FILTER_H, UiColors.OUTLINE);
		g.fill(x0 + FILTER_X + 1, y0 + FILTER_Y + 1, x0 + FILTER_X + FILTER_W - 1, y0 + FILTER_Y + FILTER_H - 1, hoverFilter ? UiColors.STEEL : UiColors.STEEL_DARK);
		String label = "◂ " + Ui.fit(font, filterName(filter).getString(), FILTER_W - 22) + " ▸";
		g.text(font, label, x0 + FILTER_X + (FILTER_W - font.width(label)) / 2, y0 + FILTER_Y + 2, hoverFilter ? UiColors.HOLO_BRIGHT : UiColors.TEXT_MUTED, false);

		// Schematic grid.
		Ui.inset(g, x0 + GRID_X - 1, y0 + GRID_Y - 1, COLS * CELL + 2, ROWS * CELL + 2, 1f);

		for (int row = 0; row < ROWS; row++) {
			for (int col = 0; col < COLS; col++) {
				int index = (scroll + row) * COLS + col;
				int cx = x0 + GRID_X + col * CELL;
				int cy = y0 + GRID_Y + row * CELL;

				if (index >= list.size()) {
					continue;
				}

				Schematic s = list.get(index);
				boolean isSelected = s.id().equals(selected);
				boolean hover = inside(mouseX - x0, mouseY - y0, GRID_X + col * CELL, GRID_Y + row * CELL, CELL, CELL);

				if (isSelected) {
					g.fill(cx, cy, cx + CELL, cy + CELL, Ui.a(UiColors.HOLO_DEEP, 0.9f));
					g.outline(cx, cy, CELL, CELL, Ui.a(UiColors.HOLO, UiAnim.flicker(time)));
				} else if (hover) {
					g.fill(cx, cy, cx + CELL, cy + CELL, Ui.a(UiColors.STEEL, 0.8f));
				}

				if (Crafting.isLegendary(s)) {
					// The gold frame (11.1), inside the cell so the selection's outline still shows; the icon sits over it.
					g.outline(cx + 1, cy + 1, CELL - 2, CELL - 2, Ui.a(LEGENDARY_GOLD, 0.65f + 0.35f * UiAnim.flicker(time)));
					g.outline(cx + 2, cy + 2, CELL - 4, CELL - 4, Ui.a(LEGENDARY_GOLD_DIM, 0.8f));
				}

				g.item(s.result(), cx + 1, cy + 1);

				if (!haveAll(s)) {
					// Missing inputs: a dim veil and a small amber corner.
					g.fill(cx + 1, cy + 1, cx + CELL - 1, cy + CELL - 1, 0x60101418);
					g.fill(cx + CELL - 4, cy + 1, cx + CELL - 1, cy + 4, UiColors.AMBER_DIM);
				}

				if (Crafting.isMasterworkOnly(s)) {
					g.fill(cx + 1, cy + CELL - 3, cx + CELL - 1, cy + CELL - 2, UiColors.BRASS_LIGHT);
				}
			}
		}

		if (list.isEmpty()) {
			List<String> lines = Ui.wrap(font, Component.translatable(menu.isCommission()
					? "screen.oldrepublic.bench.none_commission" : "screen.oldrepublic.bench.none").getString(), COLS * CELL - 6, 5);

			for (int i = 0; i < lines.size(); i++) {
				g.text(font, lines.get(i), x0 + GRID_X + 3, y0 + GRID_Y + 3 + i * 10, UiColors.TEXT_DIM, false);
			}
		}

		// Scroll bar.
		int max = maxScroll();

		if (max > 0) {
			int track = ROWS * CELL;
			int knob = Math.max(8, track * ROWS / (ROWS + max));
			int ky = y0 + GRID_Y + Math.round((track - knob) * (scroll / (float) max));
			g.fill(x0 + GRID_X + COLS * CELL + 1, y0 + GRID_Y, x0 + GRID_X + COLS * CELL + 2, y0 + GRID_Y + track, UiColors.STEEL);
			g.fill(x0 + GRID_X + COLS * CELL + 1, ky, x0 + GRID_X + COLS * CELL + 3, ky + knob, UiColors.HOLO_DIM);
		}

		drawDetails(g, x0, y0, time);

		if (!menu.isCommission()) {
			// Parts tray and output, framed in brass as the bench's own.
			Ui.inset(g, x0 + 204, y0 + 34, 58, 58, 1f);

			for (int row = 0; row < 3; row++) {
				for (int col = 0; col < 3; col++) {
					Ui.sprite(g, Ui.SLOT, x0 + 205 + col * 18, y0 + 35 + row * 18, 18, 18, 1f);
				}
			}

			int ox = x0 + 223;
			int oy = y0 + 95;
			Schematic chosen = selected == null ? null : Schematics.get(selected);
			boolean legendary = chosen != null && Crafting.isLegendary(chosen);
			g.fill(ox - 2, oy - 2, ox + 20, oy + 20, legendary ? Ui.a(LEGENDARY_GOLD, 0.75f + 0.25f * UiAnim.flicker(time)) : Ui.a(UiColors.BRASS, 0.9f));
			Ui.sprite(g, Ui.SOCKET, ox - 1, oy - 1, 20, 20, 1f);
			drawProgress(g, x0, y0, time);
		}
	}

	private void drawDetails(GuiGraphicsExtractor g, int x0, int y0, float time) {
		int x = x0 + DETAIL_X;
		int y = y0 + DETAIL_Y;
		Ui.inset(g, x - 2, y - 1, DETAIL_W + 2, DETAIL_H + 1, 1f);
		Schematic s = selected == null ? null : Schematics.get(selected);

		if (s == null) {
			List<String> lines = Ui.wrap(font, Component.translatable("screen.oldrepublic.bench.pick").getString(), DETAIL_W - 6, 5);

			for (int i = 0; i < lines.size(); i++) {
				g.text(font, lines.get(i), x + 2, y + 3 + i * 10, UiColors.TEXT_DIM, false);
			}

			return;
		}

		ItemStack out = s.result();
		String name = out.getHoverName().getString() + (out.getCount() > 1 ? " \u00D7" + out.getCount() : "");
		g.text(font, Ui.fit(font, name, DETAIL_W - 4), x + 2, y + 2, Crafting.isLegendary(s) ? LEGENDARY_GOLD : UiColors.TEXT, false);

		// Line 2: the discipline and level, then the XP per craft (or the commission's fee).
		Component second = menu.isCommission()
				? Component.translatable("screen.oldrepublic.bench.fee", menu.data(EngineeringBenchMenu.DATA_FEE))
				: Component.translatable("screen.oldrepublic.bench.xp", menu.data(EngineeringBenchMenu.DATA_XP));
		String level = levelLine(s).getString();
		String right = second.getString();
		int room = DETAIL_W - 8 - font.width(right);
		g.text(font, Ui.fit(font, level, room), x + 2, y + 11, UiColors.TEXT_DIM, false);
		g.text(font, right, x + DETAIL_W - 4 - font.width(right), y + 11, menu.isCommission() ? UiColors.AMBER : UiColors.HOLO, false);

		// Inputs: icon and have / need beneath.
		List<ItemStack> sources = sources();

		for (int i = 0; i < Math.min(4, s.inputs().size()); i++) {
			Schematic.Input input = s.inputs().get(i);
			int ix = x0 + inputX(i);
			int iy = y0 + inputY();
			ItemStack icon = icon(input, time);
			Ui.sprite(g, Ui.SLOT, ix - 1, iy - 1, 18, 18, 1f);

			if (!icon.isEmpty()) {
				g.item(icon, ix, iy);
			}

			int have = Ingredients.count(sources, input);
			String count = Math.min(have, 999) + "/" + input.count();
			int color = have >= input.count() ? UiColors.OK : UiColors.DANGER;
			g.text(font, count, ix + 8 - font.width(count) / 2, iy + 17, color, false);
		}

		if (s.inputs().size() > 4) {
			g.text(font, "+" + (s.inputs().size() - 4), x0 + inputX(4) - 4, y0 + inputY() + 4, UiColors.TEXT_DIM, false);
		}

		// Line 4: the quality odds.
		int fine = menu.data(EngineeringBenchMenu.DATA_FINE);
		int mw = menu.data(EngineeringBenchMenu.DATA_MASTERWORK);
		Component odds;

		if (menu.isCommission()) {
			odds = Component.translatable("screen.oldrepublic.bench.commission_quality");
		} else if (mw >= EngineeringBenchMenu.ODDS_ALWAYS_LEGENDARY) {
			odds = Component.translatable("screen.oldrepublic.bench.always_legendary");
		} else if (mw >= EngineeringBenchMenu.ODDS_ALWAYS_MASTERWORK) {
			odds = Component.translatable("screen.oldrepublic.bench.always_masterwork");
		} else if (fine < 0) {
			odds = Component.translatable("screen.oldrepublic.bench.no_quality");
		} else {
			odds = Component.translatable("screen.oldrepublic.bench.odds", percent(fine), percent(mw));
		}

		int oddsColor = mw >= EngineeringBenchMenu.ODDS_ALWAYS_LEGENDARY ? LEGENDARY_GOLD
				: mw >= EngineeringBenchMenu.ODDS_ALWAYS_MASTERWORK ? UiColors.BRASS_LIGHT : UiColors.TEXT_MUTED;
		g.text(font, Ui.fit(font, odds.getString(), DETAIL_W - 4), x + 2, y + 48, oddsColor, false);

		// Line 5: the Masterwork guarantee and the first craft's player XP (alternating when both apply).
		int pity = menu.data(EngineeringBenchMenu.DATA_PITY);
		int first = menu.data(EngineeringBenchMenu.DATA_FIRST_XP);
		Component pityLine = pity <= 0 ? null
				: pity == 1 ? Component.translatable("screen.oldrepublic.bench.pity_next") : Component.translatable("screen.oldrepublic.bench.pity", pity);
		Component firstLine = first > 0 ? Component.translatable("screen.oldrepublic.bench.first_craft", first) : null;
		Component last = pityLine != null && firstLine != null ? ((int) (time / 60f) % 2 == 0 ? pityLine : firstLine)
				: pityLine != null ? pityLine : firstLine;

		if (last != null) {
			g.text(font, Ui.fit(font, last.getString(), DETAIL_W - 4), x + 2, y + 57, last == pityLine ? UiColors.BRASS_LIGHT : UiColors.OK, false);
		}
	}

	private static String percent(int tenths) {
		return String.format(Locale.ROOT, tenths % 10 == 0 ? "%.0f%%" : "%.1f%%", tenths / 10f);
	}

	private static int inputX(int i) {
		return DETAIL_X + 2 + i * 23;
	}

	private static int inputY() {
		return DETAIL_Y + 22;
	}

	/** The icon of an input: the item, or a tag's members in turn. */
	private static ItemStack icon(Schematic.Input input, float time) {
		return input.what().map(item -> new ItemStack(item), tag -> {
			List<Item> members = new ArrayList<>();

			for (Iterator<Holder<Item>> it = BuiltInRegistries.ITEM.getTagOrEmpty(tag).iterator(); it.hasNext();) {
				members.add(it.next().value());
			}

			return members.isEmpty() ? ItemStack.EMPTY : new ItemStack(members.get((int) (time / 20f) % members.size()));
		});
	}

	private void drawProgress(GuiGraphicsExtractor g, int x0, int y0, float time) {
		int total = menu.data(EngineeringBenchMenu.DATA_TOTAL);
		int done = menu.data(EngineeringBenchMenu.DATA_PROGRESS);
		float fill = total > 0 ? Mth.clamp(done / (float) total, 0f, 1f) : 0f;
		int bx = x0 + RIGHT_X;
		int by = y0 + BUTTON_Y - 9;
		g.text(font, Component.translatable(total > 0 ? "screen.oldrepublic.bench.welding" : "screen.oldrepublic.bench.idle"), bx, y0 + 118,
				total > 0 ? UiColors.AMBER : UiColors.TEXT_DIM, false);
		Ui.bar(g, bx, by, RIGHT_W, 5, fill, total > 0 ? UiColors.AMBER_BRIGHT : UiColors.HOLO_BRIGHT, total > 0 ? UiColors.AMBER_DIM : UiColors.HOLO_DIM, 6, 1f);

		if (total > 0) {
			// The arc's bright tip running along the bar.
			int tx = bx + Math.round((RIGHT_W - 2) * fill);
			g.fill(tx, by - 1, tx + 2, by + 6, Ui.a(0xFFFFFFFF, UiAnim.flicker(time * 3f)));
		}
	}

	// ---------------------------------------------------------------- modify tab

	private void drawModify(GuiGraphicsExtractor g, int x0, int y0, float time) {
		slotFrame(g, x0 + 22, y0 + 52, "gear");
		slotFrame(g, x0 + 22, y0 + 84, "mod");
		Ui.inset(g, x0 + PANEL_X - 2, y0 + 34, PANEL_W + 4, 71, 1f);
		ItemStack gear = menu.getSlot(EngineeringBenchMenu.GEAR).getItem();

		if (gear.isEmpty()) {
			paragraph(g, Component.translatable("screen.oldrepublic.bench.modify.hint"), x0 + PANEL_X + 2, y0 + 38, PANEL_W - 4, 6);
			return;
		}

		g.text(font, Ui.fit(font, gear.getHoverName().getString(), PANEL_W - 4), x0 + PANEL_X + 2, y0 + 38, UiColors.TEXT, false);
		List<ModSlot> slots = Mods.slotsOf(gear);
		ItemStack mod = menu.getSlot(EngineeringBenchMenu.MOD).getItem();

		for (int i = 0; i < slots.size(); i++) {
			ModSlot slot = slots.get(i);
			ItemStack fitted = Mods.fitted(gear).get(slot);
			int sx = x0 + PANEL_X + 2;
			int sy = y0 + socketY(i);
			boolean target = !mod.isEmpty() && fitted.isEmpty() && Mods.fits(gear, slot, mod);
			Ui.sprite(g, Ui.SOCKET, sx - 1, sy - 1, 18, 18, 1f);

			if (target) {
				g.outline(sx - 2, sy - 2, 20, 20, Ui.a(UiColors.HOLO, UiAnim.flicker(time)));
			}

			if (!fitted.isEmpty()) {
				g.item(fitted, sx, sy);
			}

			int tx = sx + 21;
			g.text(font, Component.translatable("screen.oldrepublic.bench.socket." + slot.id()), tx, sy, UiColors.TEXT_MUTED, false);
			String what = fitted.isEmpty() ? Component.translatable("screen.oldrepublic.bench.socket.empty").getString() : fitted.getHoverName().getString();
			g.text(font, Ui.fit(font, what, PANEL_W - 44), tx, sy + 9, fitted.isEmpty() ? UiColors.TEXT_DIM : UiColors.TEXT, false);

			if (!fitted.isEmpty()) {
				int bx = x0 + PANEL_X + PANEL_W - 16;
				int by = sy + 4;
				g.fill(bx, by, bx + 10, by + 10, UiColors.OUTLINE);
				g.fill(bx + 1, by + 1, bx + 9, by + 9, UiColors.STEEL_DARK);
				g.text(font, "×", bx + 2, by + 1, UiColors.DANGER, false);
			}
		}

		if (slots.isEmpty()) {
			paragraph(g, Component.translatable("screen.oldrepublic.bench.modify.no_sockets"), x0 + PANEL_X + 2, y0 + 50, PANEL_W - 4, 4);
		}
	}

	// ---------------------------------------------------------------- salvage tab

	private void drawSalvage(GuiGraphicsExtractor g, int x0, int y0, float time) {
		slotFrame(g, x0 + 22, y0 + 64, "salvage");
		Ui.inset(g, x0 + PANEL_X - 2, y0 + 34, PANEL_W + 4, 71, 1f);
		ItemStack input = menu.getSlot(EngineeringBenchMenu.SALVAGE).getItem();
		int pct = menu.data(EngineeringBenchMenu.DATA_SALVAGE_PCT);
		g.text(font, Component.translatable("screen.oldrepublic.bench.salvage.returns", pct), x0 + PANEL_X + 2, y0 + 38, UiColors.TEXT_MUTED, false);

		if (input.isEmpty()) {
			paragraph(g, Component.translatable("screen.oldrepublic.bench.salvage.hint"), x0 + PANEL_X + 2, y0 + 50, PANEL_W - 4, 5);
			return;
		}

		if (Salvage.tooRare(input)) {
			paragraph(g, Component.translatable("screen.oldrepublic.bench.salvage.too_rare"), x0 + PANEL_X + 2, y0 + 50, PANEL_W - 4, 5);
			return;
		}

		if (!Salvage.canSalvage(input)) {
			paragraph(g, Component.translatable("screen.oldrepublic.bench.salvage.cannot"), x0 + PANEL_X + 2, y0 + 50, PANEL_W - 4, 5);
			return;
		}

		List<ItemStack> back = Salvage.returns(input, pct / 100f);

		for (int i = 0; i < Math.min(16, back.size()); i++) {
			int ix = x0 + PANEL_X + 2 + (i % 8) * 18;
			int iy = y0 + 49 + (i / 8) * 18;
			ItemStack stack = back.get(i);
			Ui.sprite(g, Ui.SLOT, ix - 1, iy - 1, 18, 18, 1f);
			g.item(stack, ix, iy);

			if (stack.getCount() > 1) {
				String n = String.valueOf(stack.getCount());
				g.text(font, n, ix + 17 - font.width(n), iy + 9, UiColors.TEXT, true);
			}

			if (Crafting.quality(stack) == Quality.MASTERWORK || stack.is(M4Items.MASTERWORK_FRAGMENT)) {
				g.outline(ix - 1, iy - 1, 18, 18, Ui.a(UiColors.BRASS_LIGHT, UiAnim.flicker(time)));
			}
		}

		int xp = menu.data(EngineeringBenchMenu.DATA_SALVAGE_XP);
		Component line = xp > 0 ? Component.translatable("screen.oldrepublic.bench.salvage.xp", xp) : Component.translatable("screen.oldrepublic.bench.salvage.no_xp");
		g.text(font, Ui.fit(font, line.getString(), PANEL_W - 4), x0 + PANEL_X + 2, y0 + 92, xp > 0 ? UiColors.OK : UiColors.TEXT_DIM, false);
	}

	// ---------------------------------------------------------------- disciplines tab

	private void drawDisciplines(GuiGraphicsExtractor g, int x0, int y0, int mouseX, int mouseY, float time) {
		CraftingData data = data();

		for (Discipline d : Discipline.values()) {
			int cx = x0 + CARD_X;
			int cy = y0 + cardY(d);
			Progress p = data.progress(d).orElse(null);
			boolean isSelected = d == discipline;
			boolean hover = inside(mouseX - x0, mouseY - y0, CARD_X, cardY(d), CARD_W, CARD_H);
			g.fill(cx, cy, cx + CARD_W, cy + CARD_H, UiColors.OUTLINE);
			g.fill(cx + 1, cy + 1, cx + CARD_W - 1, cy + CARD_H - 1, isSelected ? UiColors.HOLO_DEEP : hover ? UiColors.STEEL : UiColors.STEEL_DARK);

			if (isSelected) {
				g.fill(cx + 1, cy + 1, cx + 2, cy + CARD_H - 1, Ui.a(UiColors.HOLO, UiAnim.flicker(time)));
			}

			g.text(font, Crafting.name(d), cx + 5, cy + 4, p != null ? UiColors.TEXT : UiColors.TEXT_MUTED, false);
			String right;
			int rc;

			if (p != null) {
				right = Component.translatable("screen.oldrepublic.bench.level", p.level()).getString();
				rc = UiColors.OK;
			} else if (data.unlearned().contains(d)) {
				right = Component.translatable("screen.oldrepublic.bench.unlearned").getString();
				rc = UiColors.AMBER;
			} else {
				right = "";
				rc = UiColors.TEXT_DIM;
			}

			g.text(font, right, cx + CARD_W - 4 - font.width(right), cy + 4, rc, false);
		}

		drawDisciplineDetail(g, x0, y0, data, time);
	}

	private void drawDisciplineDetail(GuiGraphicsExtractor g, int x0, int y0, CraftingData data, float time) {
		int x = x0 + DISC_X;
		int y = y0 + 35;
		Ui.inset(g, x - 2, y - 1, DISC_W + 2, 71, 1f);
		Discipline d = discipline;
		Progress p = data.progress(d).orElse(null);
		g.text(font, Crafting.name(d), x + 2, y + 2, UiColors.HOLO_BRIGHT, false);

		if (p != null) {
			int[] thresholds = DisciplineXp.THRESHOLDS;
			int from = thresholds[Mth.clamp(p.level() - 1, 0, thresholds.length - 1)];
			boolean max = p.level() >= thresholds.length;
			int to = max ? from : thresholds[p.level()];
			float fill = max ? 1f : Mth.clamp((p.xp() - from) / (float) Math.max(1, to - from), 0f, 1f);
			String lv = Component.translatable("screen.oldrepublic.bench.level", p.level()).getString();
			g.text(font, lv, x + DISC_W - 4 - font.width(lv), y + 2, UiColors.OK, false);
			Ui.bar(g, x + 2, y + 13, DISC_W - 6, 5, fill, UiColors.HOLO_BRIGHT, UiColors.HOLO_DIM, 8, 1f);
			String xp = max ? Component.translatable("screen.oldrepublic.bench.max_level").getString()
					: Component.translatable("screen.oldrepublic.bench.xp_of", p.xp(), to).getString();
			g.text(font, xp, x + 2, y + 20, UiColors.TEXT_DIM, false);
		}

		int ty = paragraph(g, Component.translatable("discipline.oldrepublic." + d.id() + ".makes"), x + 2, y + (p != null ? 30 : 13), DISC_W - 6, p != null ? 2 : 3);

		// The next unlocks: the auto schematics at the next level, as icons.
		int next = p == null ? 1 : p.level() + 1;
		List<ItemStack> unlocks = new ArrayList<>();

		for (Schematic s : Schematics.all()) {
			if (s.discipline().orElse(null) == d && s.level() == next && s.learned() == Schematic.Learned.AUTO) {
				unlocks.add(s.result());
			}
		}

		if (!unlocks.isEmpty() && next <= DisciplineXp.THRESHOLDS.length) {
			g.text(font, Component.translatable("screen.oldrepublic.bench.next_unlocks", next), x + 2, ty + 1, UiColors.TEXT_MUTED, false);

			for (int i = 0; i < Math.min(7, unlocks.size()); i++) {
				g.item(unlocks.get(i), x + 2 + i * 18, ty + 10);
			}
		}

		// Beside the inventory: why the choice is closed, if it is.
		if (p == null) {
			Component why = null;

			if (data.disciplines().size() >= CraftingData.MAX_DISCIPLINES) {
				why = Component.translatable("screen.oldrepublic.bench.full");
			} else if (menu.data(EngineeringBenchMenu.DATA_SECOND_OPEN) == 0) {
				why = Component.translatable("screen.oldrepublic.bench.second_locked");
			} else if (ticks <= confirmUntil) {
				why = Component.translatable("screen.oldrepublic.bench.confirm_hint", Crafting.name(d));
			}

			if (why != null) {
				paragraph(g, why, x0 + RIGHT_X, y0 + BUTTON_Y + 20, RIGHT_W, 4);
			}
		}
	}

	// ---------------------------------------------------------------- shared drawing

	private void slotFrame(GuiGraphicsExtractor g, int x, int y, String key) {
		Ui.sprite(g, Ui.SOCKET, x - 2, y - 2, 20, 20, 1f);
		String label = Component.translatable("screen.oldrepublic.bench.slot." + key).getString();
		g.text(font, label, x + 8 - font.width(label) / 2, y - 11, UiColors.TEXT_DIM, false);
	}

	private int paragraph(GuiGraphicsExtractor g, Component text, int x, int y, int w, int maxLines) {
		for (String line : Ui.wrap(font, text.getString(), w, maxLines)) {
			g.text(font, line, x, y, UiColors.TEXT_DIM, false);
			y += 9;
		}

		return y;
	}

	// ---------------------------------------------------------------- labels (relative to the panel)

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		Ui.header(graphics, font, title, titleLabelX, titleLabelY, imageWidth - 2 * titleLabelX, UiAnim.time(0f));
		graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, UiColors.TEXT_MUTED, true);

		if (ticks <= statusUntil && status != BenchResult.NONE) {
			int color = status == BenchResult.LEGENDARY ? LEGENDARY_GOLD : status.good() ? UiColors.OK : UiColors.AMBER;
			String text = Ui.fit(font, Component.translatable(status.translationKey()).getString(), imageWidth - 96 - 8);
			graphics.fill(96, inventoryLabelY + 2, 99, inventoryLabelY + 5, color);
			graphics.text(font, text, 102, inventoryLabelY, color, false);
		}
	}

	/** The discipline XP thresholds shown on the bar (the client has no balance file: the defaults, 7.2). */
	private static final class DisciplineXp {
		static final int[] THRESHOLDS = new CraftingBalance().levelXp;
	}
}
