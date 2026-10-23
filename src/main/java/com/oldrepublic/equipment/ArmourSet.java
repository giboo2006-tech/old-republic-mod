package com.oldrepublic.equipment;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

/**
 * The era armour sets: the four of Milestone 3 (spec section 9.1) and the four of Milestone 4 (spec
 * MILESTONE_4.md 6.2). Stored by ordinal over the network and by {@link #id()} in item components,
 * so only append.
 */
public enum ArmourSet {
	REPUBLIC_TROOPER,
	SITH_TROOPER,
	JEDI_ROBES,
	SITH_ROBES,
	/** The Watch's battle armour: plate over a hooded tabard. */
	WATCH_BATTLE,
	/** Sith war armour: black-red plate and a torn cape. */
	SITH_WAR,
	/** Mandalorian hunter's plate. */
	MANDALORIAN,
	/** Spacer's gear: a padded jacket, a flight cap and buckled boots. */
	SPACER;

	/** Lower-case id used in saved item components and lang keys. */
	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** Whether this is one of the Milestone 4 sets (traits read live per piece, with quality). */
	public boolean isM4() {
		return ordinal() >= WATCH_BATTLE.ordinal();
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
