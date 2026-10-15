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

	static void init() {
	}

	private EnemyItems() {
	}
}
