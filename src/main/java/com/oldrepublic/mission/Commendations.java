package com.oldrepublic.mission;

import com.oldrepublic.identity.Faction;
import com.oldrepublic.registry.M3Items;

import net.minecraft.world.item.ItemStack;

/** The armies' commendations (spec section 5.6), the currency of their quartermasters. */
public final class Commendations {
	/**
	 * A stack of {@code count} commendations of {@code army}: {@link Faction#REPUBLIC} or
	 * {@link Faction#SITH_EMPIRE}; any other faction gives an empty stack.
	 */
	public static ItemStack of(Faction army, int count) {
		return switch (army) {
			case REPUBLIC -> new ItemStack(M3Items.REPUBLIC_COMMENDATION, count);
			case SITH_EMPIRE -> new ItemStack(M3Items.IMPERIAL_COMMENDATION, count);
			default -> ItemStack.EMPTY;
		};
	}

	private Commendations() {
	}
}
