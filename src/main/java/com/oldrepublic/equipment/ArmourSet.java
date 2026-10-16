package com.oldrepublic.equipment;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

/**
 * The four era armour sets (spec section 9.1). Stored by ordinal over the network and by
 * {@link #id()} in item components, so only append.
 */
public enum ArmourSet {
	REPUBLIC_TROOPER,
	SITH_TROOPER,
	JEDI_ROBES,
	SITH_ROBES;

	/** Lower-case id used in saved item components and lang keys. */
	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** The set with this id, or null when there is none. */
	public static @Nullable ArmourSet byId(String id) {
		for (ArmourSet set : values()) {
			if (set.id().equals(id)) {
				return set;
			}
		}

		return null;
	}

	/** The set at this ordinal, clamped into range. */
	public static ArmourSet byIndex(int index) {
		ArmourSet[] values = values();
		return values[Math.max(0, Math.min(values.length - 1, index))];
	}
}
