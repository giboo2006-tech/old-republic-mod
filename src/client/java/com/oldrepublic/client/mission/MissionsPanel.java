package com.oldrepublic.client.mission;

import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.quest.JournalTabs;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.IdentityRules;
import com.oldrepublic.identity.Path;
import com.oldrepublic.identity.ReputationTier;
import com.oldrepublic.mission.MissionBalance;
import com.oldrepublic.mission.MissionData;
import com.oldrepublic.mission.MissionRules;
import com.oldrepublic.mission.MissionService;
import com.oldrepublic.mission.Missions;
import com.oldrepublic.mission.network.TrackerHintPayload;
import com.oldrepublic.quest.story.ChapterThree;
import com.oldrepublic.quest.story.ChapterThreeBalance;
import com.oldrepublic.quest.story.ChapterThreeStage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * The Missions tab of the Wanderer's Journal (spec 5.7, 11.3), in the journal's ink. The left page
 * holds the mission in hand (its title, objective, counter and distance band), today's full-pay
 * count, the mentor's duty for a Jedi or Sith, and the own-side contracts counted toward rank 3
 * (Knight, Lord or Pathfinder). The right page lists the four story missions: done, in hand,
 * available (and from whom), or locked with the reason.
 *
 * <p>Everything is read from the synced mission, identity and story data; the client has no
 * balance file, so the full-pay count and the rank 3 threshold shown use the defaults (3 each), and
 * the server applies the live ones.
 */
final class MissionsPanel implements JournalTabs.Panel {
	private static final int INK = 0xFF2E2418;
	private static final int INK_FADED = 0xFF6B5D4A;
	private static final int HEADER = 0xFF5C2A17;
	private static final int ACCENT = 0xFF2C5873;
	private static final int RULE = 0x605C2A17;
	private static final int GREEN_INK = 0xFF3C6B2E;
	private static final int WAX = 0xFF8E2A20;

	private static final int PAGE_W = 128;
	private static final int RIGHT_OFFSET = 148;
	private static final int OBJECTIVE_LINES = 3;
	private static final int REASON_LINES = 2;
	private static final int STATUS_INDENT = 6;
	/**
	 * The client reads no balance file: the rank 3 contract counts, the full-pay count and the duty
	 * count shown are the defaults; a server that tunes them shows the default numbers here (a known
	 * issue, as for the Standing panel's rank 3 checklist).
	 */
	private static final ChapterThreeBalance SHOWN = new ChapterThreeBalance();
	private static final MissionBalance SHOWN_MISSIONS = new MissionBalance();

	/** Shown from Chapter Three's summons, or as soon as the player has any mission history. */
	static boolean visible(LocalPlayer player) {
		if (ChapterThree.atLeast(player, ChapterThreeStage.SUMMONS)) {
			return true;
		}

		MissionData data = MissionService.view(player);
		return data != null && (data.active != null || !data.storyDone.isEmpty() || !data.ownSide.isEmpty());
	}

	@Override
	public void render(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mouseX, int mouseY, float partialTick) {
		LocalPlayer player = Minecraft.getInstance().player;

		if (player == null) {
			return;
		}

		MissionData view = MissionService.view(player);
		MissionData data = view != null ? view : new MissionData();
		drawLeft(g, font, player, data, x, y, h);
		drawRight(g, font, player, data, x + RIGHT_OFFSET, y);
	}

	// ---------------------------------------------------------------- left page

	private void drawLeft(GuiGraphicsExtractor g, Font font, LocalPlayer player, MissionData data, int x, int y, int h) {
		int ty = heading(g, font, Component.translatable("screen.oldrepublic.missions.heading"), x, y);
		MissionData.Active active = data.active;

		if (active == null) {
			ty = paragraph(g, font, Component.translatable("screen.oldrepublic.missions.none"), x, ty, PAGE_W, INK_FADED, 4);
		} else {
			g.text(font, Component.translatable("screen.oldrepublic.missions.in_hand"), x, ty, INK_FADED, false);
			ty += font.lineHeight;
			ty = paragraph(g, font, Component.translatable("mission.oldrepublic." + active.id + ".title"), x, ty, PAGE_W, INK, 2);
			ty = paragraph(g, font, Component.translatable(MissionRules.objectiveKey(active.base, active.stage)), x, ty, PAGE_W, ACCENT, OBJECTIVE_LINES);
			Component where = whereLine(active, ClientMissionState.freshHint());

			if (where != null) {
				g.text(font, where, x, ty, INK_FADED, false);
				ty += font.lineHeight;
			}
		}

		int base = y + h - font.lineHeight * 4 - 4;
		g.fill(x, base - 4, x + PAGE_W, base - 3, RULE);
		int today = IdentityRules.dayIndex(player.level().getGameTime());
		int used = data.contractDay == today ? data.contractsToday : 0;
		int max = SHOWN_MISSIONS.fullPayPerDay;
		g.text(font, Component.translatable("screen.oldrepublic.missions.full_pay", Math.min(used, max), max), x, base, used >= max ? WAX : INK, false);
		Path path = Identity.path(player);

		if (MissionRules.dutyFor(path) != null) {
			boolean open = MissionRules.dutyOpen(data, today, SHOWN_MISSIONS.dutiesPerDay);
			g.text(font, Component.translatable(open ? "screen.oldrepublic.missions.duty.open" : "screen.oldrepublic.missions.duty.taken"), x,
					base + font.lineHeight, open ? INK : INK_FADED, false);
		}

		int own = Missions.ownSideContracts(player);
		int needed = path == Path.INDEPENDENT ? SHOWN.pathfinderContracts : SHOWN.rank3Contracts;
		Component rank = Component.translatable("screen.oldrepublic.missions.rank3." + path.name().toLowerCase(Locale.ROOT));
		g.text(font, rank, x, base + font.lineHeight * 2, INK_FADED, false);
		g.text(font, Component.translatable("screen.oldrepublic.missions.own_side", Math.min(own, needed), needed), x, base + font.lineHeight * 3,
				own >= needed ? GREEN_INK : INK, false);
	}

