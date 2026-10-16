package com.oldrepublic.ability;

import net.minecraft.server.level.ServerPlayer;

/**
 * Cost and cooldown modifiers every ability applies: the {@code unbound} passive and the
 * opposed-alignment rule for side powers.
 *
 * <p>Phase 0 stub: no modifiers, nothing refused.
 */
public final class AbilityCosts {
	/** Multiplier on the Force Energy cost. */
	public static float costMultiplier(ServerPlayer p, String abilityId) {
		return 1f;
	}

	/** Multiplier on the cooldown. */
	public static float cooldownMultiplier(ServerPlayer p, String abilityId) {
		return 1f;
	}

	/** True if the player's alignment refuses this power; sends the feedback itself. */
	public static boolean refused(ServerPlayer p, String abilityId) {
		return false;
	}

	/** False while the power is opposed by the player's alignment (no mastery gain). */
	public static boolean masteryAllowed(ServerPlayer p, String abilityId) {
		return true;
	}

	private AbilityCosts() {
	}
}
