package com.oldrepublic.space;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Damage to a player's ship in space and the shield in front of it (spec MILESTONE_5.md 5.4). Owned
 * by W2 space; the flight code (W1) and the stories ask it whether a ship is under fire. Phase 0
 * stub: no ship takes a hit and none has a shield until W2 fills it; W2 keeps these signatures and
 * may add methods.
 */
public final class ShipCombat {
	/**
	 * Enemy fire hits {@code owner}'s ship: the shield first, then the hull through
	 * {@code Ships.update}; at 0 hull, {@code Ships.disable} (5.4).
	 */
	public static void hitShip(ServerPlayer owner, float damage, @Nullable Entity source) {
		// Phase 0 stub: W2 fills it.
	}

	/** Whether {@code owner}'s ship was hit in the last {@code space.underFireTicks} (the {@code UNDER_FIRE} refusal, 4.7). */
	public static boolean underFire(ServerPlayer owner) {
		// Phase 0 stub: W2 fills it.
		return false;
	}

	/** The shield points {@code owner}'s ship has now (0 without a {@code SHIELD} upgrade, 5.4). */
	public static int shield(ServerPlayer owner) {
		// Phase 0 stub: W2 fills it.
		return 0;
	}

	private ShipCombat() {
	}
}
