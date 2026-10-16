package com.oldrepublic.dialogue;

import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Handlers for the {@code "action"} field of dialogue options, such as {@code story:join_jedi}.
 * Features register theirs during initialisation. Unknown ids are logged and ignored by the engine.
 */
public final class DialogueActions {
	private static final Map<String, Action> ACTIONS = new HashMap<>();

	/** Registers a handler. Registering the same id twice is a programming error. */
	public static void register(String id, Action a) {
		if (ACTIONS.putIfAbsent(id, a) != null) {
			throw new IllegalStateException("Dialogue action " + id + " is already registered");
		}
	}

	/** The handler for {@code id}, or null if none is registered. */
	public static @Nullable Action get(String id) {
		return ACTIONS.get(id);
	}

	@FunctionalInterface
	public interface Action {
		void run(ServerPlayer p, @Nullable Entity speaker);
	}

	private DialogueActions() {
	}
}
