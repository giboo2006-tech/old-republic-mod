package com.oldrepublic.client.quest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.ui.Ui;
import com.oldrepublic.progression.ForceAttachments;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.quest.JournalEntries;
import com.oldrepublic.quest.QuestRules;
import com.oldrepublic.quest.QuestStage;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;

/**
 * The Wanderer's Journal. Left page: the chapter, the current purpose and its progress, and a short
 * summary of the player's attunement. Right page: the player's own entries or the codex, as one
 * scrollable feed with an ink bullet per entry, a wax seal on entries not read yet and an ink
 * flourish between entries. Tabs are paper tabs (a seal marks the other tab when it holds unread
 * entries); the attunement summary has an inked progress bar. Everything is read from the synced
 * Force data; the screen sends nothing.
 */
public final class JournalScreen extends Screen {
	private static final Identifier TEXTURE = OldRepublicMod.id("textures/gui/journal.png");
	private static final int BOOK_W = 320;
	private static final int BOOK_H = 200;

	private static final int LEFT_X = 22;
	private static final int RIGHT_X = 170;
	private static final int PAGE_W = 128;
	private static final int PAGE_TOP = 16;
	private static final int FEED_TOP = 36;
	private static final int FEED_BOTTOM = 178;
	private static final int TAB_Y = 18;
	private static final int TAB_H = 11;

	private static final int INK = 0xFF2E2418;
	private static final int INK_FADED = 0xFF6B5D4A;
	private static final int HEADER = 0xFF5C2A17;
	private static final int ACCENT = 0xFF2C5873;
	private static final int RULE = 0x605C2A17;
	/** Feed titles are indented to make room for their bullet or unread seal. */
	private static final int TITLE_INDENT = 10;

	private static final String[] EMITTERS = {"emitter_guardian", "emitter_duelist"};
	private static final String[] GRIPS = {"grip_wrapped", "grip_ridged"};
	private static final String[] POMMELS = {"pommel_weighted", "pommel_balanced"};

	/** Entry flags unlocked during this session that the player has not looked at yet. */
	private static final Set<String> UNREAD = new HashSet<>();
	private static boolean showCodexTab;

	/** What a feed line is, for its decoration. */
	private enum Kind {
		TEXT, TITLE, TITLE_UNREAD, DIVIDER
	}

	private record Line(@Nullable FormattedCharSequence text, int color, Kind kind) {
		Line(@Nullable FormattedCharSequence text, int color) {
			this(text, color, Kind.TEXT);
		}
	}

	private final List<Line> feed = new ArrayList<>();
	private final List<Line> leftPage = new ArrayList<>();
	private float scale = 1f;
	private int left;
	private int top;
	private double scroll;
	private int contentHeight;

	public JournalScreen() {
		super(Component.translatable("screen.oldrepublic.journal"));
	}

	/** Marks a newly unlocked entry so the journal highlights it and opens on it. */
	static void markUnread(boolean codex, int index) {
		List<String> ids = codex ? JournalEntries.CODEX : JournalEntries.ENTRIES;

		if (index >= 0 && index < ids.size()) {
			UNREAD.add(codex ? JournalEntries.codexFlag(ids.get(index)) : JournalEntries.entryFlag(ids.get(index)));
		}
	}

	static void resetSession() {
		UNREAD.clear();
		showCodexTab = false;
	}

	@Override
	protected void init() {
		scale = Math.min(1f, Math.min((width - 8) / (float) BOOK_W, (height - 8) / (float) BOOK_H));
		left = (int) ((width - BOOK_W * scale) / 2);
		top = (int) ((height - BOOK_H * scale) / 2);

		if (!UNREAD.isEmpty()) {
			boolean anyEntry = UNREAD.stream().anyMatch(f -> f.startsWith("journal:"));
			showCodexTab = !anyEntry;
		}

		rebuild(true);
	}

	private @Nullable ForceData data() {
		return minecraft.player == null ? null : minecraft.player.getAttached(ForceAttachments.FORCE);
	}

