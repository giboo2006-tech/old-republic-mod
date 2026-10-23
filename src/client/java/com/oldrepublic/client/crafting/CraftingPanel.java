package com.oldrepublic.client.crafting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.oldrepublic.client.quest.JournalTabs;
import com.oldrepublic.crafting.Crafting;
import com.oldrepublic.crafting.CraftingAttachments;
import com.oldrepublic.crafting.CraftingBalance;
import com.oldrepublic.crafting.CraftingData;
import com.oldrepublic.crafting.CraftingData.Progress;
import com.oldrepublic.crafting.Discipline;
import com.oldrepublic.crafting.Schematic;
import com.oldrepublic.crafting.Schematics;
import com.oldrepublic.quest.story.ChapterFour;
import com.oldrepublic.quest.story.ChapterFourStage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * The Crafting tab of the Wanderer's Journal (spec 7.2, 11.3), in the journal's ink. The left page
 * holds the player's disciplines (level, an XP rule toward the next level, the next unlocks), any
 * unlearned discipline and when the second discipline opens. The right page lists every known
 * schematic by discipline and level, marking the ones locked behind an unlearned discipline and the
 * ones never crafted (their first craft still gives player XP); it scrolls with the wheel.
 *
 * <p>The client reads no balance file, so the XP thresholds shown are the defaults (7.2).
 */
final class CraftingPanel implements JournalTabs.Panel {
	static final String TAB_ID = "crafting";
	static final int TAB_ORDER = 40;

	private static final int INK = 0xFF2E2418;
	private static final int INK_FADED = 0xFF6B5D4A;
	private static final int HEADER = 0xFF5C2A17;
	private static final int RULE = 0x605C2A17;
	private static final int ACCENT = 0xFF2C5873;
	private static final int GREEN_INK = 0xFF3C6B2E;
	private static final int WAX = 0xFF8E2A20;

	private static final int PAGE_W = 128;
	private static final int RIGHT_OFFSET = 148;
	private static final int ROW = 18;
	private static final int[] THRESHOLDS = new CraftingBalance().levelXp;

	private int scroll;
	private int rowsShown = 1;
	private int rowCount;

	/** Shown from the Chapter Four repairs (the bench schematic), or once the player has any crafting state. */
	static boolean visible(LocalPlayer player) {
		CraftingData data = CraftingAttachments.get(player);

		if (!data.disciplines().isEmpty() || !data.known().isEmpty() || !data.unlearned().isEmpty()) {
			return true;
		}

		return ChapterFour.atLeast(player, ChapterFourStage.REPAIRS);
	}

	@Override
	public void render(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mouseX, int mouseY, float partialTick) {
		LocalPlayer player = Minecraft.getInstance().player;

		if (player == null) {
			return;
		}

		CraftingData data = CraftingAttachments.get(player);
		drawLeft(g, font, data, x, y);
		drawRight(g, font, data, x + RIGHT_OFFSET, y, h);
	}

	// ---------------------------------------------------------------- left page

	private void drawLeft(GuiGraphicsExtractor g, Font font, CraftingData data, int x, int y) {
		int ty = heading(g, font, Component.translatable("screen.oldrepublic.crafting_tab.heading"), x, y);

		if (data.disciplines().isEmpty()) {
			ty = paragraph(g, font, Component.translatable("screen.oldrepublic.crafting_tab.none"), x, ty, INK_FADED, 5) + 4;
		}

		for (Progress p : data.disciplines()) {
			Discipline d = p.discipline();
			g.text(font, Crafting.name(d), x, ty, INK, false);
			String level = Component.translatable("screen.oldrepublic.crafting_tab.level", p.level()).getString();
			g.text(font, level, x + PAGE_W - font.width(level), ty, ACCENT, false);
			ty += font.lineHeight + 1;

			boolean max = p.level() >= THRESHOLDS.length;
			int from = THRESHOLDS[Mth.clamp(p.level() - 1, 0, THRESHOLDS.length - 1)];
			int to = max ? from : THRESHOLDS[p.level()];
			float fill = max ? 1f : Mth.clamp((p.xp() - from) / (float) Math.max(1, to - from), 0f, 1f);
			// An inked rule, filled in to the share of the way to the next level.
			g.fill(x, ty, x + PAGE_W, ty + 1, RULE);
			g.fill(x, ty - 1, x + Math.round(PAGE_W * fill), ty + 2, ACCENT);
			ty += 4;
			Component xp = max ? Component.translatable("screen.oldrepublic.crafting_tab.max")
					: Component.translatable("screen.oldrepublic.crafting_tab.xp", p.xp(), to, p.level() + 1);
			g.text(font, xp, x, ty, INK_FADED, false);
			ty += font.lineHeight;

			if (!max) {
				int unlocks = 0;

				for (Schematic s : Schematics.all()) {
					if (s.discipline().orElse(null) == d && s.level() == p.level() + 1 && s.learned() == Schematic.Learned.AUTO) {
						unlocks++;
					}
				}

				if (unlocks > 0) {
					g.text(font, Component.translatable("screen.oldrepublic.crafting_tab.unlocks", unlocks), x, ty, INK_FADED, false);
					ty += font.lineHeight;
				}
			}

			ty += 5;
		}

		if (data.disciplines().size() == 1) {
			ty = paragraph(g, font, Component.translatable("screen.oldrepublic.crafting_tab.second"), x, ty, INK_FADED, 3) + 4;
		}

		for (Discipline d : data.unlearned()) {
			ty = paragraph(g, font, Component.translatable("screen.oldrepublic.crafting_tab.unlearned", Crafting.name(d)), x, ty, WAX, 2) + 2;
		}
	}

