package com.oldrepublic.quest;

import com.oldrepublic.progression.ForceData;

/**
 * Pure rules of the questline that do not need a player, kept separate so game tests can check
 * them directly. Callers commit the data afterwards.
 */
public final class QuestRules {
	/** Counter holding the game-day index + 1 of the last rewarded training meditation. */
	public static final String MEDITATION_DAY = "meditation_day";
	public static final String PUSH_PRACTICE = "push_practice";
	public static final String POACHER_KILLS = "quest_poacher_kills";
	/** Increments with every newly unlocked journal or codex entry. */
	public static final String JOURNAL_SEQ = "journal_seq";
	public static final long TICKS_PER_DAY = 24000L;

	public static QuestStage stage(ForceData data) {
		return QuestStage.of(data.questStage);
	}

	/**
	 * Moves the quest to {@code to} if the current stage is at least {@code minFrom} and before
	 * {@code to}. Stages never move backwards here.
	 *
	 * @return whether the stage changed
	 */
	public static boolean advance(ForceData data, QuestStage to, QuestStage minFrom) {
		int current = data.questStage;

		if (current < minFrom.ordinal() || current >= to.ordinal()) {
			return false;
		}

		data.questStage = to.ordinal();
		return true;
	}

	/** Whether a new Awakening dream may begin. */
	public static boolean awakeningCanStart(ForceData data, boolean dreamPending) {
		return !data.isAwakened() && !dreamPending;
	}

	/**
	 * Adds hostile push hits to the practice counter (Practice stage only), clamped to
	 * {@code needed}.
	 *
	 * @return the new count, or -1 if the hits did not count
	 */
	public static int addPractice(ForceData data, int hostileHits, int needed) {
		if (stage(data) != QuestStage.PRACTICE || hostileHits <= 0) {
			return -1;
		}

		int count = Math.min(needed, data.counter(PUSH_PRACTICE) + hostileHits);
		data.counters.put(PUSH_PRACTICE, count);
		return count;
	}

	public static int dayIndex(long gameTime) {
		return (int) Math.min(Integer.MAX_VALUE - 1, Math.max(0L, gameTime / TICKS_PER_DAY));
	}

	/**
	 * Whether a meditation may start now. The first one (Focus stage) always may; afterwards
	 * meditation is a training activity rewarded at most once per game day.
	 */
	public static boolean meditationAvailable(ForceData data, long gameTime) {
		QuestStage stage = stage(data);

		if (stage == QuestStage.STIRRING) {
			return false;
		}

		return stage == QuestStage.FOCUS || data.counter(MEDITATION_DAY) != dayIndex(gameTime) + 1;
	}

	public static void markMeditated(ForceData data, long gameTime) {
		data.counters.put(MEDITATION_DAY, dayIndex(gameTime) + 1);
	}

	private QuestRules() {
	}
}
