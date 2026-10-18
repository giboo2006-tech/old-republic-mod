package com.oldrepublic.planet;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * The worlds a player can stand on (spec MILESTONE_4.md 3.1, 3.6): the home world (the overworld,
 * the frontier of M1 to M3) and the two planet dimensions. M1 to M3 content keeps its overworld-only
 * checks through {@link #isHomeWorld}; Milestone 4 story and missions use {@link #isStoryLevel}.
 */
public final class Planets {
	public static final ResourceKey<Level> SELVAREN = ResourceKey.create(Registries.DIMENSION, OldRepublicMod.id("selvaren"));
	public static final ResourceKey<Level> VARKHAST = ResourceKey.create(Registries.DIMENSION, OldRepublicMod.id("varkhast"));

	/** Stored and synced by ordinal: append only. */
	public enum Planet {
		HOME,
		SELVAREN,
		VARKHAST;

		/** The level key of this world. */
		public ResourceKey<Level> level() {
			return switch (this) {
				case HOME -> Level.OVERWORLD;
				case SELVAREN -> Planets.SELVAREN;
				case VARKHAST -> Planets.VARKHAST;
			};
		}

		/** Lower-case id used in saved data, commands and lang keys. */
		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		/** The world of this level key, or null for any other dimension (the Nether, the End, other mods). */
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

	/** The overworld or a planet: where Milestone 4 story, missions and sites may happen. */
	public static boolean isStoryLevel(Level level) {
		return Planet.of(level.dimension()) != null;
	}

	/** The overworld only: where M1 to M3 content lives. */
	public static boolean isHomeWorld(Level level) {
		return level.dimension() == Level.OVERWORLD;
	}

	/** One of the planet dimensions (not the home world). */
	public static boolean isPlanet(Level level) {
		ResourceKey<Level> key = level.dimension();
		return key.equals(SELVAREN) || key.equals(VARKHAST);
	}

	/** The level of a world, or null when the dimension is missing (a data pack removed it, 12.2). */
	public static @Nullable ServerLevel level(MinecraftServer server, Planet planet) {
		return server.getLevel(planet.level());
	}

	private Planets() {
	}
}
