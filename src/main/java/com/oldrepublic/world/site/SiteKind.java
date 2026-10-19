package com.oldrepublic.world.site;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.resources.Identifier;

/**
 * The kinds of site a player can find, enter or be given (spec section 3.1). Worldgen kinds are found
 * through their structure tag; the others are runtime sites placed by {@link RuntimeSites} and
 * remembered in {@link WorldSites}. Synced by ordinal ({@code site_presence}), so only append; a
 * {@code switch} over kinds needs a {@code default} branch.
 */
public enum SiteKind {
	CRYSTAL_CAVE,
	JEDI_RUIN,
	SITH_TOMB,
	WRECK_FIELD,
	REPUBLIC_OUTPOST,
	IMPERIAL_OUTPOST,
	ECHO_SHRINE,
	COURIERS_FALL,
	MISSION_SITE,
	RELIQUARY,
	// Milestone 4 (spec MILESTONE_4.md 3.4, 15.1).
	/** A Watch waystone on Selvaren, clean or corrupted (worldgen). */
	WAYSTONE,
	/** A greater tomb on Varkhast (worldgen). */
	GREATER_TOMB,
	/** The Greenwatch Enclave or the Proving (one per planet per server). */
	PLANET_HUB,
	/** The Heartstone below the Greenwatch. */
	HEARTSTONE,
	/** Zarvos's Sepulchre below the Proving. */
	SEPULCHRE,
	/** A per-player expedition: the Singing Hollow or the duskglass rift. */
	EXPEDITION,
	/** Tessaly's Wayfarer cache. */
	WAYFARER_CACHE,
	/** Torvin Kaast's hunting camp. */
	HUNTING_CAMP,
	/** The Chapter Four hulk's ship cradle. */
	SHIP_CRADLE,
	// Milestone 5 (spec MILESTONE_5.md 3.2 to 3.4, 5.7, 17.1).
	/** An old freighter half-buried in Ghessar's dune sea (worldgen). */
	CRASHED_FREIGHTER,
	/** A fortified kajidic waystation in Ghessar's canyons (worldgen). */
	KAJIDIC_POST,
	/** A ring of carved stones with a Hymnsworn camp on Tyrvann (worldgen). */
	HYMN_SHRINE,
	/** A fallen warrior's cairn sunk in Tyrvann's mire (worldgen). */
	MIRE_GRAVE,
	/** A player's debtor camp on Ghessar. */
	DEBTOR_CAMP,
	/** One of a player's three verse stones on Tyrvann. */
	VERSE_STONE,
	/** The Hymnal on Tyrvann: the arch and the halls under it (one per server). */
	HYMNAL,
	/** A player's Imperial detention post on Varkhast. */
	DETENTION_POST,
	/** The Silent Spindle in the home world's orbit (one per server). */
	DERELICT_STATION;

	/** Lower-case id used in saved data, commands and lang keys ({@code site.oldrepublic.<id>}). */
	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** The kind with this id, or null when there is none. */
	public static @Nullable SiteKind byId(String id) {
		for (SiteKind kind : values()) {
			if (kind.id().equals(id)) {
				return kind;
			}
		}

		return null;
	}

	/**
	 * The kind a template of the mod belongs to, from its frozen path (spec 15.1): {@code jedi_ruin/*},
	 * {@code sith_tomb/*}, {@code wreck_field/*}, {@code crystal_cave/*}, {@code outpost/republic_post},
	 * {@code outpost/imperial_post}, {@code site/echo_shrine_*}, {@code story/couriers_fall},
	 * {@code mission/*} and {@code boss/reliquary_*}; in Milestone 4 {@code planet/waystone*},
	 * {@code greater_tomb/*}, {@code hub/greenwatch}, {@code hub/proving}, {@code hub/sepulchre},
	 * {@code planet/heartstone}, {@code expedition/*}, {@code planet/wayfarer_cache},
	 * {@code boss/hunting_camp} and {@code ship/cradle}; in Milestone 5 (MILESTONE_5.md 17.1, 18.1)
	 * {@code hub/saltgate} and {@code hub/holdfast} (hubs), {@code planet/crashed_freighter},
	 * {@code planet/kajidic_post}, {@code planet/hymn_shrine}, {@code planet/mire_grave},
	 * {@code site/debtor_camp}, {@code site/verse_stone}, {@code site/hymnal} and
	 * {@code site/hymnal_arch} (both the Hymnal), {@code site/detention_post} and
	 * {@code station/spindle}, each also with any longer path that begins with its name, so an owner
	 * may split a site into more pieces without a change here; null for anything else
	 * ({@link RuntimeSites} refuses a template without a kind).
	 */
	public static @Nullable SiteKind ofTemplate(Identifier template) {
		if (!OldRepublicMod.MOD_ID.equals(template.getNamespace())) {
			return null;
		}

		String path = template.getPath();

		if (path.startsWith("crystal_cave/")) {
			return CRYSTAL_CAVE;
		} else if (path.startsWith("jedi_ruin/")) {
			return JEDI_RUIN;
		} else if (path.startsWith("sith_tomb/")) {
			return SITH_TOMB;
		} else if (path.startsWith("wreck_field/")) {
			return WRECK_FIELD;
		} else if (path.equals("outpost/republic_post")) {
			return REPUBLIC_OUTPOST;
		} else if (path.equals("outpost/imperial_post")) {
			return IMPERIAL_OUTPOST;
		} else if (path.startsWith("site/echo_shrine_")) {
			return ECHO_SHRINE;
		} else if (path.equals("story/couriers_fall")) {
			return COURIERS_FALL;
		} else if (path.startsWith("mission/")) {
			return MISSION_SITE;
		} else if (path.startsWith("boss/reliquary_")) {
			return RELIQUARY;
		} else if (path.startsWith("planet/waystone")) {
			return WAYSTONE;
		} else if (path.startsWith("greater_tomb/")) {
			return GREATER_TOMB;
		} else if (path.equals("hub/greenwatch") || path.equals("hub/proving")) {
			return PLANET_HUB;
		} else if (path.equals("hub/sepulchre")) {
			return SEPULCHRE;
		} else if (path.equals("planet/heartstone")) {
			return HEARTSTONE;
		} else if (path.startsWith("expedition/")) {
			return EXPEDITION;
		} else if (path.equals("planet/wayfarer_cache")) {
			return WAYFARER_CACHE;
		} else if (path.equals("boss/hunting_camp")) {
			return HUNTING_CAMP;
		} else if (path.equals("ship/cradle")) {
			return SHIP_CRADLE;
		} else if (path.startsWith("hub/saltgate") || path.startsWith("hub/holdfast")) {
			return PLANET_HUB;
		} else if (path.startsWith("planet/crashed_freighter")) {
			return CRASHED_FREIGHTER;
		} else if (path.startsWith("planet/kajidic_post")) {
			return KAJIDIC_POST;
		} else if (path.startsWith("planet/hymn_shrine")) {
			return HYMN_SHRINE;
		} else if (path.startsWith("planet/mire_grave")) {
			return MIRE_GRAVE;
		} else if (path.startsWith("site/debtor_camp")) {
			return DEBTOR_CAMP;
		} else if (path.startsWith("site/verse_stone")) {
			return VERSE_STONE;
		} else if (path.startsWith("site/hymnal")) {
			return HYMNAL;
		} else if (path.startsWith("site/detention_post")) {
			return DETENTION_POST;
		} else if (path.startsWith("station/spindle")) {
			return DERELICT_STATION;
		}

		return null;
	}
}
