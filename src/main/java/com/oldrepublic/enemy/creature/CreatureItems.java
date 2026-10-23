package com.oldrepublic.enemy.creature;

import com.oldrepublic.registry.ModRegistry;

import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;

/**
 * The beasts' items (spec MILESTONE_4.md 10.3, 16): hollowhorn meat (raw drops, cooked by smelting,
 * smoking or a campfire: food 7) and the two spawn eggs. The hide, horn and fang are the lead's
 * {@code M4Items}.
 */
public final class CreatureItems {
	public static final Item RAW_HOLLOWHORN = ModRegistry.item("raw_hollowhorn",
			new Item.Properties().food(new FoodProperties.Builder().nutrition(3).saturationModifier(0.3f).build()));
	public static final Item COOKED_HOLLOWHORN = ModRegistry.item("cooked_hollowhorn",
			new Item.Properties().food(new FoodProperties.Builder().nutrition(7).saturationModifier(0.8f).build()));

	/** Testing aid: spawns a hollowhorn (a lone grown animal). */
	public static final SpawnEggItem HOLLOWHORN_SPAWN_EGG = ModRegistry.item("hollowhorn_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(CreatureEntities.HOLLOWHORN));
	/** Testing aid: spawns an ash hound. */
	public static final SpawnEggItem ASH_HOUND_SPAWN_EGG = ModRegistry.item("ash_hound_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(CreatureEntities.ASH_HOUND));

	/** Loads the class, registering the items. Called from {@code FoesFeature}. */
	public static void init() {
	}

	private CreatureItems() {
	}
}
