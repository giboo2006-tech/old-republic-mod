package com.oldrepublic.quest.turning;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.identity.Faction;
import com.oldrepublic.identity.Path;
import com.oldrepublic.quest.story.ChapterFive;
import com.oldrepublic.quest.story.ChapterFiveStage;
import com.oldrepublic.quest.story.StoryData;
import com.oldrepublic.quest.story.TurningKind;
import com.oldrepublic.quest.story.TurningStage;
import com.oldrepublic.skill.SkillIds;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * The pure rules of the Fall, the Return and rank 4 (spec MILESTONE_5.md 8): who may be offered a turning and when, what
 * a refusal does, how the steps' progress is packed into {@code StoryData.Turning.task}, what turning costs, and the rank
 * 4 requirements. Nothing here touches a player or a level, so the game tests call it with explicit days.
 *
 * <p>Every day here is a game-day index ({@code Clocks.day}: overworld game time / 24000), as are the record's
 * {@code band_since}, {@code reoffer_day} and {@code turned_day}.
 */
public final class TurningRules {
	/** Reputation source ids of the rite's two changes (8.4), not one-shot: each rite applies its net change once. */
	public static final String FALL_SOURCE = "turn.fall";
	public static final String RETURN_SOURCE = "turn.return";
	/** Story flags: Saelen's crystal was taken in a Fall, Sorn's blade broken in a Return (8.3). */
	public static final String CRYSTAL_TAKEN = "turn.crystal_taken";
	public static final String BLADE_BROKEN = "turn.blade_broken";
	/** Identity choices of rank 4 (8.5, 14.1): held, paid on an earlier path, and the Darth name. */
	public static final String RANK4_DONE = "rank4.done";
	public static final String RANK4_PAID = "rank4.paid";
	public static final String DARTH_PREFIX = "rank4.darth.";
	/** The six Darth names ({@code rank4.oldrepublic.darth_name.1} to {@code .6}). */
	public static final int DARTH_NAMES = 6;
	/** Story counter prefix of the robes' daily reissue: the day index the robe was last given. */
	public static final String REISSUE_PREFIX = "rank4.reissue.";
	/** The rank 4 powers (W6's nodes): Valor for a Master, Rage for a Darth. */
	public static final String FORCE_VALOR = "force_valor";
	public static final String FORCE_RAGE = "force_rage";

	/** The confrontation's progress ({@code task} while at CONFRONTATION). */
	public static final int CONFRONT_WAITING = 0;
	public static final int CONFRONT_DUEL = 1;
	public static final int CONFRONT_YIELDED = 2;
	/** Peaceful: the confrontation was a talk and the mentor yielded at once (8.3). */
	public static final int CONFRONT_TALKED = 3;
	/** The rite's progress ({@code task} while at RITE). */
	public static final int RITE_WAITING = 0;
	public static final int RITE_HERE = 1;
	/** The Fall's task: on the way to Ansa, then carrying the holocron. */
	public static final int FALL_TO_ANSA = 0;
	public static final int FALL_CARRYING = 1;
	/** The Return's task phases (the low four bits of {@code task}); the next four bits are the guards down. */
	public static final int RETURN_HELD = 0;
	public static final int RETURN_ESCORT = 1;
	public static final int RETURN_ABOARD = 2;
	/** The detention post's four guards: two security droids (bits 0 and 1) and two troopers (bits 2 and 3). */
	public static final int GUARDS = 4;
	public static final int ALL_GUARDS = (1 << GUARDS) - 1;
	/** The three prisoners; the task's bits 8 to 10 mark those already aboard the ship. */
	public static final int PRISONERS = 3;
	public static final int ALL_PRISONERS = (1 << PRISONERS) - 1;
	private static final int PHASE_MASK = 0xF;
	private static final int GUARD_SHIFT = 4;
	private static final int BOARDED_SHIFT = 8;
	/** A ship standing this close (horizontally) to a Greenwatch pad bay is on it. */
	public static final double BAY_REACH = 8.0;
	/** With no bays known, this close to the hub's centre counts as the Greenwatch. */
	public static final double HUB_REACH = 48.0;

