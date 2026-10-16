package com.oldrepublic.identity;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

/** A faction the player has reputation with. The display name is {@code faction.oldrepublic.<id>}. */
public enum Faction {
	JEDI_ORDER,
	SITH_EMPIRE,
	FRONTIER;

	/** Lower-case id used in saved data, commands, dialogue conditions and lang keys. */
	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** The faction with this id, or null when there is none. */
	public static @Nullable Faction byId(String id) {
		for (Faction faction : values()) {
			if (faction.id().equals(id)) {
				return faction;
			}
		}

		return null;
	}
}
