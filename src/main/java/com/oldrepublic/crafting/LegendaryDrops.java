package com.oldrepublic.crafting;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/** Legendary schematic drops and their guaranteed progress (MILESTONE_5.md 11.4); W7 owns it. Phase 0 stub. */
public final class LegendaryDrops {
	/**
	 * Rolls {@code chance} (0 to 1) for {@code p} to learn the legendary schematic {@code schematic} now, with the guarantee
	 * {@code pity} (the miss count at which it always drops, 11.4); a schematic the player knows is never rolled. True when the
	 * player learns it now. Phase 0 stub (W7 replaces it): always false.
	 */
	public static boolean roll(ServerPlayer p, Identifier schematic, float chance, int pity) {
		return false;
	}

	private LegendaryDrops() {
	}
}
