package com.oldrepublic.equipment.mod;

import java.util.List;

import com.oldrepublic.registry.ModRegistry;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/**
 * The four legendary mods (spec MILESTONE_5.md 11.2), each made only by its {@code _legendary}
 * schematic and so always Legendary: Clan-Iron Plating and Hymnweave Lining fit the armour slot,
 * the Storm Cell a blaster's cell slot and the Sunstone Edge a vibroweapon's edge. They fit and come
 * out through the M4 rules ({@link Mods}); their numbers are fixed (the {@code crafting} balance
 * section's, not per quality) and read by {@link ModEffects}. Registered from
 * {@code SaberM5Feature} at mod init.
 */
public final class LegendaryMods {
	/** Armour mod: +3 armour, 10% less blaster damage taken. */
	public static final Item CLAN_IRON_PLATING = mod("mod_clan_iron_plating");
	/** Armour mod: +6 maximum Force Energy, 10% faster regeneration. */
	public static final Item HYMNWEAVE_LINING = mod("mod_hymnweave_lining");
	/** Blaster cell: each hit arcs to one more enemy within 4 blocks for 30% of the damage. */
	public static final Item STORM_CELL = mod("mod_storm_cell");
	/** Vibroweapon edge: +12% damage, and the target burns for 40 ticks. */
	public static final Item SUNSTONE_EDGE = mod("mod_sunstone_edge");

	/** Every legendary mod, in the 11.2 table's order. */
	public static final List<Item> ALL = List.of(CLAN_IRON_PLATING, HYMNWEAVE_LINING, STORM_CELL, SUNSTONE_EDGE);

	private static Item mod(String name) {
		return ModRegistry.item(name, new Item.Properties().stacksTo(16).rarity(Rarity.EPIC));
	}

	/** Loads the class so the items register (mod init). */
	public static void init() {
	}

	private LegendaryMods() {
	}
}
