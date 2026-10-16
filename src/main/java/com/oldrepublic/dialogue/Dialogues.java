package com.oldrepublic.dialogue;

import org.jspecify.annotations.Nullable;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Server entry point of the data-driven dialogue system ({@code data/oldrepublic/dialogue/*.json}).
 * One session per player; the server validates every choice.
 *
 * <p>Phase 0 stub: no dialogue is loaded yet, so {@link #open} always refuses.
 */
public final class Dialogues {
	/**
	 * Starts dialogue {@code id} for the player, replacing any open session.
	 *
	 * @param speaker the entity speaking (faced, range-checked), or null for a speaker-less dialogue
	 * @return false if the dialogue does not exist or cannot open now
	 */
	public static boolean open(ServerPlayer p, Identifier id, @Nullable Entity speaker) {
		return false;
	}

	/** Ends the player's session, if any, with no choice made. */
	public static void close(ServerPlayer p) {
	}

	private Dialogues() {
	}
}
