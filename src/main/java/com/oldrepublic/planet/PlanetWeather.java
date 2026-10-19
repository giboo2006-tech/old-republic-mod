package com.oldrepublic.planet;

import net.minecraft.world.level.Level;

/**
 * Per-dimension weather (spec MILESTONE_5.md 3.5): Ghessar's sandstorms on their own cycle. The
 * state is a saved server-global attachment ({@code oldrepublic:planet_weather}) sent to the players
 * in that world on join and on change (payload {@code planet_weather}, 18.2).
 *
 * <p>Phase 0 stub: W3 writes the cycle, the attachment, its sync and {@link #kind}'s real body.
 */
public final class PlanetWeather {
	/** Stored and synced by ordinal: append only. */
	public enum Kind {
		CLEAR,
		SANDSTORM
	}

	/** A world's weather and the game tick it ends at (the global attachment {@code oldrepublic:planet_weather}, W3). */
	public record State(Kind kind, long untilTick) {
	}

	/** The weather in {@code level}, on either side: CLEAR except during a Ghessar storm. Stub (W3): always CLEAR. */
	public static Kind kind(Level level) {
		return Kind.CLEAR;
	}

	private PlanetWeather() {
	}
}
