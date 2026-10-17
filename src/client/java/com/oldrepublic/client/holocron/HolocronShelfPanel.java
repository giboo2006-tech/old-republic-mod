package com.oldrepublic.client.holocron;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.quest.JournalTabs;
import com.oldrepublic.client.ui.PortraitFrame;
import com.oldrepublic.client.ui.UiAnim;
import com.oldrepublic.holocron.ArchiveChoice;
import com.oldrepublic.holocron.HolocronBalance;
import com.oldrepublic.holocron.HolocronCatalog;
import com.oldrepublic.holocron.HolocronChoice;
import com.oldrepublic.holocron.HolocronData;
import com.oldrepublic.holocron.HolocronItems;
import com.oldrepublic.holocron.HolocronKind;
import com.oldrepublic.holocron.HolocronRewards;
import com.oldrepublic.holocron.HolocronRules;
import com.oldrepublic.holocron.HolocronService;
import com.oldrepublic.holocron.HolocronStamp;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.registry.M3Items;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;

/**
 * The Holocrons tab of the Wanderer's Journal (spec 4.9, 11.3). The left page is the shelf: one
 * row of four niches for each kind (Jedi, Sith, Wayfarer) with the Archive's three below once the
 * hoard is resolved (its choice owed or made), each holding the lit holocron once attuned or its
 * dark silhouette while unfound (also dark when
 * sealed or broken), with the progress "n/12", the carry bonus "Carried: +8% regen (2/3)" and the
 * echo shard count. The right page reads the holocron under the cursor (the last one hovered
 * stays): its gatekeeper's portrait and name, its status (Broken, Returned, Carried, Sealed,
 * Listened or At rest), what it taught, or a hint for its kind while it is unfound.
 *
 * <p>The client cannot read the server's balance file, so the carry percentage shown uses the
 * default values; the server applies the live ones.
 */
final class HolocronShelfPanel implements JournalTabs.Panel {
	// The journal's ink on its parchment.
	private static final int INK = 0xFF2E2418;
	private static final int INK_FADED = 0xFF6B5D4A;
	private static final int HEADER = 0xFF5C2A17;
	private static final int ACCENT = 0xFF2C5873;
	private static final int RULE = 0x605C2A17;
	private static final int GREEN_INK = 0xFF3C6B2E;
	private static final int WAX = 0xFF8E2A20;
	/** The niche: an inked edge, a shadowed recess and a lit recess behind an attuned holocron. */
	private static final int NICHE_EDGE = 0x905C2A17;
	private static final int NICHE = 0x40372A1C;
	private static final int NICHE_HOVER = 0xFF5C2A17;

	private static final int PAGE_W = 128;
	private static final int RIGHT_OFFSET = 148;
	private static final int SLOT = 20;
	private static final int SLOT_GAP = 8;
	private static final int ROWS_Y = 14;
	private static final int ROW_PITCH = 30;
	private static final int PORTRAIT = 36;
	private static final int MAX_NAME_LINES = 3;
	private static final List<HolocronKind> ROWS = List.of(HolocronKind.JEDI, HolocronKind.SITH, HolocronKind.WAYFARER);
	private static final HolocronBalance SHOWN = new HolocronBalance();

	private final Map<HolocronKind, ItemStack> lit = new EnumMap<>(HolocronKind.class);
	private final Map<HolocronKind, ItemStack> dark = new EnumMap<>(HolocronKind.class);
	/** What the next shelf opened reads first ({@link HolocronClient#selectOnShelf}). */
	private static @Nullable String nextSelection;

	/** The holocron read on the right page: the one under the cursor, or the last one hovered. */
	private @Nullable String selected;

