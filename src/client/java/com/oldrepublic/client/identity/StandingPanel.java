package com.oldrepublic.client.identity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.quest.QuestSounds;
import com.oldrepublic.client.ui.PortraitFrame;
import com.oldrepublic.client.ui.Ui;
import com.oldrepublic.client.ui.UiAnim;
import com.oldrepublic.client.ui.UiColors;
import com.oldrepublic.identity.AlignmentBand;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.IdentityData;
import com.oldrepublic.identity.IdentityRules;
import com.oldrepublic.identity.Path;
import com.oldrepublic.identity.ReputationTier;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/**
 * The Standing tab of the Force screen: alignment bar and shift log, faction reputation, path and
 * rank, the mentor card with trust, and the active lesson. The skills feature's Force screen hosts
 * one instance as its last tab and forwards drawing and clicks; the identity feature owns the
 * contents.
 *
 * <p>Three pages, picked with chips along the top: <b>Alignment</b> (the bar from deep red to
 * pale gold with the band edges marked, the band and value, the last 8 shifts), <b>Factions</b>
 * (a centred bar per faction with tier edges, the tier and what it unlocks) and <b>Path</b> (path,
 * rank and the next rank's requirements with ✓/✗, and the mentor card with its trust bar).
 * Everything is read from the synced identity data.
 */
public class StandingPanel {
	/** Trust thresholds shown on the mentor card: lesson 1, lesson 2, rank trial (section 6.3). */
	private static final int[] TRUST_MARKS = {20, 30, 60};
	private static final int TRIAL_TRUST = 60;
	/** Band edges ticked on the alignment bar (the default edges, as {@link AlignmentBand#of}). */
	private static final int[] BAND_EDGES = {-50, -15, 15, 50};
	/** Tier edges ticked on the reputation bars (the default edges, as {@link ReputationTier#of}). */
	private static final int[] TIER_EDGES = {-500, -100, 100, 500};
	/** The faction rows in display order (spec section 5.1): the Republic sits with the two orders, above the Frontier. */
	private static final Faction[] FACTION_ROWS = {Faction.JEDI_ORDER, Faction.SITH_EMPIRE, Faction.REPUBLIC, Faction.FRONTIER};
	private static final int CHIP_H = 12;
	private static final int PAD = 8;
	private static final String KEY = "screen.oldrepublic.standing.";

	private enum Page {
		ALIGNMENT, FACTIONS, PATH
	}

	private record Chip(Page page, int x, int y, int w, int h) {
	}

	private static @Nullable Function<String, @Nullable Component> lessonSource;

	private Page page = Page.ALIGNMENT;
	private final List<Chip> chips = new ArrayList<>();

	/**
	 * Lets the quest feature describe the active lesson of a mentor ({@code "jedi"} or {@code "sith"})
	 * on the mentor card; return null for none.
	 */
	public static void setLessonSource(@Nullable Function<String, @Nullable Component> source) {
		lessonSource = source;
	}

	/**
	 * Selects a page by id ({@code alignment}, {@code factions} or {@code path}), for a host that
	 * wants to open the panel on a particular page. Unknown ids are ignored.
	 */
	public void open(String pageId) {
		for (Page p : Page.values()) {
			if (p.name().equalsIgnoreCase(pageId)) {
				page = p;
			}
		}
	}

	/** Draws the panel inside the given box (GUI coordinates). */
	public void render(GuiGraphicsExtractor graphics, Font font, int x, int y, int w, int h, float partialTick) {
		float time = UiAnim.time(partialTick);
		Ui.inset(graphics, x, y, w, h, 1f);
		Minecraft mc = Minecraft.getInstance();
		IdentityData view = mc.player == null ? null : Identity.view(mc.player);
		IdentityData data = view != null ? view : new IdentityData();

		drawChips(graphics, font, x + PAD, y + 5, time);
		int top = y + 5 + CHIP_H + 6;
		int ix = x + PAD;
		int iw = w - PAD * 2;
		int bottom = y + h - 4;

		switch (page) {
			case ALIGNMENT -> drawAlignment(graphics, font, data, ix, top, iw, bottom, time);
			case FACTIONS -> drawFactions(graphics, font, data, ix, top, iw, bottom, time);
			case PATH -> drawPath(graphics, font, data, ix, top, iw, bottom, time);
		}
	}

