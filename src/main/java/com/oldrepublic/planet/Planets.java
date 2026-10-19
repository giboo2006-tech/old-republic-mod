package com.oldrepublic.planet;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * The worlds a player can stand on (spec MILESTONE_4.md 3.1, 3.6): the home world (the overworld,
 * the frontier of M1 to M3) and the planet dimensions, Selvaren and Varkhast since Milestone 4,
 * Ghessar and Tyrvann since Milestone 5 (MILESTONE_5.md 3.1). M1 to M3 content keeps its
 * overworld-only checks through {@link #isHomeWorld}; Milestone 4 and 5 story and missions use
 * {@link #isStoryLevel}.
 *
 * <p>Each world has an orbit, a dimension of its own above it (MILESTONE_5.md 5.1):
 * {@code oldrepublic:orbit_<planet id>}, the home world's included. An orbit is neither a story
 * level nor a planet; {@link #orbitOf} names the world below it.
 */
public final class Planets {
	public static final ResourceKey<Level> SELVAREN = ResourceKey.create(Registries.DIMENSION, OldRepublicMod.id("selvaren"));
	public static final ResourceKey<Level> VARKHAST = ResourceKey.create(Registries.DIMENSION, OldRepublicMod.id("varkhast"));
	/** The wasteland port world (MILESTONE_5.md 3.2). */
	public static final ResourceKey<Level> GHESSAR = ResourceKey.create(Registries.DIMENSION, OldRepublicMod.id("ghessar"));
	/** The storm jungle (MILESTONE_5.md 3.3). */
	public static final ResourceKey<Level> TYRVANN = ResourceKey.create(Registries.DIMENSION, OldRepublicMod.id("tyrvann"));

	/**
	 * Stored and synced by ordinal: append only. A {@code switch} over planets outside this class needs
	 * a {@code default} branch that does the safe thing for a world without a row (MILESTONE_5.md 3.1).
	 */
	public enum Planet {
		HOME,
		SELVAREN,
		VARKHAST,
		/** Milestone 5 (MILESTONE_5.md 3.2). */
		GHESSAR,
		/** Milestone 5 (MILESTONE_5.md 3.3). */
		TYRVANN;

		/** The level key of this world. */
		public ResourceKey<Level> level() {
			return switch (this) {
				case HOME -> Level.OVERWORLD;
				case SELVAREN -> Planets.SELVAREN;
				case VARKHAST -> Planets.VARKHAST;
				case GHESSAR -> Planets.GHESSAR;
				case TYRVANN -> Planets.TYRVANN;
			};
		}

		/** Lower-case id used in saved data, commands and lang keys. */
		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		/** The world of this level key, or null for any other dimension (the Nether, the End, an orbit, other mods). */
		public static @Nullable Planet of(ResourceKey<Level> key) {
			for (Planet planet : values()) {
				if (planet.level().equals(key)) {
					return planet;
				}
			}

			return null;
		}

		/** The world with this id, or null. */
		public static @Nullable Planet byId(String id) {
			for (Planet planet : values()) {
				if (planet.id().equals(id)) {
					return planet;
				}
			}

			return null;
		}
	}

	/** The orbit of each world, by {@link Planet} ordinal (5.1): {@code oldrepublic:orbit_<planet id>}. */
	private static final List<ResourceKey<Level>> ORBITS = Stream.of(Planet.values())
			.map(planet -> ResourceKey.create(Registries.DIMENSION, OldRepublicMod.id("orbit_" + planet.id())))
			.toList();

	/** The overworld or a planet: where Milestone 4 and 5 story, missions and sites may happen. An orbit is not one. */
	public static boolean isStoryLevel(Level level) {
		return Planet.of(level.dimension()) != null;
	}

	/** The overworld only: where M1 to M3 content lives. */
	public static boolean isHomeWorld(Level level) {
		return level.dimension() == Level.OVERWORLD;
	}

	/** One of the planet dimensions: any {@link Planet} but the home world (not an orbit). */
	public static boolean isPlanet(Level level) {
		return isPlanet(level.dimension());
	}

	/** Whether {@code key} is a planet's level: any {@link Planet} but the home world (not an orbit). */
	public static boolean isPlanet(ResourceKey<Level> key) {
		Planet planet = Planet.of(key);
		return planet != null && planet != Planet.HOME;
	}

	/** Every planet, the home world left out, in ordinal order. */
	public static List<Planet> planets() {
		return Stream.of(Planet.values()).filter(planet -> planet != Planet.HOME).toList();
	}

	/** The level of a world, or null when the dimension is missing (a data pack removed it, 12.2). */
	public static @Nullable ServerLevel level(MinecraftServer server, Planet planet) {
		return server.getLevel(planet.level());
	}

	// ---------------------------------------------------------------- orbits (MILESTONE_5.md 5.1)

	/** The level key of {@code planet}'s orbit: {@code oldrepublic:orbit_<planet id>}. */
	public static ResourceKey<Level> orbit(Planet planet) {
		return ORBITS.get(planet.ordinal());
	}

	/** The world whose orbit {@code key} is, or null when it is not an orbit. */
	public static @Nullable Planet orbitOf(ResourceKey<Level> key) {
		for (Planet planet : Planet.values()) {
			if (orbit(planet).equals(key)) {
				return planet;
			}
		}

		return null;
	}

	/** Whether {@code level} is one of the orbits. */
	public static boolean isOrbit(Level level) {
		return isOrbit(level.dimension());
	}

	/** Whether {@code key} is one of the orbits. */
	public static boolean isOrbit(ResourceKey<Level> key) {
		return orbitOf(key) != null;
	}

	/** The level of {@code planet}'s orbit, or null when the dimension is missing (a data pack removed it). */
	public static @Nullable ServerLevel orbitLevel(MinecraftServer server, Planet planet) {
		return server.getLevel(orbit(planet));
	}

	private Planets() {
	}
}
