package com.oldrepublic.registry;

import java.util.List;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/**
 * Milestone 4 plain items that more than one feature names in recipes, schematics, loot, stock or
 * rewards, registered up front so every merge resolves them (spec MILESTONE_4.md 15.1). Each
 * owner (section 16.1) owns its item's art, name and behaviour; an item that needs its own class
 * is registered by its owner, and other features look it up by id.
 */
public final class M4Items {
	// Economy (W2, 7.7)
	/** One credit. */
	public static final Item CREDIT_CHIT = ModRegistry.item("credit_chit", new Item.Properties().stacksTo(99));
	/** A hundred credits. */
	public static final Item CREDIT_BUNDLE = ModRegistry.item("credit_bundle", new Item.Properties().stacksTo(64).rarity(Rarity.UNCOMMON));

	// Planet resources (W1, 3.2 and 3.3)
	public static final Item AURELITE_SHARD = ModRegistry.item("aurelite_shard", new Item.Properties());
	public static final Item DUSKGLASS_SHARD = ModRegistry.item("duskglass_shard", new Item.Properties());

	// Crafting materials (W5, 7.2 and 7.5)
	public static final Item ALLOY_PLATE = ModRegistry.item("alloy_plate", new Item.Properties());
	public static final Item MASTERWORK_FRAGMENT = ModRegistry.item("masterwork_fragment", new Item.Properties().rarity(Rarity.RARE));

	// Ship parts and upgrades (W3, 4.2, 4.3 and 4.7)
	public static final Item POWER_CORE = ModRegistry.item("power_core", new Item.Properties().stacksTo(16));
	public static final Item HYPERDRIVE_MOTIVATOR = ModRegistry.item("hyperdrive_motivator", new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));
	public static final Item NAV_COMPUTER = ModRegistry.item("nav_computer", new Item.Properties().stacksTo(1));
	public static final Item HYPERFUEL_CELL = ModRegistry.item("hyperfuel_cell", new Item.Properties().stacksTo(16));
	public static final Item PASSENGER_SEAT = ModRegistry.item("passenger_seat", new Item.Properties().stacksTo(1));
	public static final Item CARGO_BAY_EXTENSION = ModRegistry.item("cargo_bay_extension", new Item.Properties().stacksTo(1));
	public static final Item REINFORCED_PLATING = ModRegistry.item("reinforced_plating", new Item.Properties().stacksTo(1));
	public static final Item FUEL_TANK_EXTENSION = ModRegistry.item("fuel_tank_extension", new Item.Properties().stacksTo(1));

	// Mods (W4 effects, W5 schematics, 6.3)
	public static final Item MOD_PLATING = mod("mod_plating");
	public static final Item MOD_WEAVE = mod("mod_weave");
	public static final Item MOD_ATTUNEMENT = mod("mod_attunement");
	public static final Item MOD_CONDUIT = mod("mod_conduit");
	public static final Item MOD_STABILISER = mod("mod_stabiliser");
	public static final Item JET_PACK = ModRegistry.item("jet_pack", new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
	public static final Item MOD_FOCUSED_BARREL = mod("mod_focused_barrel");
	public static final Item MOD_HEAVY_BARREL = mod("mod_heavy_barrel");
	public static final Item MOD_COOLING_CELL = mod("mod_cooling_cell");
	public static final Item MOD_OVERCHARGED_CELL = mod("mod_overcharged_cell");
	public static final Item MOD_KEEN_EDGE = mod("mod_keen_edge");
	public static final Item MOD_SHOCK_EDGE = mod("mod_shock_edge");

	/** Every mod item, in the 6.3 table's order. */
	public static final List<Item> MODS = List.of(MOD_PLATING, MOD_WEAVE, MOD_ATTUNEMENT, MOD_CONDUIT, MOD_STABILISER, JET_PACK,
			MOD_FOCUSED_BARREL, MOD_HEAVY_BARREL, MOD_COOLING_CELL, MOD_OVERCHARGED_CELL, MOD_KEEN_EDGE, MOD_SHOCK_EDGE);

	// Creature and droid drops (W6, 10.2 to 10.4)
	public static final Item HOLLOWHORN_HIDE = ModRegistry.item("hollowhorn_hide", new Item.Properties());
	public static final Item HOLLOWHORN_HORN = ModRegistry.item("hollowhorn_horn", new Item.Properties());
	public static final Item ASH_HOUND_FANG = ModRegistry.item("ash_hound_fang", new Item.Properties());
	public static final Item DROID_ACTUATOR = ModRegistry.item("droid_actuator", new Item.Properties());

	private static Item mod(String name) {
		return ModRegistry.item(name, new Item.Properties().stacksTo(16));
	}

	/** Loads the class so the items above register; called once from {@link M4Contracts#init}. */
	static void init() {
	}

	private M4Items() {
	}
}