	private void rebuild(boolean jumpToUnread) {
		ForceData data = data();
		buildLeftPage(data);
		feed.clear();
		int firstUnreadLine = -1;

		if (data == null || !data.isAwakened()) {
			addParagraph(feed, Component.translatable("journal.oldrepublic.empty"), INK_FADED);
		} else {
			List<String> ids = showCodexTab ? JournalEntries.CODEX : JournalEntries.ENTRIES;
			String prefix = showCodexTab ? "journal.oldrepublic.codex." : "journal.oldrepublic.entry.";
			boolean any = false;

			for (String id : ids) {
				String flag = showCodexTab ? JournalEntries.codexFlag(id) : JournalEntries.entryFlag(id);

				if (!data.hasFlag(flag)) {
					continue;
				}

				boolean unread = UNREAD.contains(flag);

				if (unread && firstUnreadLine < 0) {
					firstUnreadLine = feed.size();
				}

				if (any && !feed.isEmpty()) {
					feed.add(new Line(null, 0, Kind.DIVIDER));
					feed.add(new Line(null, 0));
				}

				any = true;
				Component title = Component.translatable(prefix + id + ".title").withStyle(ChatFormatting.BOLD);

				if (unread) {
					title = title.copy().append(Component.literal("  ")).append(Component.translatable("journal.oldrepublic.new").withStyle(ChatFormatting.ITALIC));
				}

				addTitle(feed, title, unread ? ACCENT : HEADER, unread);
				String body = Component.translatable(prefix + id + ".body").getString();

				for (String paragraph : body.split("\n")) {
					if (paragraph.isBlank()) {
						feed.add(new Line(null, 0));
					} else {
						addParagraph(feed, Component.literal(paragraph), INK);
					}
				}

				feed.add(new Line(null, 0));
			}

			if (!any) {
				addParagraph(feed, Component.translatable("journal.oldrepublic.codex_empty"), INK_FADED);
			}

			// Entries on the visible tab are now read.
			UNREAD.removeIf(f -> f.startsWith(showCodexTab ? "codex:" : "journal:"));
		}

		contentHeight = feed.size() * font.lineHeight;
		scroll = jumpToUnread && firstUnreadLine >= 0 ? firstUnreadLine * font.lineHeight : 0;
		clampScroll();
	}

	private void buildLeftPage(@Nullable ForceData data) {
		leftPage.clear();
		addParagraph(leftPage, Component.translatable("journal.oldrepublic.chapter").withStyle(ChatFormatting.BOLD), HEADER);
		leftPage.add(new Line(null, 0));

		QuestStage stage = data == null ? QuestStage.STIRRING : QuestStage.of(data.questStage);
		addParagraph(leftPage, Component.translatable("journal.oldrepublic.purpose").withStyle(ChatFormatting.ITALIC), INK_FADED);
		addParagraph(leftPage, Component.translatable("quest.oldrepublic.objective." + stage.name().toLowerCase(Locale.ROOT)), INK);

		if (data == null) {
			return;
		}

		leftPage.add(new Line(null, 0));

		switch (stage) {
			case PRACTICE -> addParagraph(leftPage, Component.translatable("journal.oldrepublic.progress.practice", data.counter(QuestRules.PUSH_PRACTICE)), ACCENT);
			case CALLING -> addParagraph(leftPage, Component.translatable(CallingFx.isFaint() ? "journal.oldrepublic.progress.calling_faint" : "journal.oldrepublic.progress.calling"), ACCENT);
			case CONSTRUCTION -> {
				checklist("journal.oldrepublic.part.crystal", "kyber_crystal");
				checklist("journal.oldrepublic.part.emitter", EMITTERS);
				checklist("journal.oldrepublic.part.grip", GRIPS);
				checklist("journal.oldrepublic.part.pommel", POMMELS);
			}
			default -> {
			}
		}

		if (data.counter(QuestRules.POACHER_KILLS) > 0) {
			addParagraph(leftPage, Component.translatable("journal.oldrepublic.progress.poachers", data.counter(QuestRules.POACHER_KILLS)), INK_FADED);
		}
	}

	private void checklist(String key, String... itemIds) {
		boolean has = false;

		for (String id : itemIds) {
			Item item = BuiltInRegistries.ITEM.getOptional(OldRepublicMod.id(id)).orElse(null);

			if (item != null && minecraft.player != null && minecraft.player.getInventory().contains(stack -> !stack.isEmpty() && stack.getItem() == item)) {
				has = true;
				break;
			}
		}

		Component mark = Component.literal(has ? "✔ " : "• ");
		addParagraph(leftPage, mark.copy().append(Component.translatable(key)), has ? ACCENT : INK_FADED);
	}

	private void addTitle(List<Line> into, Component text, int color, boolean unread) {
		boolean first = true;

		for (FormattedCharSequence seq : font.split(text, PAGE_W - TITLE_INDENT)) {
			into.add(new Line(seq, color, first ? (unread ? Kind.TITLE_UNREAD : Kind.TITLE) : Kind.TEXT));
			first = false;
		}
	}

	private void addParagraph(List<Line> into, Component text, int color) {
		for (FormattedCharSequence seq : font.split(text, PAGE_W)) {
			into.add(new Line(seq, color));
		}
	}