	// ---------------------------------------------------------------- right page

	private void drawRight(GuiGraphicsExtractor g, Font font, CraftingData data, int x, int y, int h) {
		int ty = heading(g, font, Component.translatable("screen.oldrepublic.crafting_tab.schematics"), x, y);
		List<Schematic> known = known(data);
		rowCount = known.size();
		rowsShown = Math.max(1, (y + h - ty - 2) / ROW);
		scroll = Mth.clamp(scroll, 0, Math.max(0, rowCount - rowsShown));

		if (known.isEmpty()) {
			paragraph(g, font, Component.translatable("screen.oldrepublic.crafting_tab.no_schematics"), x, ty, INK_FADED, 4);
			return;
		}

		for (int i = 0; i < rowsShown && scroll + i < known.size(); i++) {
			Schematic s = known.get(scroll + i);
			int ry = ty + i * ROW;
			boolean locked = Crafting.craftRefusal(data, s) != null;
			g.item(s.result(), x, ry);
			String name = s.result().getHoverName().getString();
			g.text(font, name.length() > 0 ? cut(font, name, PAGE_W - 22) : s.id().getPath(), x + 19, ry, locked ? INK_FADED : INK, false);
			Component sub = locked
					? Component.translatable("screen.oldrepublic.crafting_tab.locked")
					: s.discipline().map(d -> Component.translatable("screen.oldrepublic.crafting_tab.of", Crafting.name(d), s.level()))
							.orElse(Component.translatable("discipline.oldrepublic.common"));
			g.text(font, sub, x + 19, ry + 9, locked ? WAX : INK_FADED, false);

			if (!locked && !data.firstCrafts().contains(s.id())) {
				// A small green tick-mark: the first craft still gives player XP.
				g.fill(x + PAGE_W - 3, ry + 3, x + PAGE_W, ry + 6, GREEN_INK);
			}
		}

		if (rowCount > rowsShown) {
			int track = rowsShown * ROW;
			int knob = Math.max(6, track * rowsShown / rowCount);
			int ky = ty + Math.round((track - knob) * (scroll / (float) Math.max(1, rowCount - rowsShown)));
			g.fill(x + PAGE_W + 3, ty, x + PAGE_W + 4, ty + track, RULE);
			g.fill(x + PAGE_W + 2, ky, x + PAGE_W + 5, ky + knob, HEADER);
		}
	}

	/** The schematics the player knows (taught, or automatic at their level), by discipline, then level. */
	private static List<Schematic> known(CraftingData data) {
		List<Schematic> out = new ArrayList<>();

		for (Schematic s : Schematics.all()) {
			if (data.known().contains(s.id()) || (Crafting.knows(data, s) && (s.discipline().isEmpty() || data.has(s.discipline().get())))) {
				out.add(s);
			}
		}

		out.sort(Comparator.<Schematic>comparingInt(s -> s.discipline().map(d -> d.ordinal() + 1).orElse(0)).thenComparingInt(Schematic::level));
		return out;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
		if (rowCount <= rowsShown) {
			return false;
		}

		scroll = Mth.clamp(scroll - (int) Math.signum(dy), 0, rowCount - rowsShown);
		return true;
	}

	// ---------------------------------------------------------------- ink helpers

	private static int heading(GuiGraphicsExtractor g, Font font, Component text, int x, int y) {
		g.text(font, text, x, y + 1, HEADER, false);
		g.fill(x, y + 11, x + PAGE_W, y + 12, RULE);
		return y + 16;
	}

	private static int paragraph(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int color, int maxLines) {
		int shown = 0;

		for (FormattedCharSequence line : font.split(text, PAGE_W)) {
			if (shown++ >= maxLines) {
				break;
			}

			g.text(font, line, x, y, color, false);
			y += font.lineHeight;
		}

		return y;
	}

	private static String cut(Font font, String text, int width) {
		if (font.width(text) <= width) {
			return text;
		}

		String ellipsis = "…";
		String out = text;

		while (!out.isEmpty() && font.width(out + ellipsis) > width) {
			out = out.substring(0, out.length() - 1);
		}

		return out + ellipsis;
	}
}
