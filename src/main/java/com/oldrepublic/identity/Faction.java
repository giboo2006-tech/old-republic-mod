package com.oldrepublic.identity;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

/**
 * A faction the player has reputation with. The display name is {@code faction.oldrepublic.<id>}.
 * Ordinals travel in effect events, so only append. A {@code switch} over factions needs a default
 * branch: more may come (spec MILESTONE_5.md 7).
 */
public enum Faction {
	JEDI_ORDER,
	SITH_EMPIRE,
	FRONTIER,
	/** The Republic and its army, apart from the Jedi Order (spec section 5.1). */
	REPUBLIC,
	/** The Hutt crime family that owns Saltgate on Ghessar (spec MILESTONE_5.md 7). */
	VESSBA_KAJIDIC,
	/** The Mandalorian clan camped on Tyrvann (spec MILESTONE_5.md 7). */
	CLAN_HASK;

	/** Lower-case id used in saved data, commands, dialogue conditions and lang keys. */
	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/**
	 * Whether every player knows this faction from the start: the four of Milestones 1 to 4. A later
	 * faction is listed (the Standing panel, refusals that name it) only once the player has met it
	 * ({@link IdentityRules#metChoice}, spec MILESTONE_5.md 7.3, 14.1).
	 */
	public boolean knownFromStart() {
		return ordinal() <= REPUBLIC.ordinal();
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
