package com.oldrepublic.travel;

import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.planet.Planets;

/**
 * A place a ship can fly to (spec MILESTONE_4.md 4.5). Stored by id in saved data and by ordinal in
 * payloads: append only.
 *
 * <p>Each destination also carries what the list needs to be read instead of hard-coded entries
 * (MILESTONE_5.md 3.1): the milestone that added it, its point and light on the ship screen's star
 * chart (fractions of the chart's size, and an ARGB colour) and the faint lanes that join it to
 * earlier destinations. A destination added after Milestone 4 is left off the Nav tab while it is
 * locked ({@link #hiddenWhileLocked}), so the chart of a save without its coordinates looks and
 * acts as it did in Milestone 4.
 */
public enum Destination {
	/** The owner's landing beacon, else the ship cradle (overworld). */
	HOME(Planets.Planet.HOME, 4, 0.16f, 0.58f, 0xFFFFE9B0),
	/** The Greenwatch Enclave pad, Selvaren. */
	GREENWATCH(Planets.Planet.SELVAREN, 4, 0.56f, 0.26f, 0xFF9FE8C8, HOME),
	/** The Proving pad, Varkhast. */
	PROVING(Planets.Planet.VARKHAST, 4, 0.80f, 0.68f, 0xFFFF9A6A, HOME, GREENWATCH),
	/** Outside Torvin Kaast's camp, on the owner's path world; unlocked only during the challenge. */
	HUNTING_CAMP(null, 4, 0.42f, 0.84f, 0xFFFFB347, HOME),
	/**
	 * Milestone 5: the Vessba kajidic's port on Ghessar (MILESTONE_5.md 3.2.1); its pad bays.
	 * Unlocked by the call (6.2).
	 */
	SALTGATE(Planets.Planet.GHESSAR, 5, 0.20f, 0.16f, 0xFFE9C89A, HOME),
	/**
	 * Milestone 5: Clan Hask's holdfast on Tyrvann (MILESTONE_5.md 3.3.1); its pad bays. Unlocked at
	 * {@code ARMED} and refused until {@code BLOCKADE} (6.5).
	 */
	HOLDFAST(Planets.Planet.TYRVANN, 5, 0.88f, 0.10f, 0xFFB8A6FF, SALTGATE);

	private final Planets.@Nullable Planet planet;
	private final int milestone;
	private final float chartX;
	private final float chartY;
	private final int light;
	private final List<Destination> lanes;

	Destination(Planets.@Nullable Planet planet, int milestone, float chartX, float chartY, int light, Destination... lanes) {
		this.planet = planet;
		this.milestone = milestone;
		this.chartX = chartX;
		this.chartY = chartY;
		this.light = light;
		this.lanes = List.of(lanes);
	}

	/** The world of a fixed destination; null for the hunting camp, whose world depends on the owner. */
	public Planets.@Nullable Planet planet() {
		return planet;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** The milestone that added this destination (4 or 5). */
	public int milestone() {
		return milestone;
	}

	/**
	 * Whether the Nav tab leaves this destination out while the viewer has not unlocked it: true for
	 * a destination added after Milestone 4, whose coordinates the story gives (MILESTONE_5.md 3.1,
	 * 6.2, 6.5). The Milestone 4 four are always listed, locked or not, as before.
	 */
	public boolean hiddenWhileLocked() {
		return milestone > 4;
	}

	/** Where the destination sits on the star chart, across (0 left to 1 right). */
	public float chartX() {
		return chartX;
	}

	/** Where the destination sits on the star chart, down (0 top to 1 bottom). */
	public float chartY() {
		return chartY;
	}

	/** The colour of the destination's light on the chart and its mark on the list (ARGB). */
	public int light() {
		return light;
	}

	/**
	 * The earlier destinations this one is joined to by a lane on the chart, each drawn from the
	 * earlier destination to this one; in order, they give the Milestone 4 chart's lanes first.
	 */
	public List<Destination> lanes() {
		return lanes;
	}

	public static @Nullable Destination byId(String id) {
		for (Destination d : values()) {
			if (d.id().equals(id)) {
				return d;
			}
		}

		return null;
	}

	/**
	 * The fixed destination on {@code planet}: its hub pad (home on the home world), or null for a
	 * planet without one (MILESTONE_5.md 3.1).
	 */
	public static @Nullable Destination hubOf(Planets.Planet planet) {
		for (Destination d : values()) {
			if (d.planet == planet) {
				return d;
			}
		}

		return null;
	}
}
