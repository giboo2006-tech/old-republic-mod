package com.oldrepublic.equipment;

import com.oldrepublic.crafting.Quality;
import com.oldrepublic.equipment.blaster.BlasterItem;
import com.oldrepublic.equipment.melee.ElectrostaffItem;
import com.oldrepublic.equipment.melee.VibroWeaponItem;
import com.oldrepublic.registry.M4Components;
import com.oldrepublic.registry.ModRegistry;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/**
 * The Milestone 4 weapons W4 registers (spec MILESTONE_4.md 5 and 16.1): {@code heavy_repeater},
 * {@code vibrosword}, {@code electrostaff} and Kaast's named rifle {@code kaast_longshot}. The reused
 * {@code blaster_pistol}, {@code blaster_rifle} and {@code vibroblade} stay registered in the lead's
 * {@code enemy/EnemyItems} with W4's classes. Each class completes the properties (damage,
 * durability, attributes) from the registration defaults.
 */
public final class GearItems {
	public static final BlasterItem HEAVY_REPEATER = ModRegistry.item("heavy_repeater",
			p -> new BlasterItem(BlasterItem.Kind.REPEATER, p), new Item.Properties().stacksTo(1));
	public static final VibroWeaponItem VIBROSWORD = ModRegistry.item("vibrosword",
			p -> new VibroWeaponItem(VibroWeaponItem.Kind.SWORD, p), new Item.Properties().stacksTo(1));
	public static final ElectrostaffItem ELECTROSTAFF = ModRegistry.item("electrostaff", ElectrostaffItem::new, new Item.Properties().stacksTo(1));
	/**
	 * Kaast's Longshot (8.6 "End him"): a blaster rifle built at Masterwork with a fixed heavy barrel
	 * ({@code Mods.fittedQuality} counts it at the rifle's quality), named, so it cannot be salvaged.
	 */
	public static final BlasterItem KAAST_LONGSHOT = ModRegistry.item("kaast_longshot",
			p -> new BlasterItem(BlasterItem.Kind.RIFLE, p, Quality.MASTERWORK),
			new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)
					.component(M4Components.QUALITY, Quality.MASTERWORK.ordinal())
					.component(M4Components.NAMED, "kaast_longshot"));

	/** Loads the class, registering every item. */
	public static void init() {
	}

	private GearItems() {
	}
}
