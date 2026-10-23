package com.oldrepublic.identity;

import java.util.Locale;

/**
 * The road a character walks after the Crossing. {@code NONE} until then. Stored by {@link #id()},
 * so constants may be appended but never renamed.
 */
public enum Path {
	NONE,
	JEDI,
	SITH,
	INDEPENDENT;

	/** Lower-case id used in saved data, commands, dialogue conditions and lang keys. */
	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** The path with this id, or {@link #NONE} for an unknown or empty id. */
	public static Path byId(String id) {
		for (Path path : values()) {
			if (path.id().equals(id)) {
				return path;
			}
		}

		return NONE;
	}
}
