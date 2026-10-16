package com.oldrepublic.identity;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Server bookkeeping for one yielded entity: where and since when it has yielded, and the pending
 * first strike of an execution. In memory only; a yielded entity loaded from disk is tracked again
 * from the moment it loads.
 */
public final class YieldState {
	final UUID entity;
	final ResourceKey<Level> dimension;
	final long since;
	private long strikeAt = Long.MIN_VALUE;
	private @Nullable UUID striker;

	public YieldState(UUID entity, ResourceKey<Level> dimension, long since) {
		this.entity = entity;
		this.dimension = dimension;
		this.since = since;
	}

	/**
	 * Registers a melee strike by the deciding player. The first strike only warns; a second strike
	 * by the same player within {@code confirmTicks} confirms the execution.
	 *
	 * @return true when this strike confirms the execution
	 */
	public boolean strike(UUID player, long now, int confirmTicks) {
		boolean confirms = player.equals(striker) && now - strikeAt <= confirmTicks && now >= strikeAt;

		if (confirms) {
			striker = null;
			strikeAt = Long.MIN_VALUE;
			return true;
		}

		striker = player;
		strikeAt = now;
		return false;
	}

	/** The last player who struck it, if any (credited when it flees with no decider around). */
	@Nullable UUID striker() {
		return striker;
	}
}