	// ---------------------------------------------------------------- the offer (8.2)

	/**
	 * The turning a player could be offered now: the Fall for a Jedi, the Return for a Sith, both after Chapter Four, each
	 * once per character; NONE for an Independent, a player without a path, or one whose identity and story paths disagree
	 * (an operator's forced change still under way).
	 */
	public static TurningKind candidate(Path identity, Path story, boolean chapterFourComplete, boolean fallDone, boolean returnDone) {
		if (!chapterFourComplete || identity != story) {
			return TurningKind.NONE;
		}

		return switch (identity) {
			case JEDI -> fallDone ? TurningKind.NONE : TurningKind.FALL;
			case SITH -> returnDone ? TurningKind.NONE : TurningKind.RETURN;
			default -> TurningKind.NONE;
		};
	}

	/** Whether {@code alignment} sits in {@code kind}'s band: at or below -50 for the Fall, at or above +50 for the Return. */
	public static boolean inBand(TurningKind kind, int alignment, TurningBalance b) {
		return switch (kind) {
			case FALL -> alignment <= b.fallAlignment;
			case RETURN -> alignment >= b.returnAlignment;
			default -> false;
		};
	}

	/** Whether the record is between questlines: never offered, or the last one finished. */
	public static boolean idle(StoryData.Turning t) {
		return t.stage() == TurningStage.NONE || t.stage() == TurningStage.COMPLETE;
	}

	/**
	 * One slow check of the band on day {@code day}. While a questline is under way nothing changes. Out of the band (or
	 * with no candidate) the band's day, the refusals and the re-offer day are forgotten and a pending offer is withdrawn,
	 * back to {@code resting}; in the band the first day seen is kept.
	 */
	public static StoryData.Turning track(StoryData.Turning t, TurningKind candidate, boolean inBand, long day, StoryData.Turning resting) {
		boolean busy = !idle(t) && t.stage() != TurningStage.OFFERED;

		if (busy) {
			return t;
		}

		if (candidate == TurningKind.NONE || !inBand) {
			StoryData.Turning out = t.stage() == TurningStage.OFFERED ? resting.withOffer(-1L, 0, -1L) : t.withOffer(-1L, 0, -1L);
			return out.equals(t) ? t : out;
		}

		return t.bandSince() < 0 ? t.withOffer(day, t.refusals(), t.reofferDay()) : t;
	}

	/**
	 * Whether the offer is due on day {@code day}: between questlines, in the band for {@code bandDays} whole days, fewer
	 * than {@code refusals} refusals, and past any re-offer day.
	 */
	public static boolean offerDue(StoryData.Turning t, long day, TurningBalance b) {
		return idle(t) && t.bandSince() >= 0 && day - t.bandSince() >= b.bandDays && t.refusals() < b.refusals
				&& (t.reofferDay() < 0 || day >= t.reofferDay());
	}

	/** The record after the offer is made: OFFERED for {@code kind}, the task cleared. */
	public static StoryData.Turning offered(StoryData.Turning t, TurningKind kind) {
		return t.withStage(kind, TurningStage.OFFERED).withTask(0);
	}

	/**
	 * The record after a refusal on day {@code day}: one more refusal, the offer back after {@code reofferDays}, and the
	 * stage back to {@code resting}'s. The band's day is kept, so the offer returns while the band holds.
	 */
	public static StoryData.Turning decline(StoryData.Turning t, long day, TurningBalance b, StoryData.Turning resting) {
		return resting.withOffer(t.bandSince(), t.refusals() + 1, day + b.reofferDays);
	}

	/** Whether this refusal was the last one before the offers stop until the band is left (8.2). */
	public static boolean finalRefusal(StoryData.Turning t, TurningBalance b) {
		return t.refusals() >= b.refusals;
	}