	/** "Near, 2/3" from the tracker hint: the band or the rough distance (as the tracker shows it), and the counter while the objective counts. */
	private static @Nullable Component whereLine(MissionData.Active active, @Nullable TrackerHintPayload hint) {
		MissionRules.Band band = hint == null ? MissionRules.Band.NONE : hint.bandValue();
		Component bandText = MissionTrackerHud.bandLabel(active, band, hint);

		if (bandText == null) {
			return null;
		}

		boolean counting = active.stage == MissionData.Stage.ACTIVE || active.stage == MissionData.Stage.DECIDE;
		int progress = hint != null ? hint.progress() : active.progress;
		int goal = hint != null ? hint.target() : active.target;

		if (counting && goal > 1) {
			return Component.translatable("screen.oldrepublic.missions.where_counter", bandText, progress, goal);
		}

		return Component.translatable("screen.oldrepublic.missions.where", bandText);
	}

	// ---------------------------------------------------------------- right page

	private void drawRight(GuiGraphicsExtractor g, Font font, LocalPlayer player, MissionData data, int x, int y) {
		int ty = heading(g, font, Component.translatable("screen.oldrepublic.missions.story"), x, y);

		for (MissionRules.StoryMission mission : MissionRules.STORY_MISSIONS) {
			g.text(font, Component.translatable("mission.oldrepublic." + mission.id() + ".title"), x, ty, INK, false);
			ty += font.lineHeight;
			Status status = status(player, data, mission);
			ty = paragraph(g, font, status.text(), x + STATUS_INDENT, ty, PAGE_W - STATUS_INDENT, status.color(), REASON_LINES) + 3;
		}
	}

	/** A story mission's line on the right page and its ink. */
	private record Status(Component text, int color) {
	}

	/** Done, in hand, available from its army's officer, or locked with the first reason (the officers check the same). */
	private static Status status(LocalPlayer player, MissionData data, MissionRules.StoryMission mission) {
		String army = mission.army() == Faction.REPUBLIC ? "republic" : "imperial";

		if (data.storyDone(mission.id())) {
			return new Status(Component.translatable("screen.oldrepublic.missions.status.done"), GREEN_INK);
		} else if (data.active != null && data.active.id.equals(mission.id())) {
			return new Status(Component.translatable("screen.oldrepublic.missions.status.active"), ACCENT);
		} else if (!ChapterThree.atLeast(player, ChapterThreeStage.SUMMONS)) {
			return locked(Component.translatable("screen.oldrepublic.missions.locked.story"));
		} else if (mission.requires() != null && !data.storyDone(mission.requires())) {
			return locked(Component.translatable("screen.oldrepublic.missions.locked.requires",
					Component.translatable("mission.oldrepublic." + mission.requires() + ".title")));
		} else if (Identity.tier(player, mission.army()).ordinal() < ReputationTier.NEUTRAL.ordinal()) {
			return locked(Component.translatable("screen.oldrepublic.missions.locked.standing",
					Component.translatable("faction.oldrepublic." + mission.army().id())));
		}

		return new Status(Component.translatable("screen.oldrepublic.missions.status.available." + army), INK);
	}

	private static Status locked(Component reason) {
		return new Status(Component.translatable("screen.oldrepublic.missions.status.locked", reason), WAX);
	}

	// ---------------------------------------------------------------- ink helpers

	/** A page heading with its rule; returns the y below it. */
	private static int heading(GuiGraphicsExtractor g, Font font, Component text, int x, int y) {
		g.text(font, text, x, y + 1, HEADER, false);
		g.fill(x, y + 11, x + PAGE_W, y + 12, RULE);
		return y + 16;
	}

	/** Text wrapped to {@code width}, at most {@code maxLines} lines; returns the y below it. */
	private static int paragraph(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int width, int color, int maxLines) {
		List<FormattedCharSequence> lines = font.split(text, width);
		int shown = 0;

		for (FormattedCharSequence line : lines) {
			if (shown++ >= maxLines) {
				break;
			}

			g.text(font, line, x, y, color, false);
			y += font.lineHeight;
		}

		return y;
	}
}
