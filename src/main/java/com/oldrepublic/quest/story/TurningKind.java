package com.oldrepublic.quest.story;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

/**
 * Which way a player is turning (spec MILESTONE_5.md 8): the Fall takes a Jedi to the Sith, the
 * Return takes a Sith to the Jedi. Stored by ordinal in {@link StoryData.Turning}, so only append.
 */
public enum TurningKind {
	/** No questline has been offered. */
	NONE,
	/** Jedi to Sith (8.3). */
	FALL,
	/** Sith to Jedi (8.3). */
	RETURN;

	/** Lower-case id used in dialogue conditions ({@code fall}, {@code return}) and source ids. */
	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** The kind at this ordinal, clamped into range (the saved form). */
	public static TurningKind of(int ordinal) {
		TurningKind[] values = values();
		return values[Math.max(0, Math.min(values.length - 1, ordinal))];
	}

	/** The kind with this id, or null when there is none. */
	public static @Nullable TurningKind byId(String id) {
		for (TurningKind kind : values()) {
			if (kind.id().equals(id)) {
				return kind;
			}
		}

		return null;
	}
}