	/**
	 * Where the record rests between questlines: the last finished one (COMPLETE), or NONE when neither turning was ever
	 * finished. The turned day and the band are not touched.
	 */
	public static StoryData.Turning resting(StoryData.Turning t, boolean fallDone, boolean returnDone) {
		if (t.stage() == TurningStage.COMPLETE) {
			return t;
		}

		if (fallDone && returnDone) {
			return t.withStage(t.kind() == TurningKind.RETURN ? TurningKind.RETURN : TurningKind.FALL, TurningStage.COMPLETE).withTask(0);
		}

		if (fallDone || returnDone) {
			return t.withStage(fallDone ? TurningKind.FALL : TurningKind.RETURN, TurningStage.COMPLETE).withTask(0);
		}

		return t.withStage(TurningKind.NONE, TurningStage.NONE).withTask(0);
	}

	// ---------------------------------------------------------------- the steps (8.3)

	/** The path a turning leaves and the one it leads to. */
	public static Path from(TurningKind kind) {
		return kind == TurningKind.RETURN ? Path.SITH : Path.JEDI;
	}

	public static Path to(TurningKind kind) {
		return kind == TurningKind.RETURN ? Path.JEDI : Path.SITH;
	}

	/** The Return's task phase ({@link #RETURN_HELD}, {@link #RETURN_ESCORT} or {@link #RETURN_ABOARD}). */
	public static int phase(int task) {
		return task & PHASE_MASK;
	}

	/** The detention post's guards that are down, a bit per guard. */
	public static int guardsDown(int task) {
		return (task >>> GUARD_SHIFT) & ALL_GUARDS;
	}

	/** The prisoners already aboard, a bit per prisoner. */
	public static int boarded(int task) {
		return (task >>> BOARDED_SHIFT) & ALL_PRISONERS;
	}

	/** The Return's task value for a phase and the guards down, with nobody aboard yet. */
	public static int returnTask(int phase, int guardsDown) {
		return returnTask(phase, guardsDown, 0);
	}

	/** The Return's task value for a phase, the guards down and the prisoners aboard. */
	public static int returnTask(int phase, int guardsDown, int boarded) {
		return (phase & PHASE_MASK) | ((guardsDown & ALL_GUARDS) << GUARD_SHIFT) | ((boarded & ALL_PRISONERS) << BOARDED_SHIFT);
	}

	/** Whether every guard of the post is down (the cells may be opened). */
	public static boolean allGuardsDown(int task) {
		return guardsDown(task) == ALL_GUARDS;
	}

	/**
	 * Whether a ship standing at {@code ship} is on a Greenwatch pad bay: within {@link #BAY_REACH} blocks (horizontally)
	 * of one of {@code bays}; with no bays known, within {@link #HUB_REACH} of {@code hub}; with no hub either, anywhere
	 * (a world whose hub could not be placed still lets the prisoners off).
	 */
	public static boolean onGreenwatchBay(Vec3 ship, List<BlockPos> bays, @Nullable BlockPos hub) {
		if (!bays.isEmpty()) {
			for (BlockPos bay : bays) {
				if (flatDistanceSqr(ship, bay) <= BAY_REACH * BAY_REACH) {
					return true;
				}
			}

			return false;
		}

		return hub == null || flatDistanceSqr(ship, hub) <= HUB_REACH * HUB_REACH;
	}

	private static double flatDistanceSqr(Vec3 a, BlockPos b) {
		double dx = a.x - (b.getX() + 0.5);
		double dz = a.z - (b.getZ() + 0.5);
		return dx * dx + dz * dz;
	}

	// ---------------------------------------------------------------- what turning costs (8.4)

	/** The rank after a rite: {@code min(rank, rankCap)}. */
	public static int rankAfter(int rank, TurningBalance b) {
		return Math.min(rank, b.rankCap);
	}

	/** The skill points given back for the old tree's bought nodes: half of all spent on them, rounded down. */
	public static int refund(int spent, TurningBalance b) {
		return Math.max(0, spent) * Math.max(0, b.refundPct) / 100;
	}

