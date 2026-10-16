package com.oldrepublic.ability;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Situations that forbid a power outright, decided by other features (for example being held:
 * only Force Push, and only when the hold allows it). The ability runtime and the shared start
 * checks ask {@link #refusal} before any energy is spent and show the answer to the player.
 * Register during init. Server side.
 */
public final class AbilityLocks {
	/** One feature's lock. */
	public interface Lock {
		/** Why {@code p} may not use {@code abilityId} now, shown to the player; null when allowed. */
		@Nullable Component refusal(ServerPlayer p, String abilityId);
	}

	private static final List<Lock> LOCKS = new ArrayList<>();

	/** Adds a lock. */
	public static void register(Lock lock) {
		LOCKS.add(lock);
	}

	/** The first registered lock's refusal, or null when every lock allows the power. */
	public static @Nullable Component refusal(ServerPlayer p, String abilityId) {
		for (Lock lock : LOCKS) {
			Component reason = lock.refusal(p, abilityId);

			if (reason != null) {
				return reason;
			}
		}

		return null;
	}

	private AbilityLocks() {
	}
}
