package com.oldrepublic.combat;

/**
 * Which side an entity fights for, independent of any player's standing (spec 5.2, 7). Entities of
 * the same allegiance never hurt each other with bolts ({@link Allegiances#friendly}). Synced and
 * saved by ordinal: append only.
 */
public enum Allegiance {
	/** No side: settlers, animals, vanilla mobs and (unless disguised) players. Never friendly with anything. */
	NONE,
	/** Poacher crews. */
	CREW,
	/** Republic troopers. */
	REPUBLIC,
	/** Imperial troopers. */
	EMPIRE,
	/** Ruin Sentinels. */
	ANCIENT,
	/** Orren Vasch and the sentinels he binds in the Reliquary. */
	ARCHIVE,
	/** Mandalorian hunters, their droid escorts and Torvin Kaast. */
	HUNTERS,
	/** The Vessba kajidic's cartel enforcers (MILESTONE_5.md 7, 12.1). */
	KAJIDIC,
	/** Clan Hask's warriors (MILESTONE_5.md 7, 12.1). */
	CLAN,
	/** Ulvaine's cult: the Hymnsworn, Darth Ulvaine, the First Cantor and his choir (MILESTONE_5.md 12). */
	HYMN
}
