package com.oldrepublic.enemy.construct;

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
 * The Ruin Sentinel's entity type (2.3 blocks, persistent, kept in Peaceful: no
 * {@code notInPeaceful}), its attributes and its spawn egg. Sites, missions and the boss spawn
 * sentinels through {@link Sentinels}; nothing spawns naturally.
 */
public final class SentinelEntities {
	public static final ResourceKey<EntityType<?>> RUIN_SENTINEL_KEY = ResourceKey.create(Registries.ENTITY_TYPE, OldRepublicMod.id("ruin_sentinel"));

	public static final EntityType<RuinSentinel> RUIN_SENTINEL = Registry.register(BuiltInRegistries.ENTITY_TYPE, RUIN_SENTINEL_KEY,
			EntityType.Builder.of(RuinSentinel::new, MobCategory.MONSTER)
					.sized(1.1f, 2.3f)
					.eyeHeight(2.0f)
					.clientTrackingRange(10)
					.build(RUIN_SENTINEL_KEY));

	/** Testing aid: spawns a dormant Watcher (use the command for a Tomb sentinel or a bound one). */
	public static final Item RUIN_SENTINEL_SPAWN_EGG = ModRegistry.item("ruin_sentinel_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(RUIN_SENTINEL));

	static void init() {
		FabricDefaultAttributeRegistry.register(RUIN_SENTINEL, RuinSentinel.createAttributes());
	}

	private SentinelEntities() {
	}
}
