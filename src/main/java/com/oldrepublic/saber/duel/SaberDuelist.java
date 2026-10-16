package com.oldrepublic.saber.duel;

import net.minecraft.world.item.ItemStack;

/**
 * An NPC that fights with a lightsaber: it guards, parries and attacks in strings, and the player's
 * saber combat treats its blade like a player's.
 */
public interface SaberDuelist {
	GuardState guardState();

	/** The lightsaber it holds (built with {@code SaberAssembly}). */
	ItemStack duelSaber();
}
