package com.oldrepublic.enemy;

import com.oldrepublic.equipment.blaster.BlasterItem;
import com.oldrepublic.equipment.melee.VibroWeaponItem;
import com.oldrepublic.registry.ModRegistry;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;

/** Items of the enemy feature. */
public final class EnemyItems {
	/**
	 * The poacher's weapon, rendered in its hands. Milestone 4 makes it the player's rifle too: the
	 * class is the equipment feature's {@link BlasterItem} (spec MILESTONE_4.md 5.1).
	 */
	public static final BlasterItem BLASTER_RIFLE = ModRegistry.item("blaster_rifle", p -> new BlasterItem(BlasterItem.Kind.RIFLE, p),
			new Item.Properties().stacksTo(1));

	/** Testing aid: spawns a Crystal Poacher. */
	public static final SpawnEggItem CRYSTAL_POACHER_SPAWN_EGG = ModRegistry.item("crystal_poacher_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(EnemyEntities.CRYSTAL_POACHER));

	/** The Poacher Captain's vibroblade; in Milestone 4 also the player's ({@link VibroWeaponItem}, spec 5.2). */
	public static final VibroWeaponItem VIBROBLADE = ModRegistry.item("vibroblade", VibroWeaponItem::new, new Item.Properties().stacksTo(1));
	/** The Poacher Captain's sidearm; in Milestone 4 also the player's ({@link BlasterItem}, spec 5.1). */
	public static final BlasterItem BLASTER_PISTOL = ModRegistry.item("blaster_pistol", p -> new BlasterItem(BlasterItem.Kind.PISTOL, p),
			new Item.Properties().stacksTo(1));

	/** Testing aid: spawns a hostile Sith Acolyte. */
	public static final SpawnEggItem SITH_ACOLYTE_SPAWN_EGG = ModRegistry.item("sith_acolyte_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(EnemyEntities.SITH_ACOLYTE));
	/** Testing aid: spawns a Poacher Captain. */
	public static final SpawnEggItem POACHER_CAPTAIN_SPAWN_EGG = ModRegistry.item("poacher_captain_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(EnemyEntities.POACHER_CAPTAIN));

	/** Testing aid: spawns a Republic trooper with a role drawn from the patrol composition. */
	public static final SpawnEggItem REPUBLIC_TROOPER_SPAWN_EGG = ModRegistry.item("republic_trooper_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(EnemyEntities.REPUBLIC_TROOPER));
	/** Testing aid: spawns an Imperial trooper with a role drawn from the patrol composition. */
	public static final SpawnEggItem SITH_TROOPER_SPAWN_EGG = ModRegistry.item("sith_trooper_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(EnemyEntities.SITH_TROOPER));

	static void init() {
	}

	private EnemyItems() {
	}
}
