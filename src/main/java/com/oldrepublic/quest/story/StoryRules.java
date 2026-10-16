package com.oldrepublic.quest.story;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.identity.Path;
import com.oldrepublic.quest.QuestRules;

/**
 * Pure rules of Chapter Two that do not need a player, kept apart so game tests can check them
 * directly. Callers commit the data afterwards. Every method that changes a stage only ever moves
 * it forward, and every reward elsewhere is guarded by the stage change or a one-shot flag.
 */
public final class StoryRules {
	/** Flags in {@link StoryData#flags}. */
	public static final String LESSON_ONE_BEGUN = "lesson_one_begun";
	public static final String LESSON_TWO_BEGUN = "lesson_two_begun";
	public static final String LATE_JOINED = "late_joined";
	public static final String RECKONING_DONE = "reckoning_done";
	public static final String DEBRIEF_HEARD = "debrief_heard";
	public static final String TRIAL_WON = "trial_won";
	public static final String BRENN_SPARED = "brenn_spared";
	public static final String BRENN_KILLED = "brenn_killed";
	public static final String LEDGER_DELIVERED = "ledger_delivered";
	public static final String LEDGER_READ = "ledger_found";
	/** Waking from a long sleep made the Two Voices dream due (kept, so a relog replays it). */
	public static final String VOICES_DUE = "voices_due";
	/** The chosen mentor's camp is waiting for the player to step off its ground before it is placed. */
	public static final String CAMP_PENDING = "camp_pending";

	/** Counters in {@link StoryData#counters}. */
	public static final String PULL_TARGETS = "lesson_pull_targets";
	public static final String PULL_KILLS = "lesson_pull_kills";
	/** Hostile crew members spawned for Smoke on the Ridge, and how many of them are down. */
	public static final String CREW_TOTAL = "crew_total";
	public static final String CREW_DOWN = "crew_down";

	/** Trust ids of the two mentors. */
	public static final String JEDI = "jedi";
	public static final String SITH = "sith";

	/** Band edge below (Jedi) or above (Sith) which a path player gets no new lessons. */
	public static final int OPPOSED_BAND = 15;
	/** The Jedi does not accept a student at or below -50; the Sith not one at or above +50. */
	public static final int REFUSAL_BAND = 50;

	/**
	 * Moves the Crossing to {@code to} if it is at least {@code minFrom} and before {@code to}.
	 *
	 * @return whether the stage changed
	 */
	public static boolean advanceCrossing(StoryData data, CrossingStage to, CrossingStage minFrom) {
		if (data.crossingStage < minFrom.ordinal() || data.crossingStage >= to.ordinal()) {
			return false;
		}

		data.crossingStage = to.ordinal();
		return true;
	}

	/**
	 * Moves the path stage to {@code to} if it is at least {@code minFrom} and before {@code to}.
	 *
	 * @return whether the stage changed
	 */
	public static boolean advancePath(StoryData data, PathStage to, PathStage minFrom) {
		if (data.pathStage < minFrom.ordinal() || data.pathStage >= to.ordinal()) {
			return false;
		}

		data.pathStage = to.ordinal();
		return true;
	}

	/**
	 * Applies the Crossing choice. It is accepted once, at the meeting: Jedi and Sith start their
	 * first lesson, Independent waits for Smoke on the Ridge.
	 *
	 * @return whether the choice was applied (false on a repeat or outside the meeting)
	 */
	public static boolean choose(StoryData data, Path path, long gameTime) {
		if (path == Path.NONE || data.crossing() != CrossingStage.MEETING || data.storyPath() != Path.NONE) {
			return false;
		}

		data.crossingStage = CrossingStage.DECIDED.ordinal();
		data.path = path.id();
		data.decidedAt = gameTime;
		data.pathStage = path == Path.INDEPENDENT ? PathStage.NONE.ordinal() : PathStage.LESSON_ONE.ordinal();
		return true;
	}

