package com.oldrepublic.planet;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerPlayer;

/**
 * Per-player runtime sites on the planets (spec MILESTONE_4.md 3.4). Phase 0 stub (W1 fills it):
 * nothing is placed yet, so {@link #ensure} returns null and callers retry, as they will when a
 * planet's chunks are not generated yet.
 */
public final class PlanetSites {
	/** Stored by ordinal: append only. */
	public enum Site {
		SINGING_HOLLOW,
		DUSKGLASS_RIFT,
		WAYFARER_CACHE,
		HUNTING_CAMP,
		AMBUSH_ONE,
		AMBUSH_TWO,
		HEARTSTONE,
		SEPULCHRE
	}

	/**
	 * The site's position, placing it first when it is not placed yet; null when it cannot be placed
	 * now (its chunks are not loaded, or no ring fits). Callers retry later and point the Calling at
	 * the ring meanwhile.
	 */
	public static @Nullable GlobalPos ensure(ServerPlayer owner, Site site, Planets.Planet planet) {
		return null;
	}

	/** The site's position if it is placed for this owner, else null. Never places. */
	public static @Nullable GlobalPos find(ServerPlayer owner, Site site) {
		return null;
	}

	private PlanetSites() {
	}
}