	/** Handles a click forwarded by the host screen. Returns true if the panel used it. */
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() != 0) {
			return false;
		}

		for (Chip chip : chips) {
			if (event.x() >= chip.x() && event.x() < chip.x() + chip.w() && event.y() >= chip.y() && event.y() < chip.y() + chip.h()) {
				if (page != chip.page()) {
					page = chip.page();
					QuestSounds.ui(ModSounds.DIALOGUE_SELECT, 1.2f, 0.4f);
				}

				return true;
			}
		}

		return false;
	}

	private void drawChips(GuiGraphicsExtractor graphics, Font font, int x, int y, float time) {
		chips.clear();
		int cx = x;

		for (Page p : Page.values()) {
			Component label = Component.translatable(KEY + "page." + p.name().toLowerCase(Locale.ROOT));
			int w = font.width(label) + 12;
			boolean selected = p == page;
			graphics.fill(cx, y, cx + w, y + CHIP_H, Ui.a(selected ? UiColors.HOLO_DEEP : UiColors.STEEL_DARK, 0.9f));
			graphics.fill(cx, y + CHIP_H - 1, cx + w, y + CHIP_H, Ui.a(selected ? UiColors.HOLO : UiColors.STEEL, selected ? UiAnim.flicker(time) : 0.8f));
			Ui.text(graphics, font, label, cx + 6, y + 2, selected ? UiColors.HOLO_BRIGHT : UiColors.TEXT_MUTED, 1f);
			chips.add(new Chip(p, cx, y, w, CHIP_H));
			cx += w + 4;
		}
	}

	// ---------------------------------------------------------------- alignment

	private void drawAlignment(GuiGraphicsExtractor graphics, Font font, IdentityData data, int x, int y, int w, int bottom, float time) {
		AlignmentBand band = AlignmentBand.of(data.alignment);
		Ui.header(graphics, font, Component.translatable(KEY + "alignment"), x, y, w, time);
		y += 16;

		Component bandName = Component.translatable("alignment.oldrepublic.band." + band.name().toLowerCase(Locale.ROOT));
		Ui.text(graphics, font, bandName, x, y, IdentityStyle.band(band), 1f);
		Ui.text(graphics, font, signed(data.alignment), x + font.width(bandName) + 6, y, UiColors.TEXT_MUTED, 1f);
		y += 12;

		drawAlignmentBar(graphics, x, y, w, 9, data.alignment, time);
		y += 12;
		Ui.text(graphics, font, Component.translatable(KEY + "dark_end"), x, y, IdentityStyle.DARK, 0.8f);
		Component lightEnd = Component.translatable(KEY + "light_end");
		Ui.text(graphics, font, lightEnd, x + w - font.width(lightEnd), y, IdentityStyle.LIGHT, 0.8f);
		y += 14;

		Ui.header(graphics, font, Component.translatable(KEY + "recent"), x, y, w, time);
		y += 16;

		if (data.alignmentLog.isEmpty()) {
			Ui.text(graphics, font, Component.translatable(KEY + "no_shifts"), x, y, UiColors.TEXT_DIM, 1f);
			return;
		}

		for (int i = data.alignmentLog.size() - 1; i >= 0 && y + 9 <= bottom; i--) {
			IdentityData.LogEntry entry = data.alignmentLog.get(i);
			Component value = Component.translatable(KEY + "log_value", entry.day() + 1, signed(entry.amount()));
			Component reason = Component.literal(Ui.fit(font, Component.translatable(entry.reason()).getString(), w - font.width(value) - 12));
			Ui.labelValue(graphics, font, reason, value, x, y, w, UiColors.TEXT, entry.amount() >= 0 ? IdentityStyle.LIGHT : IdentityStyle.DARK);
			y += 11;
		}
	}

	/** The -100 to +100 bar: deep red-black through steel to pale gold, band edges ticked, a marker at the value. */
	private static void drawAlignmentBar(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int alignment, float time) {
		Ui.frame(graphics, x, y, w, h, 1f);
		int inner = w - 2;

		for (int dx = 0; dx < inner; dx += 2) {
			float t = (float) dx / Math.max(1, inner - 1);
			graphics.fill(x + 1 + dx, y + 1, x + 1 + Math.min(inner, dx + 2), y + h - 1, Ui.a(alignmentColor(t), 0.8f));
		}

		for (int edge : BAND_EDGES) {
			int ex = x + 1 + Math.round(inner * (edge + 100) / 200f);
			graphics.fill(ex, y + 1, ex + 1, y + h - 1, 0x90000000);
		}

		int centre = x + 1 + inner / 2;
		graphics.fill(centre, y - 1, centre + 1, y + h + 1, Ui.a(UiColors.TEXT_MUTED, 0.6f));

		int mx = x + 1 + Math.round((inner - 1) * (Mth.clamp(alignment, -100, 100) + 100) / 200f);
		float glow = 0.6f + 0.4f * UiAnim.pulse(time, 40f);
		graphics.fill(mx - 1, y - 2, mx + 2, y + h + 2, Ui.a(UiColors.OUTLINE, 0.9f));
		graphics.fill(mx, y - 2, mx + 1, y + h + 2, Ui.a(0xFFFFFFFF, glow));
	}

	private static int alignmentColor(float t) {
		if (t < 0.25f) {
			return ARGB.srgbLerp(t / 0.25f, IdentityStyle.DARK_DEEP, IdentityStyle.DARK);
		}

		if (t < 0.5f) {
			return ARGB.srgbLerp((t - 0.25f) / 0.25f, IdentityStyle.DARK, UiColors.STEEL);
		}

		if (t < 0.75f) {
			return ARGB.srgbLerp((t - 0.5f) / 0.25f, UiColors.STEEL, IdentityStyle.LIGHT_DEEP);
		}

		return ARGB.srgbLerp((t - 0.75f) / 0.25f, IdentityStyle.LIGHT_DEEP, IdentityStyle.LIGHT);
	}

	// ---------------------------------------------------------------- factions

	private void drawFactions(GuiGraphicsExtractor graphics, Font font, IdentityData data, int x, int y, int w, int bottom, float time) {
		Ui.header(graphics, font, Component.translatable(KEY + "factions"), x, y, w, time);
		y += 16;
		int rowH = Mth.clamp((bottom - y) / FACTION_ROWS.length, 26, 46);

		for (Faction faction : FACTION_ROWS) {
			if (y + 20 > bottom) {
				break;
			}

			drawFaction(graphics, font, faction, data.reputation(faction), x, y, w, rowH);
			y += rowH;
		}
	}

	private static void drawFaction(GuiGraphicsExtractor graphics, Font font, Faction faction, int rep, int x, int y, int w, int rowH) {
		ReputationTier tier = ReputationTier.of(rep);
		Component name = Component.translatable("faction.oldrepublic." + faction.id());
		Component tierText = Component.translatable(KEY + "tier_value",
				Component.translatable("faction.oldrepublic.tier." + tier.name().toLowerCase(Locale.ROOT)), signed(rep));
		Ui.sprite(graphics, OldRepublicMod.id("identity/faction_" + faction.id()), x, y, 9, 9, 1f);
		Ui.text(graphics, font, name, x + 12, y + 1, IdentityStyle.faction(faction), 1f);
		Ui.text(graphics, font, tierText, x + w - font.width(tierText), y + 1, IdentityStyle.tier(tier), 1f);

		int barY = y + 12;
		int barH = 6;
		Ui.frame(graphics, x, barY, w, barH, 1f);
		float centre = 0.5f;
		float value = (Mth.clamp(rep, -IdentityRules.REPUTATION_LIMIT, IdentityRules.REPUTATION_LIMIT) + IdentityRules.REPUTATION_LIMIT)
				/ (2f * IdentityRules.REPUTATION_LIMIT);
		Ui.span(graphics, x, barY, w, barH, centre, value, IdentityStyle.tier(tier), 0.85f);

		for (int edge : TIER_EDGES) {
			int ex = x + 1 + Math.round((w - 2) * (edge + IdentityRules.REPUTATION_LIMIT) / (2f * IdentityRules.REPUTATION_LIMIT));
			graphics.fill(ex, barY + 1, ex + 1, barY + barH - 1, Ui.a(UiColors.TEXT_DIM, 0.8f));
		}

		int mid = x + w / 2;
		graphics.fill(mid, barY - 1, mid + 1, barY + barH + 1, Ui.a(UiColors.TEXT_MUTED, 0.7f));

		int lines = Math.max(0, (rowH - 22) / 9);

		if (lines > 0) {
			Component unlock = Component.translatable(KEY + "unlock." + faction.id());
			int ly = barY + barH + 3;

			for (String line : Ui.wrap(font, unlock.getString(), w, lines)) {
				Ui.text(graphics, font, line, x, ly, UiColors.TEXT_DIM, 1f);
				ly += 9;
			}
		}
	}

	// ---------------------------------------------------------------- path, rank and mentor

	private void drawPath(GuiGraphicsExtractor graphics, Font font, IdentityData data, int x, int y, int w, int bottom, float time) {
		int leftW = Math.round(w * 0.55f);
		int rightX = x + leftW + 8;
		int rightW = w - leftW - 8;
		drawRank(graphics, font, data, x, y, leftW, bottom, time);
		drawMentors(graphics, font, data, rightX, y, rightW, bottom, time);
	}

	private void drawRank(GuiGraphicsExtractor graphics, Font font, IdentityData data, int x, int y, int w, int bottom, float time) {
		Ui.header(graphics, font, Component.translatable(KEY + "path"), x, y, w, time);
		y += 16;
		Path path = data.path;
		Ui.text(graphics, font, Component.translatable("path.oldrepublic." + path.id()), x, y, IdentityStyle.path(path), 1f);
		y += 11;

		if (path == Path.NONE) {
			wrapped(graphics, font, Component.translatable(KEY + "no_path"), x, y, w, bottom);
			return;
		}

		Ui.text(graphics, font, Component.translatable("rank.oldrepublic." + path.id() + "." + data.rank), x, y, UiColors.TEXT, 1f);

		if (path != Path.INDEPENDENT) {
			Ui.pips(graphics, x + w - 24, y + 2, 4, data.rank, IdentityStyle.path(path), 1f);
		}

		y += 14;
		Ui.header(graphics, font, Component.translatable(KEY + "next_rank"), x, y, w, time);
		y += 16;

		if (path == Path.INDEPENDENT) {
			wrapped(graphics, font, Component.translatable(KEY + "independent_note"), x, y, w, bottom);
			return;
		}

		if (data.rank >= 2) {
			wrapped(graphics, font, Component.translatable(KEY + "req.later." + path.id()), x, y, w, bottom);
			return;
		}

		boolean jedi = path == Path.JEDI;
		Faction own = jedi ? Faction.JEDI_ORDER : Faction.SITH_EMPIRE;
		String mentor = jedi ? "jedi" : "sith";
		boolean trusted = ReputationTier.of(data.reputation(own)).ordinal() >= ReputationTier.TRUSTED.ordinal();
		boolean aligned = jedi ? data.alignment >= 0 : data.alignment <= 0;

		y = requirement(graphics, font, Component.translatable(KEY + "req.reputation",
				Component.translatable("faction.oldrepublic.tier.trusted"), Component.translatable("faction.oldrepublic." + own.id())), trusted, x, y, w);
		y = requirement(graphics, font, Component.translatable(KEY + "req.trust", TRIAL_TRUST), data.trust(mentor) >= TRIAL_TRUST, x, y, w);
		y = requirement(graphics, font, Component.translatable(KEY + "req.alignment_" + (jedi ? "light" : "dark")), aligned, x, y, w);
		Ui.text(graphics, font, "•", x + 1, y, UiColors.TEXT_DIM, 1f);
		wrapped(graphics, font, Component.translatable(KEY + "req.trial." + path.id()), x + 10, y, w - 10, bottom);
	}

	private static int requirement(GuiGraphicsExtractor graphics, Font font, Component text, boolean met, int x, int y, int w) {
		Ui.text(graphics, font, met ? "✓" : "✗", x, y, met ? UiColors.OK : UiColors.DANGER, 1f);
		Ui.text(graphics, font, Ui.fit(font, text.getString(), w - 10), x + 10, y, met ? UiColors.TEXT : UiColors.TEXT_MUTED, 1f);
		return y + 11;
	}

	private void drawMentors(GuiGraphicsExtractor graphics, Font font, IdentityData data, int x, int y, int w, int bottom, float time) {
		Ui.header(graphics, font, Component.translatable(KEY + "mentor"), x, y, w, time);
		y += 16;
		List<String> mentors = new ArrayList<>();

		switch (data.path) {
			case JEDI -> mentors.add("jedi");
			case SITH -> mentors.add("sith");
			case INDEPENDENT -> {
				for (String m : new String[] {"jedi", "sith"}) {
					if (data.trust(m) > 0) {
						mentors.add(m);
					}
				}
			}
			case NONE -> {
			}
		}

		if (mentors.isEmpty()) {
			wrapped(graphics, font, Component.translatable(KEY + "no_mentor"), x, y, w, bottom);
			return;
		}

		for (String mentor : mentors) {
			if (y + 40 > bottom) {
				break;
			}

			y = drawMentorCard(graphics, font, mentor, data.trust(mentor), x, y, w, bottom, time) + 6;
		}
	}

	private static int drawMentorCard(GuiGraphicsExtractor graphics, Font font, String mentor, int trust, int x, int y, int w, int bottom, float time) {
		int size = 32;
		int accent = "jedi".equals(mentor) ? IdentityStyle.LIGHT : IdentityStyle.DARK;
		PortraitFrame.draw(graphics, x, y, size, OldRepublicMod.id("portrait/" + mentor + "_mentor"), accent, time, 1f);
		int tx = x + size + 6;
		int tw = w - size - 6;
		Ui.text(graphics, font, Component.translatable(KEY + "mentor." + mentor), tx, y, UiColors.TEXT_WARM, 1f);
		Ui.text(graphics, font, Component.translatable(KEY + "trust_value", trust), tx, y + 10, UiColors.TEXT_MUTED, 1f);

		int barY = y + 22;
		Ui.bar(graphics, tx, barY, tw, 6, trust / (float) IdentityRules.TRUST_MAX, accent, ARGB.srgbLerp(0.5f, accent, UiColors.STEEL_DARK), 0, 1f);

		for (int mark : TRUST_MARKS) {
			int mx = tx + 1 + Math.round((tw - 2) * mark / (float) IdentityRules.TRUST_MAX);
			boolean reached = trust >= mark;
			graphics.fill(mx, barY - 2, mx + 1, barY + 8, Ui.a(reached ? UiColors.BRASS_LIGHT : UiColors.TEXT_DIM, 0.9f));
		}

		int ly = Math.max(y + size + 4, barY + 10);
		Ui.text(graphics, font, Component.translatable(KEY + "trust_marks"), x, ly, UiColors.TEXT_DIM, 1f);
		ly += 10;
		Component lesson = lessonSource == null ? null : lessonSource.apply(mentor);

		if (lesson != null && ly + 9 <= bottom) {
			Ui.text(graphics, font, Ui.fit(font, lesson.getString(), w), x, ly, UiColors.HOLO, 1f);
			ly += 10;
		}

		return ly;
	}

	// ---------------------------------------------------------------- helpers

	private static void wrapped(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int w, int bottom) {
		int lines = Math.max(1, (bottom - y) / 10);

		for (String line : Ui.wrap(font, text.getString(), w, lines)) {
			Ui.text(graphics, font, line, x, y, UiColors.TEXT_MUTED, 1f);
			y += 10;
		}
	}

	private static String signed(int value) {
		return value > 0 ? "+" + value : Integer.toString(value);
	}
}