	/**
	 * An Independent joins a mentor later (once). The path starts at its first lesson; Smoke on the
	 * Ridge is never repeated, so a finished mission is skipped later on.
	 *
	 * @return whether the join was applied
	 */
	public static boolean lateJoin(StoryData data, Path target) {
		if ((target != Path.JEDI && target != Path.SITH) || data.storyPath() != Path.INDEPENDENT || !data.flags.add(LATE_JOINED)) {
			return false;
		}

		data.path = target.id();
		data.pathStage = PathStage.LESSON_ONE.ordinal();
		return true;
	}

	/** The mentor id ({@code jedi} or {@code sith}) of a path, or null for none and Independent. */
	public static @Nullable String mentorOf(Path path) {
		return switch (path) {
			case JEDI -> JEDI;
			case SITH -> SITH;
			default -> null;
		};
	}

	/**
	 * Why {@code target}'s mentor would not take this player on now, or null if they would.
	 *
	 * @param reputation the player's reputation with the mentor's faction
	 */
	public static @Nullable String joinBlocker(StoryData data, Path target, int alignment, int reputation) {
		Path current = data.storyPath();
		boolean atCrossing = data.crossing() == CrossingStage.MEETING && current == Path.NONE;
		boolean late = current == Path.INDEPENDENT && !data.hasFlag(LATE_JOINED);

		if (!atCrossing && !late) {
			return "message.oldrepublic.story.blocked.chosen";
		}

		if (target == Path.JEDI && alignment <= -REFUSAL_BAND) {
			return "message.oldrepublic.story.blocked.jedi_refuses";
		}

		if (target == Path.SITH && alignment >= REFUSAL_BAND) {
			return "message.oldrepublic.story.blocked.sith_refuses";
		}

		if (late && reputation < -99) {
			return "message.oldrepublic.story.blocked.reputation";
		}

		return null;
	}

	/** Whether alignment sits in the band opposed to {@code path} (no new lessons there). */
	public static boolean opposedBand(Path path, int alignment) {
		return (path == Path.JEDI && alignment <= -OPPOSED_BAND) || (path == Path.SITH && alignment >= OPPOSED_BAND);
	}

	/** Whether alignment alone keeps the rank trial closed (Jedi needs at least 0, Sith at most 0). */
	public static boolean alignmentBlocksTrial(Path path, int alignment) {
		return (path == Path.JEDI && alignment < 0) || (path == Path.SITH && alignment > 0);
	}

	/** Why a new lesson cannot begin, or null if it can. */
	public static @Nullable String lessonBlocker(Path path, int trust, int neededTrust, int alignment) {
		if (opposedBand(path, alignment)) {
			return path == Path.JEDI ? "message.oldrepublic.story.blocked.dark_clouds" : "message.oldrepublic.story.blocked.too_soft";
		}

		return trust < neededTrust ? "message.oldrepublic.story.blocked.trust" : null;
	}

	/** What the trial needs, in the order the dialogue names a failure. */
	public record TrialRequirements(int minLevel, int minTrust, int minReputation) {
	}

	/** Why the rank trial cannot begin now, or null if it can. */
	public static @Nullable String trialBlocker(Path path, TrialRequirements req, int level, int trust, int reputation, int alignment,
			long gameTime, long retryAt) {
		if (level < req.minLevel()) {
			return "message.oldrepublic.story.blocked.level";
		}

		if (trust < req.minTrust()) {
			return "message.oldrepublic.story.blocked.trial_trust";
		}

		if (reputation < req.minReputation()) {
			return path == Path.SITH ? "message.oldrepublic.story.blocked.rep_sith" : "message.oldrepublic.story.blocked.rep_jedi";
		}

		if (alignmentBlocksTrial(path, alignment)) {
			return path == Path.JEDI ? "message.oldrepublic.story.blocked.dark_clouds" : "message.oldrepublic.story.blocked.too_soft";
		}

		return gameTime < retryAt ? "message.oldrepublic.story.blocked.retry" : null;
	}

