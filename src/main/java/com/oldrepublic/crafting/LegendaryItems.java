package com.oldrepublic.crafting;

import com.oldrepublic.crafting.item.LegendaryShipPartItem;
import com.oldrepublic.registry.ModRegistry;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/**
 * The two legendary ship parts (spec MILESTONE_5.md 11.2), made by their {@code common} legendary
 * schematics at an engineering bench and fitted by the Saltgate shipwright (W1's {@code ship:fit},
 * which looks them up by id and sets {@code ShipRecord.PART_SPINDLE_CAPACITOR} or
 * {@code PART_HYMN_DRIVE}). The legendary mods are in {@code equipment.mod.LegendaryMods}, the saber
 * parts and the lens in {@code saber.SaberItems}. Registered from {@code SaberM5Feature} at mod init.
 */
public final class LegendaryItems {
	/** Shields +20 and recharge x1.5 while the ship has a shield generator (W2, 5.4). */
	public static final LegendaryShipPartItem SPINDLE_CAPACITOR = shipPart("spindle_capacitor");
	/** Jumps cost no hull wear and the Hyperspace phase is half as long (W1, 4.7). */
	public static final LegendaryShipPartItem HYMN_TUNED_DRIVE = shipPart("hymn_tuned_drive");

	private static LegendaryShipPartItem shipPart(String id) {
		return ModRegistry.item(id, p -> new LegendaryShipPartItem(p, id), new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());
	}

	/** Loads the class so the items register (mod init). */
	public static void init() {
	}

	private LegendaryItems() {
	}
}
