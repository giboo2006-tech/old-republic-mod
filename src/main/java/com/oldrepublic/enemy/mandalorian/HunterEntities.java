package com.oldrepublic.enemy.mandalorian;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.registry.ModRegistry;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;

/**
 * The hunters' entity types (spec MILESTONE_4.md 16.1): {@code mandalorian_hunter} (story ambushes,
 * bounties and Kaast's call; no natural spawning), and the never-saved {@code hunter_rocket} and
 * {@code hunter_net}.
 */
public final class HunterEntities {
	public static final ResourceKey<EntityType<?>> MANDALORIAN_HUNTER_KEY = key("mandalorian_hunter");
	public static final ResourceKey<EntityType<?>> HUNTER_ROCKET_KEY = key("hunter_rocket");
	public static final ResourceKey<EntityType<?>> HUNTER_NET_KEY = key("hunter_net");

	public static final EntityType<MandalorianHunter> MANDALORIAN_HUNTER = Registry.register(BuiltInRegistries.ENTITY_TYPE, MANDALORIAN_HUNTER_KEY,
			EntityType.Builder.<MandalorianHunter>of(MandalorianHunter::new, MobCategory.MONSTER)
					.sized(0.6f, 1.95f)
					.eyeHeight(1.62f)
					.clientTrackingRange(10)
					.build(MANDALORIAN_HUNTER_KEY));

	public static final EntityType<HunterRocket> HUNTER_ROCKET = Registry.register(BuiltInRegistries.ENTITY_TYPE, HUNTER_ROCKET_KEY,
			EntityType.Builder.<HunterRocket>of(HunterRocket::new, MobCategory.MISC)
					.sized(0.3f, 0.3f)
					.clientTrackingRange(8)
					.updateInterval(1)
					.noSave()
					.build(HUNTER_ROCKET_KEY));

	public static final EntityType<HunterNet> HUNTER_NET = Registry.register(BuiltInRegistries.ENTITY_TYPE, HUNTER_NET_KEY,
			EntityType.Builder.<HunterNet>of(HunterNet::new, MobCategory.MISC)
					.sized(1.0f, 1.9f)
					.clientTrackingRange(8)
					.updateInterval(1)
					.noSave()
					.build(HUNTER_NET_KEY));

	/** Testing aid: spawns a lone Mandalorian hunter (no owner, no ambush). */
	public static final SpawnEggItem MANDALORIAN_HUNTER_SPAWN_EGG = ModRegistry.item("mandalorian_hunter_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(MANDALORIAN_HUNTER));

	private static ResourceKey<EntityType<?>> key(String path) {
		return ResourceKey.create(Registries.ENTITY_TYPE, OldRepublicMod.id(path));
	}

	/** Registers the hunter's attributes (loading the class registers the types and the egg). Called from {@code FoesFeature}. */
	public static void init() {
		FabricDefaultAttributeRegistry.register(MANDALORIAN_HUNTER, MandalorianHunter.createAttributes());
	}

	private HunterEntities() {
	}
}
