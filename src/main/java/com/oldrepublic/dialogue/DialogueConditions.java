package com.oldrepublic.dialogue;

import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Conditions for the {@code "if"} object of dialogue options. The engine has built-in ones
 * ({@code path}, {@code rank_min}, {@code alignment_min} and so on); features add their own, such
 * as {@code story:stage}.
 */
public final class DialogueConditions {
	private static final Map<String, Condition> CONDITIONS = new HashMap<>();

	/** Registers a condition. Registering the same id twice is a programming error. */
	public static void register(String id, Condition c) {
		if (CONDITIONS.putIfAbsent(id, c) != null) {
			throw new IllegalStateException("Dialogue condition " + id + " is already registered");
		}
	}

	/** The condition for {@code id}, or null if none is registered. */
	public static @Nullable Condition get(String id) {
		return CONDITIONS.get(id);
	}

	@FunctionalInterface
	public interface Condition {
		/**
		 * @param arg the condition's value from the JSON, as a string
		 * @return null when the condition is met, otherwise a lang key explaining why not
		 */
		@Nullable String failReason(ServerPlayer p, @Nullable Entity speaker, String arg);
	}

	private DialogueConditions() {
	}
}
