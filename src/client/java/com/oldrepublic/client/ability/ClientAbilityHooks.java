package com.oldrepublic.client.ability;

import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

/**
 * Registry of {@link ClientAbilityHook}s by ability id. The skills feature's slot key handling looks
 * hooks up here; the force and saber features register theirs during client initialisation.
 */
public final class ClientAbilityHooks {
	private static final Map<String, ClientAbilityHook> HOOKS = new HashMap<>();

	/** Registers the hook for an ability. Registering the same id twice is a programming error. */
	public static void register(String id, ClientAbilityHook hook) {
		if (HOOKS.putIfAbsent(id, hook) != null) {
			throw new IllegalStateException("Client ability hook " + id + " is already registered");
		}
	}

	/** The hook for {@code id}, or null if the ability has none. */
	public static @Nullable ClientAbilityHook get(String id) {
		return HOOKS.get(id);
	}

	private ClientAbilityHooks() {
	}
}
