package com.oldrepublic.skill;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

/** When a skill-tree tab appears in the Force screen (and can be bought from). Append only. */
public enum TreeVisibility {
	/** Once Force Push is learned. */
	PUSH,
	/** Once the first lightsaber is built. */
	SABER,
	/** Once the Crossing is decided (the player has a path). */
	CROSSING,
	/** Once Force Sense is known (the Exploration tree). */
	SENSE;

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static @Nullable TreeVisibility byId(String id) {
		for (TreeVisibility value : values()) {
			if (value.id().equals(id)) {
				return value;
			}
		}

		return null;
	}
}
