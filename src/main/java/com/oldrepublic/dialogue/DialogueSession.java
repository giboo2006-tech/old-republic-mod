package com.oldrepublic.dialogue;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * One player's open dialogue on the server: which dialogue and node, who speaks, and a session id
 * the client must quote back so a late or forged choice for an old session is ignored. In memory
 * only; a relog or restart ends it with no choice made.
 */
public final class DialogueSession {
	final int id;
	final Identifier dialogue;
	final @Nullable UUID speaker;
	final ResourceKey<Level> dimension;
	String node;

	DialogueSession(int id, Identifier dialogue, String node, @Nullable UUID speaker, ResourceKey<Level> dimension) {
		this.id = id;
		this.dialogue = dialogue;
		this.node = node;
		this.speaker = speaker;
		this.dimension = dimension;
	}

	public int id() {
		return id;
	}

	public Identifier dialogue() {
		return dialogue;
	}

	public String node() {
		return node;
	}
}