	private void clampScroll() {
		int view = FEED_BOTTOM - FEED_TOP;
		scroll = Mth.clamp(scroll, 0, Math.max(0, contentHeight - view));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		double lx = toLocalX(mouseX);
		double ly = toLocalY(mouseY);
		graphics.pose().pushMatrix();
		graphics.pose().translate(left, top);
		graphics.pose().scale(scale);

		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, 0, 0, 0f, 0f, BOOK_W, BOOK_H, BOOK_W, BOOK_H);
		drawPageDecoration(graphics);
		drawLeftPage(graphics);
		drawTabs(graphics, lx, ly);
		drawFeed(graphics);

		graphics.pose().popMatrix();
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	/** Faded-ink notch script along the bottom margins, like a scholar's marginalia. */
	private void drawPageDecoration(GuiGraphicsExtractor graphics) {
		int ink = 0x285C2A17;
		Ui.glyphs(graphics, LEFT_X, FEED_BOTTOM + 4, PAGE_W, ink, 7);
		Ui.glyphs(graphics, RIGHT_X, FEED_BOTTOM + 4, PAGE_W, ink, 71);
	}

	private void drawLeftPage(GuiGraphicsExtractor graphics) {
		int y = PAGE_TOP + 2;
		boolean first = true;

		for (Line line : leftPage) {
			if (line.text() != null) {
				graphics.text(font, line.text(), LEFT_X, y, line.color(), false);
			} else if (first) {
				// The blank line under the chapter title carries an ink flourish.
				Ui.sprite(graphics, Ui.JOURNAL_ORNAMENT, LEFT_X + (PAGE_W - 64) / 2, y + 2, 64, 5, 1f);
				first = false;
			}

			y += font.lineHeight;
		}

		ForceData data = data();

		if (data != null && data.isAwakened()) {
			int base = FEED_BOTTOM - font.lineHeight * 3;
			Ui.sprite(graphics, Ui.JOURNAL_ORNAMENT, LEFT_X + (PAGE_W - 64) / 2, base - 8, 64, 5, 1f);
			Component level = Component.translatable("journal.oldrepublic.stats.level", data.level);
			graphics.text(font, level, LEFT_X, base, INK, false);
			// A thin inked bar for progress toward the next level.
			float xp = data.xpToNext <= 0 ? 1f : Mth.clamp((float) data.xp / data.xpToNext, 0f, 1f);
			int bx = LEFT_X + font.width(level) + 6;
			int bw = LEFT_X + PAGE_W - bx;

			if (bw > 12) {
				graphics.fill(bx, base + 3, bx + bw, base + 4, RULE);
				graphics.fill(bx, base + 2, bx + Math.round(bw * xp), base + 5, 0xC05C2A17);
			}

			graphics.text(font, Component.translatable("journal.oldrepublic.stats.resonance", String.format(Locale.ROOT, "%,d", data.resonance)), LEFT_X, base + font.lineHeight, INK, false);
			graphics.text(font, Component.translatable("journal.oldrepublic.stats.skill_points", data.skillPoints), LEFT_X, base + font.lineHeight * 2, INK, false);
		}
	}

	private void drawTabs(GuiGraphicsExtractor graphics, double lx, double ly) {
		boolean entriesUnread = UNREAD.stream().anyMatch(f -> f.startsWith("journal:"));
		boolean codexUnread = UNREAD.stream().anyMatch(f -> f.startsWith("codex:"));
		drawTab(graphics, Component.translatable("journal.oldrepublic.tab.entries"), RIGHT_X, !showCodexTab, entriesUnread, lx, ly);
		drawTab(graphics, Component.translatable("journal.oldrepublic.tab.codex"), RIGHT_X + PAGE_W / 2 + 4, showCodexTab, codexUnread, lx, ly);
		graphics.fill(RIGHT_X, TAB_Y + TAB_H + 1, RIGHT_X + PAGE_W, TAB_Y + TAB_H + 2, RULE);
	}

