package com.oldrepublic.enemy.stormback;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.registry.ModRegistry;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The stormback's entity type (spec MILESTONE_5.md 12.1, 18.1): {@code stormback}, a creature of
 * Tyrvann's basalt and stormwood (a neutral grazer, like the M4 hollowhorn). Never in Peaceful.
 */
public final class StormbackEntities {
	/** The entity id, also the {@code onFoeKilled} kind. */
	public static final String ID = "stormback";
	public static final ResourceKey<EntityType<?>> STORMBACK_KEY = ResourceKey.create(Registries.ENTITY_TYPE, OldRepublicMod.id("stormback"));

	public static final EntityType<Stormback> STORMBACK = Registry.register(BuiltInRegistries.ENTITY_TYPE, STORMBACK_KEY,
			EntityType.Builder.<Stormback>of(Stormback::new, MobCategory.CREATURE)
					.sized(1.6f, 1.7f)
					.eyeHeight(1.35f)
					.notInPeaceful()
					.clientTrackingRange(10)
					.build(STORMBACK_KEY));

	/** Testing aid: a grazing stormback. */
	public static final SpawnEggItem STORMBACK_SPAWN_EGG = ModRegistry.item("stormback_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(STORMBACK));

	/** Registers the attributes and the spawn placement. Called from {@code FoesM5Feature}. */
	public static void init() {
		FabricDefaultAttributeRegistry.register(STORMBACK, Stormback.createAttributes());
		SpawnPlacements.register(STORMBACK, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Stormbacks::checkSpawnRules);
	}

	private StormbackEntities() {
	}
}
