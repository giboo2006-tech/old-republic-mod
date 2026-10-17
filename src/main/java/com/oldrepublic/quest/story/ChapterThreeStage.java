package com.oldrepublic.quest.story;

/**
 * Progress through Chapter Three, "The Ones That Sing" (spec section 6.1). Stages only move
 * forward. Stored by ordinal in {@link StoryData.Chapter3}, so only append.
 */
public enum ChapterThreeStage {
	NOT_STARTED,
	/** Chapter Two is done; the dream comes after a day or a night's sleep. */
	WAITING,
	/** The dream has ended: speak with the mentor, or follow the falling light. */
	SUMMONS,
	/** Find what the Force hides: the three cairns or caches. */
	LISTENING,
	/** Find the ruin, tomb or pilgrim's road the Force points to. */
	FIRST_ECHO,
	/** Follow the Collector's trail. */
	LEADS,
	/** Find the Reliquary. */
	RELIQUARY,
	/** Face the Collector. */
	CONFRONTATION,
	/** Decide the Archive's fate, then report. */
	AFTERMATH,
	/** The chapter is done; the ongoing loop continues. */
	COMPLETE;

	/** The stage at this ordinal, clamped into range (the saved form). */
	public static ChapterThreeStage of(int ordinal) {
		ChapterThreeStage[] values = values();
		return values[Math.max(0, Math.min(values.length - 1, ordinal))];
	}
}