	/** Where the old path's order ends: Hostile, {@code min(before, -500)}. */
	public static int oldFactionEnd(int before, TurningBalance b) {
		return Math.min(before, b.oldFactionCap);
	}

	/** Where the new path's order ends: {@code max(before, 0) + 150}. */
	public static int newFactionEnd(int before, TurningBalance b) {
		return Math.max(before, 0) + b.newFactionBonus;
	}

	/** A path's two signature powers: Heal and Barrier, or Lightning and Choke (8.4). */
	public static List<String> signatures(Path path) {
		return switch (path) {
			case JEDI -> List.of("force_heal", "force_barrier");
			case SITH -> List.of("force_lightning", "force_choke");
			default -> List.of();
		};
	}

	/** A path's rank grants, removed by a rite: the rank 3 node and the rank 4 power. */
	public static List<String> rankGrants(Path path) {
		return switch (path) {
			case JEDI -> List.of(SkillIds.KNIGHTS_VIGIL, FORCE_VALOR);
			case SITH -> List.of(SkillIds.LORDS_DOMINION, FORCE_RAGE);
			default -> List.of();
		};
	}

	/** The skill tree of a path ({@code jedi} or {@code sith}), or null. */
	public static @Nullable String treeOf(Path path) {
		return switch (path) {
			case JEDI -> SkillIds.TREE_JEDI;
			case SITH -> SkillIds.TREE_SITH;
			default -> null;
		};
	}

	/** The faction of a path's order, or null. */
	public static @Nullable Faction factionOf(Path path) {
		return switch (path) {
			case JEDI -> Faction.JEDI_ORDER;
			case SITH -> Faction.SITH_EMPIRE;
			default -> null;
		};
	}

	// ---------------------------------------------------------------- rank 4 (8.5)

	/** The rank 4 identity choice of Darth name {@code n} (1 to 6). */
	public static String darthChoice(int n) {
		return DARTH_PREFIX + n;
	}

	/** The Darth name chosen ({@code 1} to {@code 6}) among the identity choices, or 0. */
	public static int darthName(Set<String> choices) {
		for (int n = 1; n <= DARTH_NAMES; n++) {
			if (choices.contains(darthChoice(n))) {
				return n;
			}
		}

		return 0;
	}

	/** Whether a robe last given on {@code lastDay} (-1 never) may be given again on {@code day}. */
	public static boolean reissueDue(int lastDay, long day) {
		return lastDay < 0 || day > lastDay;
	}

	/**
	 * What the rank 4 checks read about a player, gathered on the server.
	 *
	 * @param path the current path (story and identity agree)
	 * @param stage Chapter Five's stage
	 * @param level the Force level
	 * @param rank the rank on the current path
	 * @param pathfinder whether an Independent holds the Pathfinder title
	 * @param trust trust with the path's mentor
	 * @param ownRep reputation with the path's order
	 * @param trustedFactions how many known factions stand at {@code wayfarerTier} or better
	 * @param alignment the alignment
	 * @param masteredPowers how many powers reached {@code rank4MasteryRank}
	 * @param deeds Chapter Five's deeds
	 */
	public record Standing(Path path, ChapterFiveStage stage, int level, int rank, boolean pathfinder, int trust, int ownRep, int trustedFactions,
			int alignment, int masteredPowers, ChapterFive.Deeds deeds) {
	}

	/**
	 * One requirement as the checklist shows it.
	 *
	 * @param id the requirement ({@code story}, {@code level}, {@code rank}, {@code title}, {@code trust}, {@code reputation},
	 *     {@code factions}, {@code alignment}, {@code mastery} or {@code deeds}); lang
	 *     {@code turning.oldrepublic.rank4.req.<id>} takes the need and what the player has
	 * @param met whether it holds
	 * @param have the player's value
	 * @param need the value asked for
	 */
	public record Requirement(String id, boolean met, int have, int need) {
		/** The lang key of the checklist line and the dialogue's fail reason. */
		public String key() {
			return "turning.oldrepublic.rank4.req." + id;
		}

