package com.oldrepublic.dialogue;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Lets a feature take over another feature's dialogue for a while without editing its data (spec
 * MILESTONE_5.md 6.11, 17.1): while a Jedi's Fall is at its task, the paths worker sends Keeper
 * Ansa's and Overseer Kaddor's Chapter Four dialogues to its own (8.3). {@link Dialogues#open} asks
 * the redirect registered for the dialogue it is about to open first, before anything else.
 *
 * <p>The rules: a dialogue id is the path of an {@code oldrepublic} dialogue ({@code c4_ansa}) or a
 * full identifier ({@code othermod:talk}), and both name the same registration. The redirect gets
 * the player and the id in that form (the path for the mod's own dialogues, else the full id) and
 * returns the dialogue to open instead, in the same form; returning the same id or null opens the
 * dialogue unchanged. One hop only: the dialogue a redirect names opens as it is, with the same
 * speaker, even if it has a redirect of its own. One redirect per dialogue id; registering a second
 * is a programming error.
 */
public final class DialogueRedirects {
	private static final Map<Identifier, BiFunction<ServerPlayer, String, String>> REDIRECTS = new HashMap<>();

	/**
	 * Registers the redirect for {@code dialogueId}, usually from a feature's init.
	 *
	 * @param redirect gets the player and the dialogue id; returns the dialogue to open instead, or the
	 *     same id or null for no change
	 */
	public static void register(String dialogueId, BiFunction<ServerPlayer, String, String> redirect) {
		Identifier id = parse(dialogueId);

		if (id == null) {
			throw new IllegalArgumentException("Not a dialogue id: " + dialogueId);
		}

		if (REDIRECTS.putIfAbsent(id, redirect) != null) {
			throw new IllegalStateException("Dialogue redirect for " + dialogueId + " is already registered");
		}
	}

	/** Removes the redirect for {@code dialogueId}, if any (for tests, or a feature done with it). */
	public static void unregister(String dialogueId) {
		Identifier id = parse(dialogueId);

		if (id != null) {
			REDIRECTS.remove(id);
		}
	}

	/**
	 * The dialogue {@link Dialogues#open} opens for the player when asked for {@code id}: what the
	 * redirect returns, or {@code id} itself when there is no redirect, it answers null or the same
	 * id, or its answer is not a valid id (logged).
	 */
	public static Identifier resolve(ServerPlayer p, Identifier id) {
		BiFunction<ServerPlayer, String, String> redirect = REDIRECTS.get(id);

		if (redirect == null) {
			return id;
		}

		String answer = redirect.apply(p, name(id));

		if (answer == null) {
			return id;
		}

		Identifier target = parse(answer);

		if (target == null) {
			OldRepublicMod.LOGGER.warn("The redirect for dialogue {} answered {}, which is not a dialogue id", id, answer);
			return id;
		}

		return target;
	}

	/** A dialogue id as written in data and code: bare for the mod's own namespace. */
	private static @Nullable Identifier parse(String dialogueId) {
		String trimmed = dialogueId.trim();
		return Identifier.tryParse(trimmed.contains(":") ? trimmed : OldRepublicMod.MOD_ID + ":" + trimmed);
	}

	/** The form a redirect sees: the path of an {@code oldrepublic} dialogue, else the full id. */
	private static String name(Identifier id) {
		return OldRepublicMod.MOD_ID.equals(id.getNamespace()) ? id.getPath() : id.toString();
	}

	private DialogueRedirects() {
	}
}