	/**
	 * The trust the next step of the path needs: lesson two, then the trial. Zero where nothing
	 * waits on trust.
	 */
	public static int trustNeeded(PathStage stage, int lessonTwoTrust, int trialTrust) {
		return switch (stage) {
			case LESSON_TWO -> lessonTwoTrust;
			case DEBRIEF, TRIAL -> trialTrust;
			default -> 0;
		};
	}

	/**
	 * Whether the daily session with the mentor is open today: once the path is complete, and also
	 * earlier while trust is short of the next step, so a player can always earn it back.
	 */
	public static boolean sessionAvailable(StoryData data, long gameTime, int trust, int neededTrust) {
		if (mentorOf(data.storyPath()) == null || data.sessionDay == QuestRules.dayIndex(gameTime)) {
			return false;
		}

		return data.stage() == PathStage.COMPLETE || trust < neededTrust;
	}

	/**
	 * Whether Atone (Jedi) or Embrace (Sith) is offered: only while alignment blocks the trial or
	 * keeps new lessons away, once per game day.
	 */
	public static boolean atoneAvailable(StoryData data, int alignment, long gameTime) {
		Path path = data.storyPath();

		if (mentorOf(path) == null || data.atoneDay == QuestRules.dayIndex(gameTime)) {
			return false;
		}

		return opposedBand(path, alignment) || alignmentBlocksTrial(path, alignment);
	}

	/**
	 * Smoke on the Ridge is complete once the crew is down, the Captain's fate is settled and the
	 * captives' fate is settled (abandoning them counts).
	 */
	public static boolean reckoningComplete(StoryData data, boolean crewDefeated) {
		return crewDefeated && data.captainFate().resolved() && data.captivesFate() != CaptivesFate.NONE;
	}

	/** Whether every hostile crew member is down (from the death counters). */
	public static boolean crewDown(StoryData data) {
		return data.counter(CREW_DOWN) >= data.counter(CREW_TOTAL);
	}

	/**
	 * Walking away from still-bound captives counts as leaving them: once the crew is down and the
	 * Captain has yielded or met her fate, and the owner is farther than {@code abandonDistance}.
	 */
	public static boolean captivesAbandoned(StoryData data, boolean crewDown, double distance, double abandonDistance) {
		return crewDown && data.captainFate() != CaptainFate.NONE && data.captivesFate() == CaptivesFate.NONE && distance > abandonDistance;
	}

	/**
	 * The stage after Smoke on the Ridge: Independents finish the chapter, path players report to
	 * their mentor. A late joiner who already did the mission goes straight to the debrief.
	 */
	public static PathStage afterReckoning(Path path) {
		return path == Path.INDEPENDENT ? PathStage.COMPLETE : PathStage.DEBRIEF;
	}

	/** Lesson two hands over to Smoke on the Ridge, or to the debrief if the mission is already done. */
	public static PathStage afterLessonTwo(StoryData data) {
		return data.hasFlag(RECKONING_DONE) ? PathStage.DEBRIEF : PathStage.RECKONING;
	}

	/**
	 * Lang key of the current Chapter Two objective (the journal's left page and the Standing tab),
	 * or null when there is none to show.
	 */
	public static @Nullable String objectiveKey(StoryData data) {
		String base = "quest.oldrepublic.c2.objective.";

		switch (data.crossing()) {
			case NOT_STARTED, WAITING -> {
				return null;
			}
			case VOICES -> {
				return base + "voices";
			}
			case MEETING -> {
				return base + "meeting";
			}
			case DECIDED -> {
			}
		}

		Path path = data.storyPath();
		PathStage stage = data.stage();

		if (path == Path.INDEPENDENT && stage == PathStage.NONE) {
			return base + "independent.waiting";
		}

		String key = base + path.id() + "." + stage.name().toLowerCase(Locale.ROOT);

		if ((stage == PathStage.LESSON_ONE && data.hasFlag(LESSON_ONE_BEGUN))) {
			return key + "_active";
		}

		return key;
	}

	private StoryRules() {
	}
}
