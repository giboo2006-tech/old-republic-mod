package com.oldrepublic.quest.story;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

/**
 * Progress through Chapter Five, "The Long Hymn" (spec MILESTONE_5.md 6.1). Stages only move
 * forward. Stored by ordinal in {@link StoryData.Chapter5}, so only append.
 */
public enum ChapterFiveStage {
	NOT_STARTED,
	/** Chapter Four is done; the call comes at a safe moment. */
	WAITING,
	/** The call has played: find the Buyer's factor at Saltgate, on Ghessar. */
	CALLED,
	/** On Ghessar: get to the factor by one of the three ways (6.3). */
	SALTGATE,
	/** A way is complete: reach Imrel Dask in the counting-house (6.4). */
	THE_FACTOR,
	/** Dask's fate is chosen: have the Saltgate shipwright fit cannons (6.5). */
	ARMED,
	/** Break Ulvaine's blockade over Tyrvann (6.5). */
	BLOCKADE,
	/** The blockade is broken: land on Tyrvann. */
	TYRVANN,
	/** Silence the three verse stones (6.6). */
	VERSES,
	/** Face Darth Ulvaine in the Hymnal (6.7). */
	THE_HYMNAL,
	/** Report to the mentor. */
	AFTERMATH,
	/** The chapter is done; the ongoing loop continues (6.8). */
	COMPLETE;

	/** Lower-case id used in lang keys ({@code quest.oldrepublic.c5.stage.<id>}) and dialogue conditions. */
	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** The stage at this ordinal, clamped into range (the saved form). */
	public static ChapterFiveStage of(int ordinal) {
		ChapterFiveStage[] values = values();
		return values[Math.max(0, Math.min(values.length - 1, ordinal))];
	}

	/** The stage with this id, or null when there is none. */
	public static @Nullable ChapterFiveStage byId(String id) {
		for (ChapterFiveStage stage : values()) {
			if (stage.id().equals(id)) {
				return stage;
			}
		}

		return null;
	}
}
