package com.oldrepublic.quest.story;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

/**
 * The steps of the Fall and the Return (spec MILESTONE_5.md 8.3). Steps only move forward within
 * one questline. Stored by ordinal in {@link StoryData.Turning}, so only append.
 */
public enum TurningStage {
	/** No questline is under way. */
	NONE,
	/** The offer has been made; accept or decline it (8.2). */
	OFFERED,
	/** "A Gift for the Empire" or "Amends". */
	TASK,
	/** "The Last Lesson" or "The Overseer's Due": the old mentor comes to the player. */
	CONFRONTATION,
	/** "Kneel" or "Welcomed": the new mentor names the player. */
	RITE,
	/** The rite is done and the path has changed (8.4). */
	COMPLETE;

	/** Lower-case id used in dialogue conditions ({@code turn:stage}). */
	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** The stage at this ordinal, clamped into range (the saved form). */
	public static TurningStage of(int ordinal) {
		TurningStage[] values = values();
		return values[Math.max(0, Math.min(values.length - 1, ordinal))];
	}

	/** The stage with this id, or null when there is none. */
	public static @Nullable TurningStage byId(String id) {
		for (TurningStage stage : values()) {
			if (stage.id().equals(id)) {
				return stage;
			}
		}

		return null;
	}
}
