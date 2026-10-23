package com.oldrepublic.ability;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

/** Registry of every {@link ForceAbility}, keyed by id. Abilities register during mod initialisation. */
public final class Abilities {
	private static final Map<String, ForceAbility> ABILITIES = new LinkedHashMap<>();

	/** Registers an ability. Registering the same id twice is a programming error. */
	public static void register(ForceAbility a) {
		if (ABILITIES.putIfAbsent(a.id(), a) != null) {
			throw new IllegalStateException("Ability " + a.id() + " is already registered");
		}
	}

	public static @Nullable ForceAbility get(String id) {
		return ABILITIES.get(id);
	}

	/** Every registered ability, in registration order (read-only). */
	public static Collection<ForceAbility> all() {
		return Collections.unmodifiableCollection(ABILITIES.values());
	}

	private Abilities() {
	}
}
