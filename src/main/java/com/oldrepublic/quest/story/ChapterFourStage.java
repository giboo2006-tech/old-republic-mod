package com.oldrepublic.quest.story;

/**
 * Progress through Chapter Four, "Stars to Steer By" (spec MILESTONE_4.md 8.1). Stages only move
 * forward. Stored by ordinal in {@link StoryData.Chapter4}, so only append.
 */
public enum ChapterFourStage {
	NOT_STARTED,
	/** Chapter Three is done; the seeker droid comes at a safe moment. */
	MARKED,
	/** The droid has scanned the player: hear the briefing. */
	SEEKER,
	/** Make the ship fly: the four parts and a fuel cell. */
	REPAIRS,
	/** Fly to the path world. */
	FIRST_FLIGHT,
	/** The world arc: two missions, the arc's power and an expedition. */
	WORLD_ARC,
	/** Find out who hired the hunter (three clues). */
	HUNTED,
	/** Answer the hunter's challenge. */
	CHALLENGED,
	/** Face Torvin Kaast. */
	DUEL,
	/** Report to the mentor. */
	AFTERMATH,
	/** The chapter is done; the ongoing loop continues. */
	COMPLETE;

	/** The stage at this ordinal, clamped into range (the saved form). */
	public static ChapterFourStage of(int ordinal) {
		ChapterFourStage[] values = values();
		return values[Math.max(0, Math.min(values.length - 1, ordinal))];
	}
}