	HolocronShelfPanel() {
		for (HolocronKind kind : HolocronKind.values()) {
			lit.put(kind, new ItemStack(HolocronItems.of(kind)));
			ItemStack silhouette = new ItemStack(HolocronItems.of(kind));
			silhouette.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(RisingHolocron.DARK), List.of()));
			dark.put(kind, silhouette);
		}

		selected = nextSelection != null && HolocronCatalog.find(nextSelection) != null ? nextSelection : null;
		nextSelection = null;
	}

	/** Makes the next shelf opened read {@code id} on its right page until the cursor hovers another. */
	static void selectNext(@Nullable String id) {
		nextSelection = id;
	}

	/** Whether the tab is shown: once the player attuned any holocron. */
	static boolean visible(LocalPlayer player) {
		HolocronData view = HolocronService.view(player);
		return view != null && !view.attuned.isEmpty();
	}

	@Override
	public void render(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mouseX, int mouseY, float partialTick) {
		LocalPlayer player = Minecraft.getInstance().player;

		if (player == null) {
			return;
		}

		HolocronData view = HolocronService.view(player);
		HolocronData data = view != null ? view : new HolocronData();
		Set<String> carried = carriedIds(player, data);
		float time = UiAnim.time(partialTick);

		// ---- left page: the shelf
		g.text(font, Component.translatable("screen.oldrepublic.holocrons.heading"), x, y + 1, HEADER, false);
		Component progress = Component.translatable("screen.oldrepublic.holocrons.progress", HolocronRules.worldCount(data), HolocronCatalog.WORLD_COUNT);
		g.text(font, progress, x + PAGE_W - font.width(progress), y + 1, INK_FADED, false);
		g.fill(x, y + 11, x + PAGE_W, y + 12, RULE);

		String hovered = null;
		int rowY = y + ROWS_Y;

		for (HolocronKind kind : ROWS) {
			List<HolocronCatalog.Entry> row = HolocronCatalog.world(kind);
			g.text(font, kindName(kind), x, rowY, ink(kind), false);
			String count = HolocronRules.worldCount(data, kind) + "/" + row.size();
			g.text(font, count, x + PAGE_W - font.width(count), rowY, INK_FADED, false);
			String hit = drawRow(g, row, data, carried, x, rowY + 10, mouseX, mouseY);
			hovered = hit != null ? hit : hovered;
			rowY += ROW_PITCH;
		}

		ArchiveChoice archive = data.archiveChoice();

		// The Archive's three niches appear once the hoard is resolved: its choice is owed or made (4.9).
		if (archive != null || data.archivePending) {
			g.text(font, Component.translatable("screen.oldrepublic.holocrons.archive"), x, rowY, INK, false);

			if (archive != null) {
				Component decided = Component.translatable("screen.oldrepublic.holocrons.archive." + archive.name().toLowerCase(Locale.ROOT));
				g.text(font, decided, x + PAGE_W - font.width(decided), rowY, INK_FADED, false);
			}

			String hit = drawRow(g, HolocronCatalog.archive(), data, carried, x, rowY + 10, mouseX, mouseY);
			hovered = hit != null ? hit : hovered;
		}

		int footer = y + h - 20;
		g.fill(x, footer - 3, x + PAGE_W, footer - 2, RULE);
		int count = HolocronRules.carryCount(HolocronRewards.mainInventoryStamps(player.getInventory()), player.getUUID(), data.serials,
				Identity.path(player), SHOWN.carriedMax);
		g.text(font, Component.translatable("screen.oldrepublic.holocrons.carry", count * SHOWN.carriedRegenPct, count, SHOWN.carriedMax), x, footer,
				count > 0 ? GREEN_INK : INK_FADED, false);
		g.text(font, Component.translatable("screen.oldrepublic.holocrons.shards", shards(player.getInventory())), x, footer + 10, INK, false);

		// ---- right page: the hovered holocron
		if (hovered != null) {
			selected = hovered;
		}

		int rx = x + RIGHT_OFFSET;

		if (selected == null) {
			int ty = y + 40;

			for (FormattedCharSequence line : font.split(Component.translatable("screen.oldrepublic.holocrons.select"), PAGE_W - 16)) {
				g.text(font, line, rx + (PAGE_W - font.width(line)) / 2, ty, INK_FADED, false);
				ty += font.lineHeight;
			}

			return;
		}

		drawDetail(g, font, rx, y, h, HolocronCatalog.get(selected), data, carried, player, time);
	}

	/** Draws a row of niches centred on the left page; returns the id under the cursor, if any. */
	private @Nullable String drawRow(GuiGraphicsExtractor g, List<HolocronCatalog.Entry> row, HolocronData data, Set<String> carried, int x0, int sy,
			int mouseX, int mouseY) {
		// Four niches fill the page with 12 px margins; fewer are centred.
		int span = row.size() * SLOT + (row.size() - 1) * SLOT_GAP;
		int sx = x0 + (PAGE_W - span) / 2;
		String hovered = null;

		for (HolocronCatalog.Entry entry : row) {
			boolean hover = mouseX >= sx && mouseX < sx + SLOT && mouseY >= sy && mouseY < sy + SLOT;
			drawNiche(g, entry, data, carried, sx, sy, hover || entry.id().equals(selected));

			if (hover) {
				hovered = entry.id();
			}

			sx += SLOT + SLOT_GAP;
		}

		return hovered;
	}

	/**
	 * One niche. The marks sit in the niche's 2 px margin, never over the holocron: a green dot top
	 * right when carried, a wax dot bottom right when sealed, a red notch when broken, a blue one
	 * when returned.
	 */
	private void drawNiche(GuiGraphicsExtractor g, HolocronCatalog.Entry entry, HolocronData data, Set<String> carried, int sx, int sy, boolean highlight) {
		String id = entry.id();
		boolean attuned = data.isAttuned(id);
		HolocronChoice choice = data.choice(id);
		boolean returned = data.returned.contains(id);
		boolean shownLit = attuned && choice != HolocronChoice.SEAL && choice != HolocronChoice.BREAK && !returned;

		g.fill(sx, sy, sx + SLOT, sy + SLOT, highlight ? NICHE_HOVER : NICHE_EDGE);
		g.fill(sx + 1, sy + 1, sx + SLOT - 1, sy + SLOT - 1, NICHE);

		if (shownLit) {
			// The seams' light on the back of the niche.
			int glow = HolocronCatalog.tint(entry.kind()) & 0xFFFFFF;
			g.fill(sx + 1, sy + 1, sx + SLOT - 1, sy + SLOT - 1, 0x30000000 | glow);
			g.fill(sx + 4, sy + SLOT - 3, sx + SLOT - 4, sy + SLOT - 1, 0x70000000 | glow);
		}

		g.item(shownLit ? lit.get(entry.kind()) : dark.get(entry.kind()), sx + 2, sy + 2);

		if (carried.contains(id)) {
			g.fill(sx + SLOT - 2, sy, sx + SLOT, sy + 2, GREEN_INK);
		}

		if (choice == HolocronChoice.SEAL) {
			g.fill(sx + SLOT - 2, sy + SLOT - 2, sx + SLOT, sy + SLOT, WAX);
		} else if (choice == HolocronChoice.BREAK) {
			g.fill(sx, sy + SLOT - 2, sx + 2, sy + SLOT, WAX);
			g.fill(sx + SLOT - 2, sy, sx + SLOT, sy + 2, WAX);
		} else if (returned) {
			g.fill(sx + SLOT - 2, sy + SLOT - 2, sx + SLOT, sy + SLOT, ACCENT);
		}
	}

	private void drawDetail(GuiGraphicsExtractor g, Font font, int rx, int y, int h, HolocronCatalog.Entry entry, HolocronData data, Set<String> carried,
			LocalPlayer player, float time) {
		String id = entry.id();
		ArchiveChoice archive = data.archiveChoice();
		boolean known = data.isAttuned(id) || (entry.archive() && archive != null);
		int tint = HolocronCatalog.tint(entry.kind());

		PortraitFrame.draw(g, rx, y + 2, PORTRAIT, known ? portrait(entry) : null, tint, time, 1f);

		int tx = rx + PORTRAIT + 6;
		int tw = PAGE_W - PORTRAIT - 6;
		int ty = y + 3;
		Component name = known ? Component.translatable(HolocronCatalog.nameKey(id)) : Component.translatable("screen.oldrepublic.holocrons.unfound");
		List<FormattedCharSequence> nameLines = font.split(name, tw);

		for (int i = 0; i < Math.min(MAX_NAME_LINES, nameLines.size()); i++) {
			g.text(font, nameLines.get(i), tx, ty, known ? HEADER : INK_FADED, false);
			ty += font.lineHeight;
		}

		g.text(font, kindName(entry.kind()), tx, ty + 1, ink(entry.kind()), false);

		int by = y + 2 + PORTRAIT + 6;
		g.fill(rx, by, rx + PAGE_W, by + 1, RULE);
		by += 5;

		if (!known) {
			String hint = entry.archive() ? "archive" : entry.kind().name().toLowerCase(Locale.ROOT);
			wrap(g, font, Component.translatable("holocron.oldrepublic.hint." + hint), rx, by, INK_FADED);
			return;
		}

		HolocronChoice choice = data.choice(id);
		g.text(font, status(entry, data, carried, choice, archive), rx, by, statusColor(entry, data, carried, choice), false);
		by += font.lineHeight + 3;

		if (data.isAttuned(id) && choice != null) {
			String node = HolocronCatalog.teaching(id, Identity.path(player), choice);
			Component taught = node == null
					? Component.translatable("screen.oldrepublic.holocrons.lore")
					: Component.translatable("screen.oldrepublic.holocrons.taught", Component.translatable("skill.oldrepublic.node." + node + ".name"));
			by = wrap(g, font, taught, rx, by, INK) + 3;
		}

		if (entry.archive()) {
			by = wrap(g, font, Component.translatable("holocron.oldrepublic.item.archive"), rx, by, INK_FADED) + 3;
		}

		if (carried.contains(id)) {
			wrap(g, font, Component.translatable("screen.oldrepublic.holocrons.replay"), rx, Math.max(by, y + h - 20), INK_FADED);
		}
	}

	/** The status word, most telling first: Broken, Returned, Carried, Sealed, Listened, At rest; the Archive shows its fate. */
	private static Component status(HolocronCatalog.Entry entry, HolocronData data, Set<String> carried, @Nullable HolocronChoice choice,
			@Nullable ArchiveChoice archive) {
		String key;

		if (entry.archive() && archive != ArchiveChoice.KEEP && archive != null) {
			key = "archive." + archive.name().toLowerCase(Locale.ROOT);
		} else if (choice == HolocronChoice.BREAK) {
			key = "status.broken";
		} else if (data.returned.contains(entry.id())) {
			key = "status.returned";
		} else if (carried.contains(entry.id())) {
			key = "status.carried";
		} else if (choice == HolocronChoice.SEAL) {
			key = "status.sealed";
		} else if (choice == HolocronChoice.LISTEN) {
			key = "status.listened";
		} else {
			key = "status.kept";
		}

		return Component.translatable("screen.oldrepublic.holocrons." + key);
	}

	private static int statusColor(HolocronCatalog.Entry entry, HolocronData data, Set<String> carried, @Nullable HolocronChoice choice) {
		if (choice == HolocronChoice.BREAK) {
			return WAX;
		}

		if (data.returned.contains(entry.id())) {
			return ACCENT;
		}

		return carried.contains(entry.id()) ? GREEN_INK : HEADER;
	}

	/** Word-wraps {@code text} across the right page; returns the y under the last line. */
	private static int wrap(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int color) {
		for (FormattedCharSequence line : font.split(text, PAGE_W)) {
			g.text(font, line, x, y, color, false);
			y += font.lineHeight;
		}

		return y;
	}

	/** Ids whose owned, current copy is in the main inventory (what the carry bonus can count). */
	private static Set<String> carriedIds(LocalPlayer player, HolocronData data) {
		Set<String> ids = new HashSet<>();

		for (HolocronStamp stamp : HolocronRewards.mainInventoryStamps(player.getInventory())) {
			int serial = data.serial(stamp.id());

			if (stamp.owner().equals(player.getUUID()) && serial > 0 && stamp.serial() == serial && !data.returned.contains(stamp.id())) {
				ids.add(stamp.id());
			}
		}

		return ids;
	}

	private static int shards(Inventory inventory) {
		int count = 0;

		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);

			if (stack.is(M3Items.HOLOCRON_SHARD)) {
				count += stack.getCount();
			}
		}

		return count;
	}

	/** The gatekeeper's portrait; the Archive's holocrons all speak with their keeper's face. */
	private static Identifier portrait(HolocronCatalog.Entry entry) {
		return OldRepublicMod.id(entry.archive() ? "portrait/orren_vasch" : "portrait/gatekeeper_" + entry.id());
	}

	private static Component kindName(HolocronKind kind) {
		return Component.translatable("holocron.oldrepublic.kind." + kind.name().toLowerCase(Locale.ROOT));
	}

	/** A kind's tint darkened to an ink that reads on the parchment. */
	private static int ink(HolocronKind kind) {
		return switch (kind) {
			case JEDI -> 0xFF7A5A14;
			case SITH -> 0xFF8E2A20;
			case WAYFARER -> 0xFF6E4E22;
		};
	}
}
