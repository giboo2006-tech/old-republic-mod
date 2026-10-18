package com.oldrepublic.world.site;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.resources.Identifier;

/**
 * The kinds of site a player can find, enter or be given (spec section 3.1). Worldgen kinds are found
 * through their structure tag; the others are runtime sites placed by {@link RuntimeSites} and
 * remembered in {@link WorldSites}. Synced by ordinal ({@code site_presence}), so only append.
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
	SHIP_CRADLE;

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
	 * {@code boss/hunting_camp} and {@code ship/cradle}; null for anything else.
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
		}

		return null;
	}
}
