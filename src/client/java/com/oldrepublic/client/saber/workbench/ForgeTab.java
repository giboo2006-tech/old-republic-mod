package com.oldrepublic.client.saber.workbench;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.ui.Ui;
import com.oldrepublic.client.ui.UiAnim;
import com.oldrepublic.client.ui.UiColors;
import com.oldrepublic.crafting.Schematic;
import com.oldrepublic.crafting.bench.Ingredients;
import com.oldrepublic.saber.HiltMaterial;
import com.oldrepublic.saber.HiltParts;
import com.oldrepublic.saber.HiltSet;
import com.oldrepublic.saber.SaberBalance;
import com.oldrepublic.saber.SaberComponents;
import com.oldrepublic.saber.SaberItems;
import com.oldrepublic.saber.SaberStats;
import com.oldrepublic.saber.forge.ForgeActionPayload;
import com.oldrepublic.saber.forge.ForgeAttachments;
import com.oldrepublic.saber.forge.ForgeData;
import com.oldrepublic.saber.forge.ForgeRules;
import com.oldrepublic.saber.workbench.LightsaberWorkbenchMenu;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The workbench's Forge tab (MILESTONE_7.md 5.3), drawn and clicked through
 * {@link LightsaberWorkbenchScreen} in the panel the Tune tab uses. Left: the designs grouped by slot,
 * one row each for emitters, grips and pommels (the craftable designs, then a set or legendary part
 * sitting in that row's socket; a design the player does not know shows locked; every tile shows the
 * chosen material), and under them the eight material chips (a chip the player cannot afford says why
 * in its tooltip) and the chosen part's name. Right: the chosen part's 3D icon in the chosen material on
 * the forge's anvil, larger than a slot, and its stat change against the part in its socket. A status
 * line says what is missing. In the console: the cost against what the player carries, above the
 * Forge, Re-forge and Study buttons (the screen's). The tab checks what the server will check
 * ({@link ForgeRules}), so a button is lit only when its action can go through; the credits are counted
 * from the inventory (the server also counts a near ship's hold, as for the Tune tab).
 */
final class ForgeTab {
	// Layout, relative to the screen's top-left corner, inside the Tune tab's panel.
	private static final int PANEL_X = 61;
	private static final int PANEL_Y = 18;
	private static final int PANEL_W = 203;
	private static final int PANEL_H = 93;
	/** The design rows (emitters, grips, pommels); neighbouring tiles share their 1 px rims. */
	private static final int GRID_X = 65;
	private static final int GRID_Y = 21;
	private static final int TILE = 18;
	private static final int TILE_STEP = 17;
	/** The material chips, a row of eight under the designs. */
	private static final int CHIPS_X = 65;
	private static final int CHIPS_Y = 76;
	private static final int CHIP = 14;
	private static final int CHIP_STEP = 15;
	/** The chosen part's name, under the chips. */
	private static final int NAME_Y = 91;
	private static final int NAME_W = 120;
	/** The forge well: the chosen part's 3D icon at {@link #ICON_SCALE} times a slot's size, on the anvil. */
	private static final int WELL_X = 189;
	private static final int WELL_Y = 20;
	private static final int WELL_W = 71;
	private static final int WELL_H = 50;
	private static final int ICON_SCALE = 3;
	private static final int ANVIL_W = 44;
	private static final int ANVIL_H = 6;
	/** The stat change under the well: two cells a line (damage, speed; reach, guard; parry). */
	private static final int STATS_X = 191;
	private static final int STATS_Y = 72;
	private static final int STAT_CELL_W = 35;
	private static final int STAT_LINE_H = 9;
	private static final int STAT_ICON = 7;
	private static final int STATUS_Y = PANEL_Y + PANEL_H - 11;
	/** The console: the cost, then the three buttons (the screen places them at these rows). */
	private static final int COST_Y = 112;
	static final int BUTTON_Y = 149;
	static final int BUTTON_STEP = 15;
	static final int BUTTON_H = 14;
	/** How long the well and the socket glint after a Forge or a Study, in ticks. */
	private static final int GLINT_TICKS = 24;

	private static final Identifier TILE_SPRITE = OldRepublicMod.id("forge/tile");
	private static final Identifier TILE_SELECTED = OldRepublicMod.id("forge/tile_selected");
	private static final Identifier TILE_LOCKED = OldRepublicMod.id("forge/tile_locked");
	private static final Identifier LOCK = OldRepublicMod.id("forge/lock");
	private static final Identifier SOCKET_MARK = OldRepublicMod.id("forge/socket_mark");
	private static final Identifier CHIP_SPRITE = OldRepublicMod.id("forge/chip");
	private static final Identifier CHIP_SELECTED = OldRepublicMod.id("forge/chip_selected");
	private static final Identifier PLATE = OldRepublicMod.id("forge/plate");
	private static final Identifier ANVIL = OldRepublicMod.id("forge/anvil");
	/** By stat, in the screen's order: damage, speed, reach, guard, parry. */
	private static final Identifier[] STAT_ICONS = {OldRepublicMod.id("forge/stat_damage"), OldRepublicMod.id("forge/stat_speed"),
			OldRepublicMod.id("forge/stat_reach"), OldRepublicMod.id("forge/stat_guard"), OldRepublicMod.id("forge/stat_parry")};
	private static final String[] STAT_KEYS = {"damage", "speed", "reach", "stamina", "parry"};
	/** The decimals each stat shows, as the Assemble tab's numbers. */
	private static final int[] STAT_DECIMALS = {2, 2, 2, 0, 1};

	/** Default balance for the stat change (the server's stat numbers are not sent; its costs are). */
	private static final SaberBalance DEFAULT_BALANCE = new SaberBalance();
	private static final String KEY = "screen.oldrepublic.workbench.forge.";

	/** The design and material last chosen, kept while the game runs (as the screen's last tab). */
	private static HiltParts.@Nullable Part lastDesign;
	private static HiltMaterial lastMaterial = HiltMaterial.STEEL;
	private static long glintAt = Long.MIN_VALUE;
	private static int glintSocket = -1;
	private static int glintTint = UiColors.HOLO_BRIGHT;

	private final LightsaberWorkbenchMenu menu;

	ForgeTab(LightsaberWorkbenchMenu menu) {
		this.menu = menu;
	}

	/** What the checks need, read once a frame: the known designs, the stacks that may pay, the credits carried and the costs. */
	private record Ctx(ForgeData known, List<ItemStack> sources, long credits, ForgeRules.Costs costs) {
	}

	private Ctx ctx() {
		Player player = Minecraft.getInstance().player;

		if (player == null) {
			return new Ctx(ForgeData.EMPTY, List.of(), 0L, menu.forgeCosts());
		}

		return new Ctx(ForgeAttachments.get(player), Ingredients.sources(null, player), LightsaberWorkbenchScreen.carriedCredits(player), menu.forgeCosts());
	}

	// ---------------------------------------------------------------- the choice

	/** Chooses {@code design} (its row's slot follows) and {@code material}, as clicks on their tile and chip would (client tests). */
	static void select(HiltParts.@Nullable Part design, HiltMaterial material) {
		lastDesign = design;
		lastMaterial = material;
	}

	/** The chosen material. */
	HiltMaterial material() {
		return lastMaterial;
	}

	/**
	 * The chosen design while its tile shows (a craftable design, or the part in its socket), else the
	 * first part in a socket, else the first design the player knows.
	 */
	HiltParts.@Nullable Part design() {
		if (lastDesign != null && shown(lastDesign)) {
			return lastDesign;
		}

		for (HiltParts.Slot slot : HiltParts.Slot.values()) {
			HiltParts.Part inSocket = socketPart(slot);

			if (inSocket != null) {
				return inSocket;
			}
		}

		Player player = Minecraft.getInstance().player;
		ForgeData known = player == null ? ForgeData.EMPTY : ForgeAttachments.get(player);

		for (HiltParts.Slot slot : HiltParts.Slot.values()) {
			for (HiltParts.Part part : ForgeRules.craftable(slot)) {
				if (known.knows(part)) {
					return part;
				}
			}
		}

		return null;
	}

	private boolean shown(HiltParts.Part design) {
		return SaberItems.isCraftableDesign(design) || socketPart(HiltParts.slot(design)) == design;
	}

	/** A row's designs: the craftable ones of its slot, then the part in its socket when that is a set or legendary design. */
	private List<HiltParts.Part> row(HiltParts.Slot slot) {
		List<HiltParts.Part> out = new ArrayList<>(ForgeRules.craftable(slot));
		HiltParts.Part inSocket = socketPart(slot);

		if (inSocket != null && !out.contains(inSocket)) {
			out.add(inSocket);
		}

		return out;
	}

	private ItemStack socketStack(HiltParts.Slot slot) {
		return menu.getSlot(ForgeRules.socket(slot)).getItem();
	}

	private HiltParts.@Nullable Part socketPart(HiltParts.Slot slot) {
		return ForgeRules.part(socketStack(slot));
	}

	// ---------------------------------------------------------------- the actions

	boolean canForge() {
		HiltParts.Part design = design();
		Ctx c = ctx();
		return design != null && ForgeRules.checkForge(design, lastMaterial, c.known(), c.sources(), c.credits(), c.costs()) == ForgeRules.Result.OK;
	}

	boolean canReforge() {
		HiltParts.Part design = design();
		Ctx c = ctx();
		return design != null && ForgeRules.checkReforge(socketStack(HiltParts.slot(design)), design, lastMaterial, c.sources(), c.credits(), c.costs())
				== ForgeRules.Result.OK;
	}

	boolean canStudy() {
		HiltParts.Part design = design();

		if (design == null) {
			return false;
		}

		ItemStack socket = socketStack(HiltParts.slot(design));
		return ForgeRules.part(socket) == design && ForgeRules.checkStudy(socket, ctx().known()) == ForgeRules.Result.OK;
	}

	/** Sends the chosen design and material with {@code action} ({@code ForgeActionPayload.STUDY}, {@code FORGE} or {@code REFORGE}); the server decides. */
	void send(int action) {
		HiltParts.Part design = design();

		if (design != null) {
			ClientPlayNetworking.send(new ForgeActionPayload(action, design.itemId(), lastMaterial.ordinal()));
		}
	}

	// ---------------------------------------------------------------- clicks and tooltips

	/** Chooses the design or material under the mouse; true when the click was on one. */
	boolean mouseClicked(double mouseX, double mouseY, int x0, int y0) {
		HiltParts.Part tile = tileAt(mouseX, mouseY, x0, y0);

		if (tile != null) {
			lastDesign = tile;
			return true;
		}

		HiltMaterial chip = chipAt(mouseX, mouseY, x0, y0);

		if (chip != null) {
			lastMaterial = chip;
			return true;
		}

		return false;
	}

	private HiltParts.@Nullable Part tileAt(double mouseX, double mouseY, int x0, int y0) {
		for (HiltParts.Slot slot : HiltParts.Slot.values()) {
			List<HiltParts.Part> row = row(slot);
			int ty = y0 + GRID_Y + slot.ordinal() * TILE_STEP;

			for (int i = 0; i < row.size(); i++) {
				int tx = x0 + GRID_X + i * TILE_STEP;

				if (mouseX >= tx && mouseX < tx + TILE_STEP && mouseY >= ty && mouseY < ty + TILE_STEP) {
					return row.get(i);
				}
			}
		}

		return null;
	}

	private static @Nullable HiltMaterial chipAt(double mouseX, double mouseY, int x0, int y0) {
		int cy = y0 + CHIPS_Y;

		if (mouseY < cy || mouseY >= cy + CHIP) {
			return null;
		}

		for (HiltMaterial material : HiltMaterial.values()) {
			int cx = x0 + CHIPS_X + material.ordinal() * CHIP_STEP;

			if (mouseX >= cx && mouseX < cx + CHIP) {
				return material;
			}
		}

		return null;
	}

	private static int statAt(double mouseX, double mouseY, int x0, int y0) {
		for (int i = 0; i < STAT_KEYS.length; i++) {
			int sx = x0 + STATS_X + (i % 2) * STAT_CELL_W;
			int sy = y0 + STATS_Y + (i / 2) * STAT_LINE_H;

			if (mouseX >= sx && mouseX < sx + STAT_CELL_W && mouseY >= sy - 1 && mouseY < sy + STAT_LINE_H - 1) {
				return i;
			}
		}

		return -1;
	}

	/** The tooltip under the mouse (a design, a material, the well or a stat), or null. */
	@Nullable Component tooltip(int mouseX, int mouseY, int x0, int y0) {
		Ctx c = ctx();
		HiltParts.Part tile = tileAt(mouseX, mouseY, x0, y0);

		if (tile != null) {
			return designTip(tile, c);
		}

		HiltMaterial chip = chipAt(mouseX, mouseY, x0, y0);

		if (chip != null) {
			return chipTip(chip, c);
		}

		HiltParts.Part design = design();
		int wx = x0 + WELL_X;
		int wy = y0 + WELL_Y;

		if (design != null && mouseX >= wx && mouseX < wx + WELL_W && mouseY >= wy && mouseY < wy + WELL_H) {
			return lines(name(design), Component.translatable(KEY + "chip.name", materialName(lastMaterial), lastMaterial.tier()));
		}

		int stat = statAt(mouseX, mouseY, x0, y0);

		if (design != null && stat >= 0) {
			float[] before = values(benchBefore());
			float[] after = values(benchAfter(design));
			boolean empty = socketStack(HiltParts.slot(design)).isEmpty();
			return lines(Component.translatable(KEY + "stat.tip", statName(stat), format(stat, before[stat], false), format(stat, after[stat], false)),
					Component.translatable(KEY + (empty ? "stat.vs_empty" : "stat.vs_socket")));
		}

		return null;
	}

	private Component designTip(HiltParts.Part design, Ctx c) {
		String kind = SaberItems.isLegendary(design) ? "tile.legendary"
				: HiltSet.of(design) != null ? "tile.set"
				: c.known().knows(design) ? "tile.known" : "tile.unknown";
		Component tip = lines(name(design), Component.translatable(KEY + kind));
		ItemStack socket = socketStack(HiltParts.slot(design));

		if (ForgeRules.part(socket) == design) {
			tip = lines(tip, Component.translatable(KEY + "tile.socket", materialName(SaberComponents.material(socket))));
		}

		return tip;
	}

	/** A chip's tooltip: the material and its tier, what the chosen design costs in it, and what is short. */
	private Component chipTip(HiltMaterial material, Ctx c) {
		Component tip = Component.translatable(KEY + "chip.name", materialName(material), material.tier());
		HiltParts.Part design = design();

		if (design == null) {
			return tip;
		}

		int units = c.costs().units(design);
		int credits = c.costs().credits(material);
		Item unit = ForgeRules.unitItem(material);
		Component unitName = unit == null ? Component.literal(material.itemId().toString()) : new ItemStack(unit).getHoverName();
		tip = lines(tip, units > 0 ? Component.translatable(KEY + "chip.cost", units, unitName, credits) : Component.translatable(KEY + "chip.cost_credits", credits));
		List<Schematic.Input> inputs = ForgeRules.inputs(design, material, c.costs());
		int have = inputs == null || inputs.isEmpty() ? 0 : Ingredients.count(c.sources(), inputs.get(0));

		if (units > 0 && (inputs == null || have < units)) {
			tip = lines(tip, Component.translatable(KEY + "chip.short_units", have, units, unitName).withStyle(ChatFormatting.GOLD));
		}

		if (c.credits() < credits) {
			tip = lines(tip, Component.translatable(KEY + "chip.short_credits", c.credits(), credits).withStyle(ChatFormatting.GOLD));
		}

		ItemStack socket = socketStack(HiltParts.slot(design));

		if (ForgeRules.part(socket) == design && SaberComponents.material(socket) == material) {
			tip = lines(tip, Component.translatable(KEY + "chip.current"));
		}

		return tip;
	}

	private static Component lines(Component first, Component second) {
		return first.copy().append("\n").append(second);
	}

	// ---------------------------------------------------------------- the stat change

	/** The bench's three part sockets as they are, as {@link ForgeRules#benchStats} counts them. */
	private SaberStats benchBefore() {
		return ForgeRules.benchStats(socketStack(HiltParts.Slot.EMITTER), socketStack(HiltParts.Slot.GRIP), socketStack(HiltParts.Slot.POMMEL), DEFAULT_BALANCE);
	}

	/** The bench with {@code design} in the chosen material in its socket. */
	private SaberStats benchAfter(HiltParts.Part design) {
		ItemStack candidate = SaberItems.partStack(design, lastMaterial);
		HiltParts.Slot slot = HiltParts.slot(design);
		return ForgeRules.benchStats(slot == HiltParts.Slot.EMITTER ? candidate : socketStack(HiltParts.Slot.EMITTER),
				slot == HiltParts.Slot.GRIP ? candidate : socketStack(HiltParts.Slot.GRIP),
				slot == HiltParts.Slot.POMMEL ? candidate : socketStack(HiltParts.Slot.POMMEL), DEFAULT_BALANCE);
	}

	private static float[] values(SaberStats stats) {
		return new float[] {(float) stats.damage(), (float) stats.attackSpeed(), (float) stats.reachBonus(), stats.maxStamina(), stats.parryTicks()};
	}

	/** {@code value} with its stat's decimals; {@code signed} for a change, where a change too small to show is "±0". */
	private static String format(int stat, float value, boolean signed) {
		int decimals = STAT_DECIMALS[stat];

		if (signed && Math.abs(value) < 0.5f * Math.pow(10, -decimals)) {
			return "±0";
		}

		return String.format(Locale.ROOT, (signed ? "%+." : "%.") + decimals + "f", value);
	}

	private static Component statName(int stat) {
		return Component.translatable("container.oldrepublic.lightsaber_workbench.stat." + STAT_KEYS[stat]);
	}

	// ---------------------------------------------------------------- drawing

	/** The panel, then the console's cost; the buttons are the screen's. */
	void draw(GuiGraphicsExtractor g, Font font, int x0, int y0, int mouseX, int mouseY, float time) {
		Ctx c = ctx();
		HiltParts.Part design = design();
		int px = x0 + PANEL_X;
		int py = y0 + PANEL_Y;
		Ui.inset(g, px, py, PANEL_W, PANEL_H, 1f);
		Ui.scanlines(g, px + 2, py + 2, PANEL_W - 4, PANEL_H - 4, 0x08FFFFFF, time);

		drawGrid(g, x0, y0, mouseX, mouseY, c, design);
		drawChips(g, x0, y0, mouseX, mouseY, c, design);
		drawName(g, font, x0, y0, design);
		drawWell(g, x0, y0, design, time);
		drawStats(g, font, x0, y0, mouseX, mouseY, design);
		drawStatus(g, font, x0, y0, c, design);
		drawCost(g, font, x0, y0, c, design);
	}

	private void drawGrid(GuiGraphicsExtractor g, int x0, int y0, int mouseX, int mouseY, Ctx c, HiltParts.@Nullable Part design) {
		HiltParts.Part hover = tileAt(mouseX, mouseY, x0, y0);

		for (HiltParts.Slot slot : HiltParts.Slot.values()) {
			List<HiltParts.Part> row = row(slot);
			ItemStack socket = socketStack(slot);
			HiltParts.Part inSocket = ForgeRules.part(socket);
			int ty = y0 + GRID_Y + slot.ordinal() * TILE_STEP;

			for (int i = 0; i < row.size(); i++) {
				HiltParts.Part part = row.get(i);
				int tx = x0 + GRID_X + i * TILE_STEP;
				boolean locked = SaberItems.isCraftableDesign(part) && !c.known().knows(part);
				boolean selected = part == design;
				Ui.sprite(g, selected ? TILE_SELECTED : locked ? TILE_LOCKED : TILE_SPRITE, tx, ty, TILE, TILE, 1f);
				g.item(SaberItems.partStack(part, lastMaterial), tx + 1, ty + 1);

				if (locked) {
					// Not known yet: dimmed under a padlock, still pickable (to study or re-forge the one in the socket).
					g.fill(tx + 1, ty + 1, tx + TILE - 1, ty + TILE - 1, Ui.a(UiColors.OUTLINE, 0.55f));
					Ui.sprite(g, LOCK, tx + TILE - 8, ty + TILE - 9, 6, 7, UiColors.TEXT_MUTED);
				}

				if (part == inSocket) {
					// The part on the bench: a brass flag, and a pip of its material.
					Ui.sprite(g, SOCKET_MARK, tx + 1, ty + 1, 5, 5, 1f);
					g.fill(tx + TILE - 4, ty + 2, tx + TILE - 2, ty + 4, 0xFF000000 | SaberComponents.material(socket).tint());
				}

				if (part == hover && !selected) {
					g.outline(tx, ty, TILE, TILE, Ui.a(UiColors.HOLO, 0.75f));
				}
			}
		}
	}

	private void drawChips(GuiGraphicsExtractor g, int x0, int y0, int mouseX, int mouseY, Ctx c, HiltParts.@Nullable Part design) {
		HiltMaterial hover = chipAt(mouseX, mouseY, x0, y0);
		int cy = y0 + CHIPS_Y;

		for (HiltMaterial material : HiltMaterial.values()) {
			int cx = x0 + CHIPS_X + material.ordinal() * CHIP_STEP;
			boolean selected = material == lastMaterial;
			boolean affordable = design == null || ForgeRules.checkCost(design, material, c.sources(), c.credits(), c.costs()) == ForgeRules.Result.OK;
			int tint = 0xFF000000 | material.tint();
			Ui.sprite(g, selected ? CHIP_SELECTED : CHIP_SPRITE, cx, cy, CHIP, CHIP, 1f);
			Ui.sprite(g, PLATE, cx + 2, cy + 2, 10, 6, affordable ? tint : ARGB.srgbLerp(0.55f, tint, UiColors.STEEL_DARK));

			// The tier as brass pips under the plate.
			for (int t = 0; t < material.tier(); t++) {
				g.fill(cx + 2 + t * 2, cy + 10, cx + 3 + t * 2, cy + 11, affordable ? UiColors.BRASS_LIGHT : UiColors.BRASS_DARK);
			}

			if (!affordable) {
				// Short of the units or the credits: an amber corner (the tooltip says which).
				g.fill(cx + CHIP - 4, cy + CHIP - 3, cx + CHIP - 1, cy + CHIP - 2, UiColors.AMBER);
				g.fill(cx + CHIP - 2, cy + CHIP - 5, cx + CHIP - 1, cy + CHIP - 3, UiColors.AMBER);
			}

			if (material == hover && !selected) {
				g.outline(cx, cy, CHIP, CHIP, Ui.a(UiColors.HOLO, 0.75f));
			}
		}
	}

	private static void drawName(GuiGraphicsExtractor g, Font font, int x0, int y0, HiltParts.@Nullable Part design) {
		if (design == null) {
			return;
		}

		int x = x0 + CHIPS_X;
		int y = y0 + NAME_Y;
		String name = name(design).getString();
		String material = materialName(lastMaterial).getString();

		if (font.width(name + "  " + material) <= NAME_W) {
			g.text(font, name, x, y, UiColors.TEXT, false);
			g.text(font, material, x + font.width(name + "  "), y, ARGB.srgbLerp(0.4f, 0xFF000000 | lastMaterial.tint(), 0xFFFFFFFF), false);
		} else {
			g.text(font, Ui.fit(font, name, NAME_W), x, y, UiColors.TEXT, false);
		}
	}

	/** The forge well: a glow of the material's colour over the anvil, and the part's 3D icon on it. */
	private void drawWell(GuiGraphicsExtractor g, int x0, int y0, HiltParts.@Nullable Part design, float time) {
		int wx = x0 + WELL_X;
		int wy = y0 + WELL_Y;
		int tint = 0xFF000000 | lastMaterial.tint();
		g.fill(wx, wy, wx + WELL_W, wy + WELL_H, Ui.a(UiColors.OUTLINE, 0.55f));
		g.fillGradient(wx + 1, wy + 1, wx + WELL_W - 1, wy + WELL_H - 1, 0x00000000, Ui.a(tint, 0.2f + 0.06f * UiAnim.flicker(time)));
		g.outline(wx, wy, WELL_W, WELL_H, Ui.a(UiColors.HOLO_DIM, 0.8f));
		Ui.sprite(g, ANVIL, wx + (WELL_W - ANVIL_W) / 2, wy + WELL_H - ANVIL_H - 1, ANVIL_W, ANVIL_H, 1f);

		if (design != null) {
			int size = 16 * ICON_SCALE;
			// API-CHECK: an item under a scaled pose (the GUI blits its atlas icon through the pose): expect it 3x, maybe soft.
			g.pose().pushMatrix();
			g.pose().translate(wx + (WELL_W - size) / 2, wy + 1);
			g.pose().scale(ICON_SCALE);
			g.item(SaberItems.partStack(design, lastMaterial), 0, 0);
			g.pose().popMatrix();
		}

		float age = glintAge();

		if (age >= 0f) {
			// Just forged or studied: the well flashes in the material's colour and fades.
			float t = age / GLINT_TICKS;
			g.fill(wx + 1, wy + 1, wx + WELL_W - 1, wy + WELL_H - 1, Ui.a(glintTint, 0.35f * (1f - t)));
			g.outline(wx - 1, wy - 1, WELL_W + 2, WELL_H + 2, Ui.a(glintTint, 1f - t));
		}
	}

	private void drawStats(GuiGraphicsExtractor g, Font font, int x0, int y0, int mouseX, int mouseY, HiltParts.@Nullable Part design) {
		float[] before = design == null ? null : values(benchBefore());
		float[] after = design == null ? null : values(benchAfter(design));
		int hover = statAt(mouseX, mouseY, x0, y0);

		for (int i = 0; i < STAT_KEYS.length; i++) {
			int sx = x0 + STATS_X + (i % 2) * STAT_CELL_W;
			int sy = y0 + STATS_Y + (i / 2) * STAT_LINE_H;
			float change = before == null ? 0f : after[i] - before[i];
			String text = format(i, change, true);
			int color = text.startsWith("±") ? UiColors.TEXT_DIM : change > 0f ? UiColors.OK : UiColors.AMBER;
			Ui.sprite(g, STAT_ICONS[i], sx, sy, STAT_ICON, STAT_ICON, i == hover ? UiColors.HOLO_BRIGHT : UiColors.TEXT_MUTED);
			g.text(font, text, sx + STAT_ICON + 2, sy, color, false);
		}
	}

	/** A status line and its colour. */
	private record Line(Component text, int color) {
	}

	/** The status at the panel's foot: what is missing, or what the action will do. */
	private void drawStatus(GuiGraphicsExtractor g, Font font, int x0, int y0, Ctx c, HiltParts.@Nullable Part design) {
		Line line = statusLine(c, design);
		int px = x0 + PANEL_X;
		g.fill(px + 6, y0 + STATUS_Y + 3, px + 9, y0 + STATUS_Y + 6, line.color());
		g.text(font, Ui.fit(font, line.text().getString(), PANEL_W - 18), px + 12, y0 + STATUS_Y, line.color(), false);
	}

	/** The status line: Study first (it is free), then the Re-forge of the part in the socket, else the Forge. */
	private Line statusLine(Ctx c, HiltParts.@Nullable Part design) {
		if (design == null) {
			return new Line(Component.translatable(KEY + "status.choose"), UiColors.TEXT_MUTED);
		}

		ItemStack socket = socketStack(HiltParts.slot(design));
		Component name = name(design);
		Component material = materialName(lastMaterial);

		if (ForgeRules.part(socket) == design) {
			if (ForgeRules.checkStudy(socket, c.known()) == ForgeRules.Result.OK) {
				return new Line(Component.translatable(KEY + "status.study", name), UiColors.HOLO_BRIGHT);
			}

			ForgeRules.Result result = ForgeRules.checkReforge(socket, design, lastMaterial, c.sources(), c.credits(), c.costs());
			return switch (result) {
				case OK -> new Line(Component.translatable(KEY + "status.ready_reforge", name, material), UiColors.OK);
				case SAME_MATERIAL -> new Line(Component.translatable(KEY + "status.same_material_hint", name, material), UiColors.AMBER);
				default -> new Line(shortLine(result, c, design), UiColors.AMBER);
			};
		}

		ForgeRules.Result result = ForgeRules.checkForge(design, lastMaterial, c.known(), c.sources(), c.credits(), c.costs());

		if (result != ForgeRules.Result.OK) {
			return new Line(shortLine(result, c, design), UiColors.AMBER);
		}

		if (menu.forgeFitsSocket(design)) {
			return new Line(Component.translatable(KEY + "status.ready_forge", name), UiColors.OK);
		}

		boolean staffPommel = HiltParts.slot(design) == HiltParts.Slot.POMMEL && menu.isStaffMode();
		return new Line(Component.translatable(KEY + (staffPommel ? "status.ready_forge_staff" : "status.ready_forge_inventory"), name), UiColors.OK);
	}

	/** Why a Forge or Re-forge cannot go: what is short, with how much the player carries. */
	private Component shortLine(ForgeRules.Result result, Ctx c, HiltParts.Part design) {
		if (result == ForgeRules.Result.NO_MATERIALS) {
			List<Schematic.Input> inputs = ForgeRules.inputs(design, lastMaterial, c.costs());
			Item unit = ForgeRules.unitItem(lastMaterial);

			if (inputs != null && !inputs.isEmpty() && unit != null) {
				return Component.translatable(KEY + "status.need_units", new ItemStack(unit).getHoverName(), Ingredients.count(c.sources(), inputs.get(0)),
						inputs.get(0).count());
			}
		}

		if (result == ForgeRules.Result.NO_CREDITS) {
			return Component.translatable(KEY + "status.need_credits", c.credits(), c.costs().credits(lastMaterial));
		}

		return Component.translatable(result.messageKey());
	}

	/** The console: the material's units against what the player carries, then the credits. */
	private static void drawCost(GuiGraphicsExtractor g, Font font, int x0, int y0, Ctx c, HiltParts.@Nullable Part design) {
		int cx = x0 + LightsaberWorkbenchScreen.CONSOLE_X;
		g.text(font, Component.translatable("screen.oldrepublic.workbench.tune.cost_label"), cx, y0 + COST_Y, UiColors.TEXT_MUTED, true);

		if (design == null) {
			return;
		}

		List<Schematic.Input> inputs = ForgeRules.inputs(design, lastMaterial, c.costs());
		Item unit = ForgeRules.unitItem(lastMaterial);

		if (inputs != null && !inputs.isEmpty() && unit != null) {
			int have = Ingredients.count(c.sources(), inputs.get(0));
			int need = inputs.get(0).count();
			g.item(new ItemStack(unit), cx + 1, y0 + COST_Y + 9);
			g.text(font, have + "/" + need, cx + 20, y0 + COST_Y + 13, have >= need ? UiColors.OK : UiColors.AMBER, false);
		}

		int credits = c.costs().credits(lastMaterial);
		String cost = Component.translatable("screen.oldrepublic.workbench.tune.credits", credits).getString();
		g.text(font, Ui.fit(font, cost, LightsaberWorkbenchScreen.CONSOLE_W), cx + 1, y0 + COST_Y + 28, c.credits() >= credits ? UiColors.OK : UiColors.AMBER, false);
	}

	// ---------------------------------------------------------------- the glint ({@code workbench_forge}, {@code workbench_study})

	/** A part was forged ({@code socket} -1: into the inventory) or re-forged in material {@code material}: the well and the socket glint in its colour. */
	static void onForge(long gameTime, int socket, int material) {
		glintAt = gameTime;
		glintSocket = socket;
		HiltMaterial m = material >= 0 && material < HiltMaterial.values().length ? HiltMaterial.values()[material] : HiltMaterial.STEEL;
		glintTint = ARGB.srgbLerp(0.25f, 0xFF000000 | m.tint(), 0xFFFFFFFF);
	}

	/** A design was studied from {@code socket}: the well and the socket glint holo blue. */
	static void onStudy(long gameTime, int socket) {
		glintAt = gameTime;
		glintSocket = socket;
		glintTint = UiColors.HOLO_BRIGHT;
	}

	/** Ticks since the last glint, or -1 once it is over. */
	private static float glintAge() {
		Minecraft mc = Minecraft.getInstance();

		if (mc.level == null) {
			return -1f;
		}

		float age = mc.level.getGameTime() - glintAt;
		return age >= 0f && age < GLINT_TICKS ? age : -1f;
	}

	/** The ring that opens out of the socket a part went into or was studied from (any tab). */
	void drawSocketGlint(GuiGraphicsExtractor g, int x0, int y0) {
		float age = glintAge();

		if (age < 0f || glintSocket < 0 || glintSocket >= menu.slots.size()) {
			return;
		}

		Slot s = menu.getSlot(glintSocket);
		float t = age / GLINT_TICKS;
		int grow = Math.round(t * 5f);
		int cx = x0 + s.x + 8;
		int cy = y0 + s.y + 8;
		g.outline(cx - 10 - grow, cy - 10 - grow, 20 + grow * 2, 20 + grow * 2, Ui.a(glintTint, 1f - t));
	}

	// ---------------------------------------------------------------- names

	private static Component name(HiltParts.Part design) {
		return Component.translatable("item.oldrepublic." + design.itemId());
	}

	private static Component materialName(HiltMaterial material) {
		return Component.translatable(material.translationKey());
	}
}
