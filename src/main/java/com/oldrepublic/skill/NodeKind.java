package com.oldrepublic.skill;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

/** What a skill-tree node is. Stored in tree JSON by {@link #id()}. */
public enum NodeKind {
	/** Unlocks an active power that can sit in an ability slot. */
	ABILITY,
	/** An exclusive upgrade inside a {@code group}; owning one excludes its siblings. */
	BRANCH,
	/** A passive with one to three ranks. */
	PASSIVE,
	/** A saber form; several can be owned, one is active. */
	FORM,
	/** A saber move, such as the lunge. */
	TECHNIQUE;

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static @Nullable NodeKind byId(String id) {
		for (NodeKind kind : values()) {
			if (kind.id().equals(id)) {
				return kind;
			}
		}

		return null;
	}
}
