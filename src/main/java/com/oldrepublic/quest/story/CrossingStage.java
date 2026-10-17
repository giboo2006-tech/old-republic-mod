package com.oldrepublic.quest.story;

/**
 * Stages of Chapter Two's opening, "Two Voices" (spec section 5.1). Stored by ordinal in
 * {@link StoryData#crossingStage}, so only append new stages.
 */
public enum CrossingStage {
	/** Chapter One is not complete yet. */
	NOT_STARTED,
	/** Chapter One is complete; the Two Voices dream comes after a day or the next long sleep. */
	WAITING,
	/** The dream has played; the journal pulls toward the meeting point. */
	VOICES,
	/** Arrived: the Jedi and the Sith wait for an answer. */
	MEETING,
	/** A path was chosen (or both were refused). */
	DECIDED;

	public static CrossingStage of(int ordinal) {
		CrossingStage[] values = values();
		return values[Math.max(0, Math.min(values.length - 1, ordinal))];
	}
}
