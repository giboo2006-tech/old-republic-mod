package com.oldrepublic.dialogue;

import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Handlers for the {@code "action"} field of dialogue options, such as {@code story:join_jedi}.
 * Features register theirs during initialisation. Unknown ids are logged and ignored by the engine.
 *
 * <p>An action may carry one argument after a space, such as {@code "mission:start r1_silent_patrol"}:
 * register those with {@link #registerArg}. The engine resolves an action text in the order of
 * {@link Dialogues} (the exact text first, then {@code id:arg}, {@code id_arg}, then the argument
 * action {@code id}), so actions registered either way keep working.
 */
public final class DialogueActions {
	private static final Map<String, Action> ACTIONS = new HashMap<>();
	private static final Map<String, ArgAction> ARG_ACTIONS = new HashMap<>();

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

	/** Registers a handler that takes an argument. Registering the same id twice is a programming error. */
	public static void registerArg(String id, ArgAction a) {
		if (ARG_ACTIONS.putIfAbsent(id, a) != null) {
			throw new IllegalStateException("Dialogue argument action " + id + " is already registered");
		}
	}

	/** The argument handler for {@code id}, or null if none is registered. */
	public static @Nullable ArgAction getArg(String id) {
		return ARG_ACTIONS.get(id);
	}

	@FunctionalInterface
	public interface Action {
		void run(ServerPlayer p, @Nullable Entity speaker);
	}

	/** An action with one argument: the text after the first run of whitespace, trimmed. */
	@FunctionalInterface
	public interface ArgAction {
		void run(ServerPlayer p, @Nullable Entity speaker, String arg);
	}

	private DialogueActions() {
	}
}
