package com.oldrepublic.quest.story;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.holocron.HolocronKind;
import com.oldrepublic.holocron.HolocronService;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.identity.Path;
import com.oldrepublic.mission.MissionFeature;
import com.oldrepublic.mission.Missions;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.Guidance;
import com.oldrepublic.quest.QuestEffects;
import com.oldrepublic.quest.QuestItems;
import com.oldrepublic.quest.QuestRules;
import com.oldrepublic.quest.QuestStage;
import com.oldrepublic.world.outpost.Outposts;
import com.oldrepublic.world.site.SiteKind;
import com.oldrepublic.world.site.SitePiece;
import com.oldrepublic.world.site.StructureLocator;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * The STORY provider of the journal's Calling (spec 5.7, priority {@link Guidance#STORY}) and the
 * journal-hold tick that sends the Calling after Chapter Two. Where it points, by stage:
 * <ul>
 * <li>SUMMONS (Independent): Courier's Fall, variant {@code FALL};</li>
 * <li>FIRST_ECHO: the first-echo target, variant {@code HOLOCRON} tinted by kind;</li>
 * <li>LEADS, level-gated: the nearest unused chamber, else the nearest outpost board (6.4);</li>
 * <li>LEADS or later: an unused extra Echo Shrine (the old-world guarantee, 4.3);</li>
 * <li>RELIQUARY: the heart, variant {@code RELIQUARY};</li>
 * <li>COMPLETE (Independent ready for the title): Courier's Fall again (5.8).</li>
 * </ul>
 * The level gate and the extra shrine step aside while a mission is active, so the mission's own
 * Calling (priority 50) is not drowned out between story beats.
 */
public final class StoryGuidance {
	/** Request source name (logs and tests). */
	public static final String SOURCE = "story";

	/** The STORY request for the player now, or null. */
	public static Guidance.@Nullable Request request(ServerPlayer p) {
		if (!StoryService.inOverworld(p)) {
			return null;
		}

		StoryData data = Story.get(p);
		StoryData.Chapter3 c = data.chapter3;
		Path path = data.storyPath();

		return switch (c.stage()) {
			case SUMMONS -> path == Path.INDEPENDENT && c.fall() != null ? story(c.fall(), QuestEffects.CALLING_FALL) : null;
			case FIRST_ECHO -> {
				FirstEcho.Target target = FirstEcho.target(p);
				yield target == null ? null : story(target.pos(), Guidance.variant(QuestEffects.CALLING_HOLOCRON, target.kind().ordinal()));
			}
			case LEADS -> leadsRequest(p, data);
			case RELIQUARY -> c.reliquary() == null ? null : story(c.reliquary(), QuestEffects.CALLING_RELIQUARY);
			case COMPLETE -> {
				if (!data.hasFlag(ChapterThreeRules.PATHFINDER_DONE) && RankThree.pathfinderCalling(p, data)) {
					yield story(c.fall(), QuestEffects.CALLING_FALL);
				}

				yield extraShrineRequest(p, data);
			}
			case CONFRONTATION, AFTERMATH -> extraShrineRequest(p, data);
			case NOT_STARTED, WAITING, LISTENING -> null;
		};
	}

	/** LEADS: the level gate's chamber or board, else an unused extra shrine; nothing while a mission runs. */
	private static Guidance.@Nullable Request leadsRequest(ServerPlayer p, StoryData data) {
		if (Missions.activeId(p) != null) {
			return null;
		}

		ChapterThreeBalance b = ChapterThreeService.balance();

		if (!ChapterThreeRules.levelGated(data.chapter3.leads().size(), Progression.get(p).level, b)) {
			return extraShrineRequest(p, data);
		}

		Path path = data.storyPath();
		HolocronKind kind = ChapterThreeRules.echoKind(path);
		BlockPos chamber = nearestChamber(p, path, kind, b);
		Faction army = nearestArmy(p, path, b);
		BlockPos board = army == null ? null : Outposts.nearestTerminal(p, army, b.firstEchoRadius);
		ChapterThreeRules.GateTarget gate = ChapterThreeRules.gatedTarget(chamber, kind, board, army == null ? 0 : army.ordinal());
		return gate == null ? null : story(gate.pos(), gate.callingVariant());
	}

