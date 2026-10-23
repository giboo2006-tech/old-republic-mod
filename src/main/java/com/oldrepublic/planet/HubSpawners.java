package com.oldrepublic.planet;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/**
 * Who spawns a hub's people (spec MILESTONE_4.md 3.5). The hub templates hold {@code hub_spawner}
 * blocks, each with a role: {@code host}, {@code apprentice}, {@code acolyte}, {@code guard},
 * {@code droid}, or {@code vendor:<stock id>} (for example {@code vendor:watch_quartermaster}).
 * The feature that owns the people (W2; W6 for the droid) registers a {@link Factory} per role
 * during init; a role with a colon falls back to the factory of its prefix ({@code vendor}). The
 * spawner calls it the first time a player comes near and again once per in-game day while its
 * last spawn is dead or gone ({@code HubSpawnerBlockEntity}).
 */
public final class HubSpawners {
	/** Spawns one hub person for {@code role} at {@code at} (a free feet cell); null when it cannot. */
	@FunctionalInterface
	public interface Factory {
		@Nullable Entity spawn(ServerLevel level, BlockPos at, Planets.Planet planet, String role);
	}

	private static final Map<String, Factory> FACTORIES = new ConcurrentHashMap<>();
	private static final Map<String, Boolean> WARNED = new ConcurrentHashMap<>();

	/** Registers the factory of {@code role} (a full role, or a prefix such as {@code vendor}). Replaces an earlier one. */
	public static void register(String role, Factory factory) {
		FACTORIES.put(role, factory);
	}

	/** The factory for {@code role}: an exact match, else the factory of the part before the colon; null without one. */
	public static @Nullable Factory factory(String role) {
		Factory exact = FACTORIES.get(role);

		if (exact != null) {
			return exact;
		}

		int colon = role.indexOf(':');
		return colon > 0 ? FACTORIES.get(role.substring(0, colon)) : null;
	}

	/** Spawns {@code role} through its factory; null (warned once per role) when nobody registered it or it could not spawn. */
	public static @Nullable Entity spawn(ServerLevel level, BlockPos at, Planets.Planet planet, String role) {
		Factory factory = factory(role);

		if (factory == null) {
			if (WARNED.putIfAbsent(role, Boolean.TRUE) == null) {
				OldRepublicMod.LOGGER.warn("No hub spawner factory for role '{}' yet; the hub stays empty there", role);
			}

			return null;
		}

		return factory.spawn(level, at, planet, role);
	}

	private HubSpawners() {
	}
}
