package com.oldrepublic.travel;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.planet.Planets;

/**
 * A place a ship can fly to (spec MILESTONE_4.md 4.5). Stored by id in saved data and by ordinal in
 * payloads: append only.
 */
public enum Destination {
	/** The owner's landing beacon, else the ship cradle (overworld). */
	HOME(Planets.Planet.HOME),
	/** The Greenwatch Enclave pad, Selvaren. */
	GREENWATCH(Planets.Planet.SELVAREN),
	/** The Proving pad, Varkhast. */
	PROVING(Planets.Planet.VARKHAST),
	/** Outside Torvin Kaast's camp, on the owner's path world; unlocked only during the challenge. */
	HUNTING_CAMP(null);

	private final Planets.@Nullable Planet planet;

	Destination(Planets.@Nullable Planet planet) {
		this.planet = planet;
	}

	/** The world of a fixed destination; null for the hunting camp, whose world depends on the owner. */
	public Planets.@Nullable Planet planet() {
		return planet;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static @Nullable Destination byId(String id) {
		for (Destination d : values()) {
			if (d.id().equals(id)) {
				return d;
			}
		}

		return null;
	}
}