	/**
	 * One of the player's Echo Shrines that has not given them a holocron yet (the old-world
	 * guarantee's shrine, or a first-echo shrine left behind), unless a mission is active.
	 */
	private static Guidance.@Nullable Request extraShrineRequest(ServerPlayer p, StoryData data) {
		if (data.chapter3.shrines().isEmpty() || Missions.activeId(p) != null) {
			return null;
		}

		BlockPos shrine = FirstEcho.unusedShrine(p);
		HolocronKind kind = ChapterThreeRules.echoKind(data.storyPath());
		return shrine == null ? null : story(shrine, Guidance.variant(QuestEffects.CALLING_HOLOCRON, kind.ordinal()));
	}

	/**
	 * The nearest pedestal of the path's kind in a site the locator has cached and the player has not
	 * used, or an unused shrine of theirs. Refreshes the locator's cache every {@code retryTicks}.
	 */
	private static @Nullable BlockPos nearestChamber(ServerPlayer p, Path path, HolocronKind kind, ChapterThreeBalance b) {
		List<SiteKind> kinds = ChapterThreeRules.echoSites(path);
		BlockPos best = FirstEcho.unusedShrine(p);

		if (ChapterThreeService.due(p, "gate_search")) {
			ChapterThreeService.retryLater(p, "gate_search", b.retryTicks);

			for (SiteKind siteKind : kinds) {
				StructureLocator.find(p, siteKind, b.firstEchoRadius, HolocronService.usedSites(p), piece -> {
				});
			}
		}

		for (SiteKind siteKind : kinds) {
			SitePiece piece = StructureLocator.cached(p, siteKind);

			if (piece == null || HolocronService.usedSites(p).test(piece)) {
				continue;
			}

			BlockPos pedestal = FirstEcho.pedestal(piece, kind);

			if (best == null || p.blockPosition().distSqr(pedestal) < p.blockPosition().distSqr(best)) {
				best = pedestal;
			}
		}

		return best;
	}

	/** The army whose board the gate points to: the mentor's side, or for an Independent the nearer outpost. */
	private static @Nullable Faction nearestArmy(ServerPlayer p, Path path, ChapterThreeBalance b) {
		return switch (path) {
			case JEDI -> Faction.REPUBLIC;
			case SITH -> Faction.SITH_EMPIRE;
			case INDEPENDENT -> {
				BlockPos republic = Outposts.nearestTerminal(p, Faction.REPUBLIC, b.firstEchoRadius);
				BlockPos empire = Outposts.nearestTerminal(p, Faction.SITH_EMPIRE, b.firstEchoRadius);

				if (republic == null) {
					yield empire == null ? null : Faction.SITH_EMPIRE;
				}

				yield empire == null || p.blockPosition().distSqr(republic) <= p.blockPosition().distSqr(empire) ? Faction.REPUBLIC : Faction.SITH_EMPIRE;
			}
			case NONE -> null;
		};
	}

	private static Guidance.Request story(BlockPos target, int callingVariant) {
		return new Guidance.Request(Guidance.STORY, Vec3.atCenterOf(target), callingVariant, SOURCE);
	}

	// ---------------------------------------------------------------- journal hold

	/**
	 * Every tick (from {@link ChapterThreeService#tick}): at {@code missions.guidanceIntervalTicks},
	 * while the journal is held in the overworld and no Chapter One or Two Calling is in play, runs
	 * the old-world guarantee and sends one Calling pulse for the best request of every provider.
	 */
	static void journalTick(ServerPlayer p) {
		int interval = Math.max(1, MissionFeature.balance().guidanceIntervalTicks);

		if (p.tickCount % interval != 0 || !StoryService.inOverworld(p) || !QuestItems.holdsJournal(p)) {
			return;
		}

		StoryData data = Story.get(p);

		if (QuestRules.stage(Progression.get(p)) == QuestStage.CALLING || earlierCalling(data)) {
			return;
		}

		FirstEcho.tickExtraShrine(p);
		Guidance.pulse(p);
	}

	/** Whether Chapter Two's own Calling (the meeting point, the smoke) runs for the player now. */
	private static boolean earlierCalling(StoryData data) {
		EncounterState state = data.encounterState();
		return (data.crossing() == CrossingStage.VOICES && data.meeting != null) || (data.encounterPos != null
				&& (state == EncounterState.PLACED || state == EncounterState.SPAWNED) && !data.captainFate().resolved());
	}

	static void init() {
		Guidance.register(StoryGuidance::request);
	}

	private StoryGuidance() {
	}
}
