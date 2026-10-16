package com.oldrepublic.enemy;

import com.oldrepublic.registry.ModRegistry;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;

/** Items of the enemy feature. */
public final class EnemyItems {
	/** The poacher's weapon. Rendered in its hands; not usable by players in Milestone 1. */
	public static final Item BLASTER_RIFLE = ModRegistry.item("blaster_rifle", new Item.Properties().stacksTo(1));

	/** Testing aid: spawns a Crystal Poacher. */
	public static final SpawnEggItem CRYSTAL_POACHER_SPAWN_EGG = ModRegistry.item("crystal_poacher_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(EnemyEntities.CRYSTAL_POACHER));

	/** The Poacher Captain's vibroblade. Render-only: not obtainable in survival. */
	public static final Item VIBROBLADE = ModRegistry.item("vibroblade", new Item.Properties().stacksTo(1));
	/** The Poacher Captain's sidearm. Render-only: not obtainable in survival. */
	public static final Item BLASTER_PISTOL = ModRegistry.item("blaster_pistol", new Item.Properties().stacksTo(1));

	/** Testing aid: spawns a hostile Sith Acolyte. */
	public static final SpawnEggItem SITH_ACOLYTE_SPAWN_EGG = ModRegistry.item("sith_acolyte_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(EnemyEntities.SITH_ACOLYTE));
	/** Testing aid: spawns a Poacher Captain. */
	public static final SpawnEggItem POACHER_CAPTAIN_SPAWN_EGG = ModRegistry.item("poacher_captain_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(EnemyEntities.POACHER_CAPTAIN));

	static void init() {
	}

	private EnemyItems() {
	}
}
