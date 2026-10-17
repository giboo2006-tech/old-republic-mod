package com.oldrepublic.registry;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/**
 * Milestone 3 items that other features' recipes, loot and rewards name, registered up front so
 * every merge resolves them (spec section 15.1). Each owner owns its item's art, name and
 * behaviour: the materials belong to the world feature, the commendations to the mission feature,
 * the shard to the holocron feature and the singing crystal to the saber feature. The Archivist
 * pommel and Reliquary emitter are hilt parts and register with the other parts in
 * {@code SaberItems}.
 */
public final class M3Items {
	/** Plate from wreck salvage; crafts and repairs trooper armour. */
	public static final Item DURASTEEL_PLATE = ModRegistry.item("durasteel_plate", new Item.Properties());
	/** Thread from the lumen moss of Jedi ruins; crafts and repairs Jedi robes. */
	public static final Item LUMEN_THREAD = ModRegistry.item("lumen_thread", new Item.Properties());
	/** Silk from the funerary urns of Sith tombs; crafts and repairs Sith robes. */
	public static final Item GRAVE_SILK = ModRegistry.item("grave_silk", new Item.Properties());
	/** Paid for Republic faction work; buys Republic trooper armour. */
	public static final Item REPUBLIC_COMMENDATION = ModRegistry.item("republic_commendation", new Item.Properties().rarity(Rarity.UNCOMMON));
	/** Paid for Imperial faction work; buys Sith trooper armour. */
	public static final Item IMPERIAL_COMMENDATION = ModRegistry.item("imperial_commendation", new Item.Properties().rarity(Rarity.UNCOMMON));
	/** A sliver of crystal bound in brass, given by a holocron echo. */
	public static final Item HOLOCRON_SHARD = ModRegistry.item("holocron_shard", new Item.Properties().rarity(Rarity.UNCOMMON));
	/** The amber kyber crystal the Collector leaves behind; a blade built with it sings. */
	public static final Item SINGING_CRYSTAL = ModRegistry.item("singing_crystal", new Item.Properties().stacksTo(1).rarity(Rarity.RARE));

	/** Loads the class so the items above register; called once from OldRepublicMod. */
	public static void init() {
	}

	private M3Items() {
	}
}
