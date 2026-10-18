package com.oldrepublic.vendor;

import java.util.Optional;

import com.oldrepublic.crafting.Discipline;
import com.oldrepublic.identity.Faction;

import net.minecraft.world.item.ItemStack;

/**
 * One line of a vendor's stock (spec MILESTONE_4.md 7.8), from {@code data/oldrepublic/vendor/<id>.json}.
 * Phase 0 freezes the record; W2 writes the loader, its codec and the screen.
 *
 * @param price in credits, before the faction discount and {@code vendors.priceMultiplier}
 * @param faction the faction whose tier gates it (empty: the vendor's own, or none)
 * @param tier the minimum faction tier ordinal
 * @param discipline the discipline a trainer schematic needs
 * @param level that discipline's level
 * @param dailyLimit purchases per player per day; 0 for no limit
 */
public record VendorEntry(ItemStack item, int price, Optional<Faction> faction, int tier, Optional<Discipline> discipline, int level, int dailyLimit) {
	public VendorEntry {
		item = item.copy();
		price = Math.max(0, price);
		dailyLimit = Math.max(0, dailyLimit);
	}

	/** The item, copied. */
	public ItemStack stack() {
		return item.copy();
	}
}
