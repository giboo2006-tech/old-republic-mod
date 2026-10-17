package com.oldrepublic.mission;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.holocron.HolocronService;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.Path;
import com.oldrepublic.mission.MissionData.Active;
import com.oldrepublic.quest.Guidance;
import com.oldrepublic.quest.QuestEffects;
import com.oldrepublic.quest.story.ChapterThree;
import com.oldrepublic.quest.story.ChapterThreeStage;
import com.oldrepublic.world.outpost.Outposts;
import com.oldrepublic.world.site.SiteKind;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * The mission feature's Calling providers (spec 5.7): while the journal is held the highest request
 * wins (the story's targets are 100, A's).
 *
 * <ul>
 * <li>The active mission, 50: its site, or the officer or terminal for a turn-in ({@code CALLING_CONTRACT}).</li>
 * <li>The interrogation pull, 40: for {@code interrogatePullTicks} after Interrogate (or the freed
 * settler's lead), the nearest ruin, tomb or wreck within {@code interrogateRadius} whose pedestal
 * has not given the player a holocron ({@code CALLING_HOLOCRON} with the holocron kind).</li>
 * <li>An unvisited outpost, 20: from Chapter Three's summons, the nearest terminal of an army whose
 * outpost the player has not discovered, own army first ({@code CALLING_OUTPOST} with the army).</li>
 * </ul>
 */
public final class MissionGuidance {
	/** Ticks before a pull search that found nothing (or is still out) is tried again. */
	private static final int PULL_SEARCH_RETRY = 200;

	/** Where a player's pull points, and the holocron kind it tints the Calling with. */
	private record Pull(Vec3 target, int holocronKind) {
	}

	private static final Map<UUID, Pull> PULLS = new HashMap<>();
	/** Game time from which a new pull search may start, per player. */
	private static final Map<UUID, Long> NEXT_SEARCH = new HashMap<>();

	static void register() {
		Guidance.register(MissionGuidance::missionRequest);
		Guidance.register(MissionGuidance::interrogationRequest);
		Guidance.register(MissionGuidance::outpostRequest);
	}

	/** MISSION (50): the active mission's target, or null. */
	public static Guidance.@Nullable Request missionRequest(ServerPlayer p) {
		Active active = MissionService.get(p).active;
		Vec3 target = active == null ? null : MissionService.target(p, active);
		return target == null ? null : new Guidance.Request(Guidance.MISSION, target, QuestEffects.CALLING_CONTRACT, "mission");
	}

	/** INTERROGATION (40): the pull toward an unused chamber while it lasts, or null. */
	public static Guidance.@Nullable Request interrogationRequest(ServerPlayer p) {
		MissionData d = MissionService.get(p);
		long now = p.level().getGameTime();

		if (d.pullUntil <= now) {
			PULLS.remove(p.getUUID());
			return null;
		}

		Pull pull = PULLS.get(p.getUUID());

		if (pull == null) {
			search(p);
			return null;
		}

		return new Guidance.Request(Guidance.INTERROGATION, pull.target(), Guidance.variant(QuestEffects.CALLING_HOLOCRON, pull.holocronKind()), "interrogation");
	}

	/** OUTPOST (20): from the summons on, the nearest terminal of an army whose outpost is still undiscovered, or null. */
	public static Guidance.@Nullable Request outpostRequest(ServerPlayer p) {
		if (!ChapterThree.atLeast(p, ChapterThreeStage.SUMMONS)) {
			return null;
		}

		MissionData d = MissionService.get(p);
		int radius = MissionFeature.balance().outpostGuidanceRadius;

		for (Faction army : armies(Identity.path(p))) {
			SiteKind kind = army == Faction.REPUBLIC ? SiteKind.REPUBLIC_OUTPOST : SiteKind.IMPERIAL_OUTPOST;

			if (d.discovered(kind.id())) {
				continue;
			}

			BlockPos terminal = Outposts.nearestTerminal(p, army, radius);

			if (terminal != null) {
				return new Guidance.Request(Guidance.OUTPOST, Vec3.atCenterOf(terminal), Guidance.variant(QuestEffects.CALLING_OUTPOST, army.ordinal()), "outpost");
			}
		}

		return null;
	}

	/** The armies in the order the outpost pull tries them: a Sith's own army first, everyone else the Republic first. */
	static List<Faction> armies(Path path) {
		return path == Path.SITH ? List.of(Faction.SITH_EMPIRE, Faction.REPUBLIC) : List.of(Faction.REPUBLIC, Faction.SITH_EMPIRE);
	}

	/** Starts (or restarts) the pull: {@code interrogatePullTicks} from now, toward a chamber found by a fresh search. */
	static void startPull(ServerPlayer p) {
		MissionData d = MissionService.get(p);
		d.pullUntil = p.level().getGameTime() + MissionFeature.balance().interrogatePullTicks;
		MissionService.commit(p, d);
		PULLS.remove(p.getUUID());
		NEXT_SEARCH.remove(p.getUUID());
		search(p);
	}

	private static void search(ServerPlayer p) {
		UUID id = p.getUUID();
		long now = p.level().getGameTime();

		if (now < NEXT_SEARCH.getOrDefault(id, Long.MIN_VALUE)) {
			return;
		}

		NEXT_SEARCH.put(id, now + PULL_SEARCH_RETRY);
		MissionSites.nearest(p, MissionRules.WORLDGEN_KINDS, MissionFeature.balance().interrogateRadius, HolocronService.usedSites(p), piece -> {
			if (piece != null) {
				PULLS.put(id, new Pull(Vec3.atCenterOf(MissionSites.heart(piece)), HolocronService.senseKind(p, piece).ordinal()));
			}
		});
	}

	/** Forgets a player's pull (leave, respawn); the saved {@code pull_until} brings it back with a new search. */
	static void forget(UUID player) {
		PULLS.remove(player);
		NEXT_SEARCH.remove(player);
	}

	/** Forgets all in-memory state (server stopped). */
	static void clear() {
		PULLS.clear();
		NEXT_SEARCH.clear();
	}

	private MissionGuidance() {
	}
}
