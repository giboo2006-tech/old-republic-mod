package com.oldrepublic.registry;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/**
 * Milestone 5 plain items that more than one feature names in recipes, schematics, loot, stock or
 * rewards, registered up front so every merge resolves them (spec MILESTONE_5.md 17.1 and 18.1).
 * Each owner owns its item's art, name and behaviour; an item that needs its own class is
 * registered by its owner, and other features look it up by id.
 */
public final class M5Items {
	// Planet resources (W3, 3.2 and 3.3)
	public static final Item SUNSTONE = ModRegistry.item("sunstone", new Item.Properties());
	public static final Item STORMGLASS_SHARD = ModRegistry.item("stormglass_shard", new Item.Properties());

	// Legendary materials (W7, 11.3)
	public static final Item HYMNSTONE_SHARD = ModRegistry.item("hymnstone_shard", new Item.Properties().rarity(Rarity.RARE));
	public static final Item CLAN_IRON = ModRegistry.item("clan_iron", new Item.Properties().rarity(Rarity.UNCOMMON));

	// Space (W2, 5.3 and 5.9)
	/** One missile for the ship's rack, fired from the hold. */
	public static final Item CONCUSSION_MISSILE = ModRegistry.item("concussion_missile", new Item.Properties().stacksTo(16));
	/** The kajidic's cargo for a {@code space_run}; never stacks, so each run carries its own. */
	public static final Item SEALED_CARGO = ModRegistry.item("sealed_cargo", new Item.Properties().stacksTo(1));

	/** Loads the class so the items above register; called once from {@link M5Contracts#init}. */
	static void init() {
	}

	private M5Items() {
	}
}
