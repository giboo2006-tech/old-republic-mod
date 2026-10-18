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
import com.oldrepublic.quest.story.ChapterFourBalance;
import com.oldrepublic.quest.story.ChapterFourRules;
import com.oldrepublic.quest.story.ChapterFourStage;
import com.oldrepublic.quest.story.ChapterThreeBalance;
import com.oldrepublic.quest.story.ChapterThreeRules;
import com.oldrepublic.quest.story.ChapterThreeStage;
import com.oldrepublic.quest.story.PathStage;
import com.oldrepublic.quest.story.Story;
import com.oldrepublic.quest.story.StoryData;
import com.oldrepublic.quest.story.StoryRules;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
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
 *
 * <p>Features add their own tabs through {@link JournalTabs}: when any is visible, a strip of
 * bookmarks stands above the book, the journal's own pages first, and an open feature tab draws
 * its panel across both pages.
 *
 * <p>From Chapter Three's SUMMONS the left page is Chapter Three's: the objective with its counts,
 * the Collector's yield while the confrontation waits on the player, and from LEADS the trail (each
 * lead found, ticked, then a line for each lead still missing) and, when the trail is complete but
 * the player's level is not, the gate and where XP comes from fastest. Journal notes
 * ({@code ChapterThreeService.addJournalNote}) are written under their entry in the feed. The
 * thresholds are the {@link ChapterThreeBalance} defaults: the client reads no balance file.
 *
 * <p>From Chapter Four's SEEKER the left page is Chapter Four's ({@link ChapterFourRules#objective}):
 * the objective with its counts and, during the world arc, the arc's checklist.
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
	/** Height of the bookmark strip above the book, when there are feature tabs. */
	private static final int STRIP_H = 12;
	/** Left edge of the first bookmark. */
	private static final int BOOKMARK_X = 14;
	/** The {@link #openTab} value for the journal's own pages. */
	private static final String JOURNAL_TAB = "journal";

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

	/** Index in {@link JournalEntries#ENTRIES} where each chapter begins, Chapter One first. */
	private static final int[] CHAPTER_STARTS = JournalEntries.CHAPTER_FIRSTS.stream().mapToInt(JournalEntries.ENTRIES::indexOf).toArray();

	/** Entry flags unlocked during this session that the player has not looked at yet. */
	private static final Set<String> UNREAD = new HashSet<>();
	private static boolean showCodexTab;
	/** The open bookmark: {@link #JOURNAL_TAB} or a {@link JournalTabs.Tab} id. */
	private static String openTab = JOURNAL_TAB;
	/** Set by {@link #openOnTab}: the next opening keeps that tab instead of jumping to unread entries. */
	private static boolean keepTab;
	private static final ChapterThreeBalance CHAPTER_THREE_DEFAULTS = new ChapterThreeBalance();
	private static final ChapterFourBalance CHAPTER_FOUR_DEFAULTS = new ChapterFourBalance();
	/** Leads drawn with up to two lines each while this few are found; more get one line each. */
	private static final int LEADS_TWO_LINES = 3;

	/** What a feed line is, for its decoration. */
	private enum Kind {
		TEXT, TITLE, TITLE_UNREAD, DIVIDER
	}

	private record Line(@Nullable FormattedCharSequence text, int color, Kind kind) {
		Line(@Nullable FormattedCharSequence text, int color) {
			this(text, color, Kind.TEXT);
		}
	}

	/** A bookmark's hit box in book coordinates; {@code tab} is null for the journal's own pages. */
	private record Bookmark(JournalTabs.@Nullable Tab tab, int x, int w) {
	}

	private final List<Line> feed = new ArrayList<>();
	private final List<Line> leftPage = new ArrayList<>();
	private final List<Bookmark> bookmarks = new ArrayList<>();
	/** The feature tabs visible when the journal opened, in strip order. */
	private List<JournalTabs.Tab> tabs = List.of();
	/** The open feature tab's panel, or null while the journal's own pages are open. */
	private JournalTabs.@Nullable Panel panel;
	private @Nullable String panelTab;
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
		openTab = JOURNAL_TAB;
		keepTab = false;
	}

	/**
	 * Opens the journal on a tab: {@code "journal"} for its own pages (entries), {@code "codex"} for
	 * its own pages on the codex, or a {@link JournalTabs} id such as {@code "missions"}. An unknown
	 * or hidden tab opens the journal's own pages. Used by the client test steps.
	 */
	public static void openOnTab(String tabId) {
		Minecraft mc = Minecraft.getInstance();

		if (mc.player == null) {
			return;
		}

		boolean codex = "codex".equals(tabId);
		showCodexTab = codex;
		String id = codex ? JOURNAL_TAB : tabId;

		if (mc.gui.screen() instanceof JournalScreen open) {
			open.openTab(id);
			open.rebuild(false);
			return;
		}

		openTab = id;
		keepTab = true;
		mc.gui.setScreen(new JournalScreen());
	}

	@Override
	protected void init() {
		tabs = minecraft.player == null ? List.of() : JournalTabs.visible(minecraft.player);
		// The bookmark strip stands above the book, so the book moves down to make room for it.
		int strip = tabs.isEmpty() ? 0 : STRIP_H;
		scale = Math.min(1f, Math.min((width - 8) / (float) BOOK_W, (height - 8) / (float) (BOOK_H + strip)));
		left = (int) ((width - BOOK_W * scale) / 2);
		top = (int) ((height - (BOOK_H + strip) * scale) / 2 + strip * scale);

		boolean kept = keepTab;
		keepTab = false;

		if (!kept && !UNREAD.isEmpty()) {
			boolean anyEntry = UNREAD.stream().anyMatch(f -> f.startsWith("journal:"));
			showCodexTab = !anyEntry;
			openTab = JOURNAL_TAB;
		}

		openTab(openTab);
		rebuild(!kept);
	}

	/** Opens a bookmark; an unknown or hidden tab opens the journal's own pages. Keeps the panel of a tab already open. */
	private void openTab(String id) {
		JournalTabs.Tab tab = tabs.stream().filter(t -> t.id().equals(id)).findFirst().orElse(null);
		openTab = tab == null ? JOURNAL_TAB : tab.id();

		if (tab == null) {
			panel = null;
			panelTab = null;
		} else if (!tab.id().equals(panelTab)) {
			panel = tab.panel().get();
			panelTab = tab.id();
		}
	}

	private @Nullable ForceData data() {
		return minecraft.player == null ? null : minecraft.player.getAttached(ForceAttachments.FORCE);
	}

	private void rebuild(boolean jumpToUnread) {
		ForceData data = data();
		buildLeftPage(data);
		feed.clear();
		int firstUnreadLine = -1;
		StoryData story = minecraft.player == null ? null : Story.peek(minecraft.player);

		if (data == null || !data.isAwakened()) {
			addParagraph(feed, Component.translatable("journal.oldrepublic.empty"), INK_FADED);
		} else {
			List<String> ids = showCodexTab ? JournalEntries.CODEX : JournalEntries.ENTRIES;
			String prefix = showCodexTab ? "journal.oldrepublic.codex." : "journal.oldrepublic.entry.";
			boolean any = false;
			boolean headers = !showCodexTab && hasEntryFrom(data, CHAPTER_STARTS[1]);
			int chapter = 0;

			for (String id : ids) {
				String flag = showCodexTab ? JournalEntries.codexFlag(id) : JournalEntries.entryFlag(id);

				if (!data.hasFlag(flag)) {
					continue;
				}

				int entryChapter = chapterOf(JournalEntries.ENTRIES.indexOf(id));

				if (headers && entryChapter != chapter) {
					// Chapter headers in the Entries feed once Chapter Two has begun.
					chapter = entryChapter;

					if (any) {
						feed.add(new Line(null, 0));
					}

					addParagraph(feed, Component.translatable("journal.oldrepublic.chapter_header." + chapter).withStyle(ChatFormatting.BOLD, ChatFormatting.UNDERLINE), HEADER);
					feed.add(new Line(null, 0));
					any = false;
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

				if (!showCodexTab && story != null) {
					// Notes added since: in a lighter hand under the entry.
					for (String note : ChapterThreeRules.notes(story, id)) {
						feed.add(new Line(null, 0));
						addParagraph(feed, Component.literal("~ ").append(Component.translatable(note)).withStyle(ChatFormatting.ITALIC), ACCENT);
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

	/** The chapter (1 for Chapter One) of the entry at this index of {@link JournalEntries#ENTRIES}. */
	private static int chapterOf(int entryIndex) {
		int chapter = 1;

		for (int i = 1; i < CHAPTER_STARTS.length; i++) {
			if (entryIndex >= CHAPTER_STARTS[i]) {
				chapter = i + 1;
			}
		}

		return chapter;
	}

	private static boolean hasEntryFrom(ForceData data, int from) {
		List<String> ids = JournalEntries.ENTRIES;

		for (int i = Math.max(0, from); i < ids.size(); i++) {
			if (data.hasFlag(JournalEntries.entryFlag(ids.get(i)))) {
				return true;
			}
		}

		return false;
	}

	private void buildLeftPage(@Nullable ForceData data) {
		leftPage.clear();
		StoryData story = minecraft.player == null ? null : Story.peek(minecraft.player);

		if (story != null && story.chapter4.stage().ordinal() >= ChapterFourStage.SEEKER.ordinal()) {
			buildChapterFourPage(story, data == null ? 1 : data.level);
			return;
		}

		if (story != null && story.chapter3.stage().ordinal() >= ChapterThreeStage.SUMMONS.ordinal()) {
			buildChapterThreePage(story, data == null ? 1 : data.level);
			return;
		}

		String chapterTwo = minecraft.player == null ? null : Story.activeLessonKey(minecraft.player);

		if (chapterTwo != null) {
			buildChapterTwoPage(chapterTwo);
			return;
		}

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

	/** Chapter Two's left page: its title, the current objective and lesson progress. */
	private void buildChapterTwoPage(String objectiveKey) {
		addParagraph(leftPage, Component.translatable("journal.oldrepublic.chapter_two").withStyle(ChatFormatting.BOLD), HEADER);
		leftPage.add(new Line(null, 0));
		addParagraph(leftPage, Component.translatable("journal.oldrepublic.purpose").withStyle(ChatFormatting.ITALIC), INK_FADED);
		addParagraph(leftPage, Component.translatable(objectiveKey), INK);
		StoryData story = minecraft.player == null ? null : Story.peek(minecraft.player);

		if (story == null || story.stage() != PathStage.LESSON_ONE || !story.hasFlag(StoryRules.LESSON_ONE_BEGUN)) {
			return;
		}

		leftPage.add(new Line(null, 0));
		int pulls = Math.max(story.counter(StoryRules.PULL_TARGETS), story.counter(StoryRules.PULL_KILLS));
		addParagraph(leftPage, Component.translatable("journal.oldrepublic.progress.lesson", pulls), ACCENT);
	}

	/**
	 * Chapter Three's left page: the title, the objective with its counts, the Collector waiting on
	 * the player's answer, and from LEADS the trail and the level gate. Lines past the page's room
	 * (above the level and Resonance at its foot) are dropped.
	 */
	private void buildChapterThreePage(StoryData story, int level) {
		StoryData.Chapter3 c = story.chapter3;
		addParagraph(leftPage, Component.translatable("journal.oldrepublic.chapter_three").withStyle(ChatFormatting.BOLD), HEADER);
		leftPage.add(new Line(null, 0));
		addParagraph(leftPage, Component.translatable("journal.oldrepublic.purpose").withStyle(ChatFormatting.ITALIC), INK_FADED);
		Component objective = ChapterThreeRules.objective(story, level, CHAPTER_THREE_DEFAULTS);

		if (objective != null) {
			addParagraph(leftPage, objective, INK);
		}

		if (c.stage() == ChapterThreeStage.CONFRONTATION && c.boss() > 0) {
			leftPage.add(new Line(null, 0));
			addParagraph(leftPage, Component.translatable(c.boss() == 1 ? "quest.oldrepublic.c3.journal.boss.yielded" : "quest.oldrepublic.c3.journal.boss.resolved"),
					ACCENT);
		}

		boolean trail = c.stage().ordinal() >= ChapterThreeStage.LEADS.ordinal() && c.stage().ordinal() <= ChapterThreeStage.RELIQUARY.ordinal();

		if (trail) {
			leftPage.add(new Line(null, 0));
			addParagraph(leftPage, Component.translatable("quest.oldrepublic.c3.journal.leads").withStyle(ChatFormatting.ITALIC), INK_FADED);
			int lines = c.leads().size() <= LEADS_TWO_LINES ? 2 : 1;

			for (String lead : c.leads()) {
				addClipped(leftPage, Component.literal("✔ ").append(Component.translatable(ChapterThreeRules.leadKey(lead))), ACCENT, lines);
			}

			for (int i = c.leads().size(); i < CHAPTER_THREE_DEFAULTS.leadsNeeded; i++) {
				addParagraph(leftPage, Component.literal("• ").append(Component.translatable("quest.oldrepublic.c3.journal.lead_open")), INK_FADED);
			}

			if (c.stage() == ChapterThreeStage.LEADS && ChapterThreeRules.levelGated(c.leads().size(), level, CHAPTER_THREE_DEFAULTS)) {
				leftPage.add(new Line(null, 0));
				addParagraph(leftPage, Component.translatable("quest.oldrepublic.c3.journal.gate"), HEADER);
				addParagraph(leftPage, Component.translatable("quest.oldrepublic.c3.journal.xp_sources"), INK_FADED);
			}
		}

		// Keep clear of the level, Resonance and skill points at the page's foot.
		int room = (FEED_BOTTOM - font.lineHeight * 3 - 10 - (PAGE_TOP + 2)) / font.lineHeight;

		while (leftPage.size() > room) {
			leftPage.removeLast();
		}
	}

	/**
	 * Chapter Four's left page (spec MILESTONE_4.md 8.1, 8.9): the title, the objective with its
	 * counts and, while the world arc runs, the arc's checklist (each step ticked when done, the
	 * expeditions with their count). Lines past the page's room are dropped, as on Chapter Three's.
	 */
	private void buildChapterFourPage(StoryData story, int level) {
		StoryData.Chapter4 c = story.chapter4;
		addParagraph(leftPage, Component.translatable("journal.oldrepublic.chapter_four").withStyle(ChatFormatting.BOLD), HEADER);
		leftPage.add(new Line(null, 0));
		addParagraph(leftPage, Component.translatable("journal.oldrepublic.purpose").withStyle(ChatFormatting.ITALIC), INK_FADED);
		ChapterFourRules.Objective objective = ChapterFourRules.objective(story, ChapterFourRules.partsFitted(story), level, CHAPTER_FOUR_DEFAULTS);

		if (objective != null) {
			addParagraph(leftPage, objective.text(), INK);
		}

		List<ChapterFourRules.Objective> steps = c.stage() == ChapterFourStage.WORLD_ARC ? ChapterFourRules.arcChecklist(c, story.storyPath()) : List.of();

		if (!steps.isEmpty()) {
			leftPage.add(new Line(null, 0));
			addParagraph(leftPage, Component.translatable("quest.oldrepublic.c4.journal.arc").withStyle(ChatFormatting.ITALIC), INK_FADED);

			for (ChapterFourRules.Objective step : steps) {
				boolean done = !step.args().isEmpty() && step.args().getFirst() instanceof Integer n && n > 0;
				addClipped(leftPage, Component.literal(done ? "✔ " : "• ").append(step.text()), done ? ACCENT : INK_FADED, 2);
			}
		}

		if (c.stage() == ChapterFourStage.HUNTED || c.stage() == ChapterFourStage.CHALLENGED) {
			leftPage.add(new Line(null, 0));
			addParagraph(leftPage, Component.translatable("quest.oldrepublic.c4.journal.hunted").withStyle(ChatFormatting.ITALIC), INK_FADED);
		}

		int room = (FEED_BOTTOM - font.lineHeight * 3 - 10 - (PAGE_TOP + 2)) / font.lineHeight;

		while (leftPage.size() > room) {
			leftPage.removeLast();
		}
	}

	/** A paragraph of at most {@code maxLines} lines; a cut one ends in an ellipsis. */
	private void addClipped(List<Line> into, Component text, int color, int maxLines) {
		List<FormattedCharSequence> lines = font.split(text, PAGE_W);

		for (int i = 0; i < Math.min(maxLines, lines.size()); i++) {
			FormattedCharSequence line = lines.get(i);

			if (i == maxLines - 1 && lines.size() > maxLines) {
				String plain = Ui.fit(font, toPlain(line) + "…", PAGE_W);
				line = Component.literal(plain).getVisualOrderText();
			}

			into.add(new Line(line, color));
		}
	}

	/** The characters of a formatted line, without its styles. */
	private static String toPlain(FormattedCharSequence line) {
		StringBuilder out = new StringBuilder();
		line.accept((index, style, codePoint) -> {
			out.appendCodePoint(codePoint);
			return true;
		});
		return out.toString();
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

		// Bookmarks first, so the cover's top edge overlaps their feet.
		drawBookmarks(graphics, lx, ly);
		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, 0, 0, 0f, 0f, BOOK_W, BOOK_H, BOOK_W, BOOK_H);
		drawPageDecoration(graphics);

		if (panel != null) {
			panel.render(graphics, font, LEFT_X, PAGE_TOP, RIGHT_X + PAGE_W - LEFT_X, FEED_BOTTOM - PAGE_TOP, (int) Math.floor(lx), (int) Math.floor(ly), partialTick);
		} else {
			drawLeftPage(graphics);
			drawTabs(graphics, lx, ly);
			drawFeed(graphics);
		}

		graphics.pose().popMatrix();
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	/**
	 * The bookmark strip above the book, when feature tabs exist: paper tabs tucked under the top
	 * edge of the cover, the open one raised, in the page's colour and edged in ink.
	 */
	private void drawBookmarks(GuiGraphicsExtractor graphics, double lx, double ly) {
		bookmarks.clear();

		if (tabs.isEmpty()) {
			return;
		}

		int x = BOOKMARK_X;
		bookmarks.add(new Bookmark(null, x, bookmarkWidth(Component.translatable("journal.oldrepublic.tab.journal"))));

		for (JournalTabs.Tab tab : tabs) {
			Bookmark last = bookmarks.getLast();
			bookmarks.add(new Bookmark(tab, last.x() + last.w() + 2, bookmarkWidth(tab.label())));
		}

		for (Bookmark bookmark : bookmarks) {
			Component label = bookmark.tab() == null ? Component.translatable("journal.oldrepublic.tab.journal") : bookmark.tab().label();
			boolean selected = openTab.equals(bookmark.tab() == null ? JOURNAL_TAB : bookmark.tab().id());
			boolean hover = hit(bookmark, lx, ly);
			int x0 = bookmark.x();
			int x1 = x0 + bookmark.w();
			int y0 = selected ? -STRIP_H : -STRIP_H + 2;

			graphics.fill(x0, y0, x1, 3, selected ? 0xFFEBDDBF : hover ? 0xFFD6C29A : 0xFFC4AE84);
			graphics.fill(x0, y0, x1, y0 + 1, selected ? HEADER : INK_FADED);
			graphics.fill(x0, y0, x0 + 1, 3, selected ? HEADER : INK_FADED);
			graphics.fill(x1 - 1, y0, x1, 3, selected ? HEADER : INK_FADED);
			int color = selected ? HEADER : hover ? INK : INK_FADED;
			graphics.text(font, label, x0 + (bookmark.w() - font.width(label)) / 2, y0 + 2, color, false);
		}
	}

	private int bookmarkWidth(Component label) {
		return font.width(label) + 12;
	}

	private static boolean hit(Bookmark bookmark, double lx, double ly) {
		return lx >= bookmark.x() && lx < bookmark.x() + bookmark.w() && ly >= -STRIP_H && ly < 3;
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

		if (event.button() == 0) {
			for (Bookmark bookmark : bookmarks) {
				if (hit(bookmark, lx, ly)) {
					String id = bookmark.tab() == null ? JOURNAL_TAB : bookmark.tab().id();

					if (!id.equals(openTab)) {
						openTab(id);
						rebuild(true);
						QuestSounds.ui(ModSounds.JOURNAL_OPEN, 1.25f, 0.45f);
					}

					return true;
				}
			}
		}

		if (panel != null) {
			return panel.mouseClicked(new MouseButtonEvent(lx, ly, event.buttonInfo()), doubleClick) || super.mouseClicked(event, doubleClick);
		}

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
		if (panel != null) {
			return panel.mouseScrolled(toLocalX(mouseX), toLocalY(mouseY), scrollX, scrollY);
		}

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