	/**
	 * A tab: the open one is a raised paper tab edged in ink with a brass rivet at each end, the
	 * other is a recessed one in faded ink. A wax seal marks a tab holding entries not yet read.
	 */
	private void drawTab(GuiGraphicsExtractor graphics, Component label, int x, boolean selected, boolean unread, double lx, double ly) {
		int w = PAGE_W / 2 - 4;
		boolean hover = lx >= x && lx < x + w && ly >= TAB_Y && ly < TAB_Y + TAB_H;

		if (selected) {
			graphics.fill(x, TAB_Y - 1, x + w, TAB_Y + TAB_H + 1, 0x30FFF4DC);
			graphics.fill(x, TAB_Y - 1, x + w, TAB_Y, HEADER);
			graphics.fill(x, TAB_Y - 1, x + 1, TAB_Y + TAB_H, HEADER);
			graphics.fill(x + w - 1, TAB_Y - 1, x + w, TAB_Y + TAB_H, HEADER);
			graphics.fill(x + 2, TAB_Y + 1, x + 4, TAB_Y + 3, 0xFFB08A4E);
			graphics.fill(x + w - 4, TAB_Y + 1, x + w - 2, TAB_Y + 3, 0xFFB08A4E);
			graphics.fill(x + 1, TAB_Y + TAB_H, x + w - 1, TAB_Y + TAB_H + 2, 0xFFEBDDBF);
		} else {
			graphics.fill(x + 1, TAB_Y + 1, x + w - 1, TAB_Y + TAB_H, hover ? 0x285C2A17 : 0x145C2A17);
		}

		int color = selected ? HEADER : hover ? INK : INK_FADED;
		int tx = x + (w - font.width(label)) / 2;
		graphics.text(font, label, tx, TAB_Y + 2, color, false);

		if (unread && !selected) {
			Ui.sprite(graphics, Ui.JOURNAL_SEAL, x + w - 10, TAB_Y + 1, 9, 9, 1f);
		}
	}

	private void drawFeed(GuiGraphicsExtractor graphics) {
		// API-CHECK: enableScissor takes coordinates in the current pose (it is transformed like other draws).
		graphics.enableScissor(RIGHT_X - 2, FEED_TOP, RIGHT_X + PAGE_W + 2, FEED_BOTTOM);
		int y = FEED_TOP - (int) scroll;

		for (Line line : feed) {
			if (y + font.lineHeight > FEED_TOP && y < FEED_BOTTOM) {
				switch (line.kind()) {
					case DIVIDER -> Ui.sprite(graphics, Ui.JOURNAL_ORNAMENT, RIGHT_X + (PAGE_W - 64) / 2, y + 2, 64, 5, 0.8f);
					case TITLE -> {
						// Ink diamond bullet
						graphics.fill(RIGHT_X + 2, y + 3, RIGHT_X + 5, y + 4, HEADER);
						graphics.fill(RIGHT_X + 3, y + 2, RIGHT_X + 4, y + 5, HEADER);
					}
					case TITLE_UNREAD -> Ui.sprite(graphics, Ui.JOURNAL_SEAL, RIGHT_X - 1, y - 1, 9, 9, 1f);
					case TEXT -> {
					}
				}

				if (line.text() != null) {
					int indent = line.kind() == Kind.TITLE || line.kind() == Kind.TITLE_UNREAD ? TITLE_INDENT : 0;
					graphics.text(font, line.text(), RIGHT_X + indent, y, line.color(), false);
				}
			}

			y += font.lineHeight;
		}

		graphics.disableScissor();

		int view = FEED_BOTTOM - FEED_TOP;

		if (contentHeight > view) {
			int barX = RIGHT_X + PAGE_W + 4;
			int thumbH = Math.max(10, view * view / contentHeight);
			int thumbY = FEED_TOP + (int) ((view - thumbH) * (scroll / (contentHeight - view)));
			graphics.fill(barX, FEED_TOP, barX + 2, FEED_BOTTOM, 0x305C2A17);
			graphics.fill(barX, thumbY, barX + 2, thumbY + thumbH, 0xA05C2A17);
			// Small brass caps at both ends of the thumb.
			graphics.fill(barX - 1, thumbY, barX + 3, thumbY + 1, 0xFFB08A4E);
			graphics.fill(barX - 1, thumbY + thumbH - 1, barX + 3, thumbY + thumbH, 0xFFB08A4E);
		}
	}

	private double toLocalX(double mouseX) {
		return (mouseX - left) / scale;
	}

	private double toLocalY(double mouseY) {
		return (mouseY - top) / scale;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double lx = toLocalX(event.x());
		double ly = toLocalY(event.y());

		if (event.button() == 0 && ly >= TAB_Y && ly < TAB_Y + TAB_H) {
			int w = PAGE_W / 2 - 4;
			boolean entries = lx >= RIGHT_X && lx < RIGHT_X + w;
			boolean codex = lx >= RIGHT_X + PAGE_W / 2 + 4 && lx < RIGHT_X + PAGE_W / 2 + 4 + w;

			if ((entries && showCodexTab) || (codex && !showCodexTab)) {
				showCodexTab = codex;
				rebuild(true);
				QuestSounds.ui(ModSounds.JOURNAL_OPEN, 1.25f, 0.45f);
				return true;
			}
		}

		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		scroll -= scrollY * font.lineHeight * 3;
		clampScroll();
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (QuestClient.journalKey().matches(event)) {
			onClose();
			return true;
		}

		return super.keyPressed(event);
	}
}
