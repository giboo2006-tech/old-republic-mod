package com.oldrepublic.quest.story;

/**
 * Progress along the chosen path (spec section 5.2), one value shared by every path. Independents
 * go {@code NONE -> RECKONING -> COMPLETE}. Stored by ordinal in {@link StoryData#pathStage}, so
 * only append new stages.
 */
public enum PathStage {
	NONE,
	/** "The Reach" / "What Is Owed": learn and use Force Pull. */
	LESSON_ONE,
	/** "The Still Blade" / "Relentless": a sparring lesson that teaches a form. */
	LESSON_TWO,
	/** "Smoke on the Ridge". */
	RECKONING,
	/** Report back to the mentor. */
	DEBRIEF,
	/** The rank trial is offered once its requirements are met. */
	TRIAL,
	/** The chapter is complete; daily sessions continue. */
	COMPLETE;

	public static PathStage of(int ordinal) {
		PathStage[] values = values();
		return values[Math.max(0, Math.min(values.length - 1, ordinal))];
	}
}