		/** The dialogue fail reason: the key with the need and what the player has ({@code key|need|have}). */
		public String failReason() {
			return key() + "|" + need + "|" + have;
		}
	}

	/** How many of the rank 4 deeds count: the hymn, the echoes, the bounties, the contracts and (a Sith's) the writ. */
	public static int deeds(ChapterFive.Deeds d, Path path, TurningBalance b) {
		int count = 0;
		count += d.hymnCleared() ? 1 : 0;
		count += d.echoes() >= b.deedEchoes ? 1 : 0;
		count += d.bounties() >= b.deedBounties ? 1 : 0;
		count += d.contracts() >= b.deedContracts ? 1 : 0;
		count += path == Path.SITH && d.writ() ? 1 : 0;
		return count;
	}

	/**
	 * Every requirement of rank 4 for the player's path, in the order the dialogue names a failure (8.5): Master or Darth
	 * for a Jedi or a Sith, the Wayfarer title for an Independent; empty for no path.
	 */
	public static List<Requirement> rankFour(Standing s, TurningBalance b) {
		List<Requirement> out = new ArrayList<>();
		boolean story = s.stage() == ChapterFiveStage.COMPLETE;
		int deeds = deeds(s.deeds(), s.path(), b);

		switch (s.path()) {
			case JEDI, SITH -> {
				int lean = s.path() == Path.JEDI ? s.alignment() : -s.alignment();
				out.add(new Requirement("story", story, story ? 1 : 0, 1));
				out.add(new Requirement("level", s.level() >= b.rank4Level, s.level(), b.rank4Level));
				out.add(new Requirement("rank", s.rank() >= 3, s.rank(), 3));
				out.add(new Requirement("trust", s.trust() >= b.rank4Trust, s.trust(), b.rank4Trust));
				out.add(new Requirement("reputation", s.ownRep() >= b.rank4Rep, s.ownRep(), b.rank4Rep));
				out.add(new Requirement("alignment", lean >= b.rank4Alignment, lean, b.rank4Alignment));
				out.add(new Requirement("mastery", s.masteredPowers() >= b.rank4MasteryPowers, s.masteredPowers(), b.rank4MasteryPowers));
				out.add(new Requirement("deeds", deeds >= b.rank4Deeds, deeds, b.rank4Deeds));
			}
			case INDEPENDENT -> {
				out.add(new Requirement("story", story, story ? 1 : 0, 1));
				out.add(new Requirement("level", s.level() >= b.rank4Level, s.level(), b.rank4Level));
				out.add(new Requirement("title", s.pathfinder(), s.pathfinder() ? 1 : 0, 1));
				out.add(new Requirement("factions", s.trustedFactions() >= b.wayfarerFactions, s.trustedFactions(), b.wayfarerFactions));
				out.add(new Requirement("mastery", s.masteredPowers() >= b.rank4MasteryPowers, s.masteredPowers(), b.rank4MasteryPowers));
				out.add(new Requirement("deeds", deeds >= b.rank4Deeds, deeds, b.rank4Deeds));
			}
			default -> {
			}
		}

		return out;
	}

	/** The first unmet requirement's fail reason, null when every one is met, or {@code no_path} for a player without one. */
	public static @Nullable String rankFourBlocker(Standing s, TurningBalance b) {
		List<Requirement> requirements = rankFour(s, b);

		if (requirements.isEmpty()) {
			return "turning.oldrepublic.rank4.req.no_path";
		}

		for (Requirement requirement : requirements) {
			if (!requirement.met()) {
				return requirement.failReason();
			}
		}

		return null;
	}

	/** Whether the rank 4 checklist shows: from Chapter Five's AFTERMATH, for a path that has a rank 4. */
	public static boolean checklistVisible(Path path, ChapterFiveStage stage) {
		return (path == Path.JEDI || path == Path.SITH || path == Path.INDEPENDENT) && stage.ordinal() >= ChapterFiveStage.AFTERMATH.ordinal();
	}

	private TurningRules() {
	}
}
