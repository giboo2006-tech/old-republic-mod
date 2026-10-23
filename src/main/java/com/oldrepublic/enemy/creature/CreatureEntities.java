package com.oldrepublic.enemy.creature;

import com.oldrepublic.OldRepublicMod;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The planet beasts' entity types (spec MILESTONE_4.md 16.1): {@code hollowhorn} (a creature of
 * Selvaren) and {@code ash_hound} (a fire-immune monster of Varkhast).
 */
public final class CreatureEntities {
	public static final ResourceKey<EntityType<?>> HOLLOWHORN_KEY = ResourceKey.create(Registries.ENTITY_TYPE, OldRepublicMod.id("hollowhorn"));
	public static final ResourceKey<EntityType<?>> ASH_HOUND_KEY = ResourceKey.create(Registries.ENTITY_TYPE, OldRepublicMod.id("ash_hound"));

	public static final EntityType<Hollowhorn> HOLLOWHORN = Registry.register(BuiltInRegistries.ENTITY_TYPE, HOLLOWHORN_KEY,
			EntityType.Builder.<Hollowhorn>of(Hollowhorn::new, MobCategory.CREATURE)
					.sized(1.3f, 1.6f)
					.eyeHeight(1.45f)
					.clientTrackingRange(10)
					.build(HOLLOWHORN_KEY));

	public static final EntityType<AshHound> ASH_HOUND = Registry.register(BuiltInRegistries.ENTITY_TYPE, ASH_HOUND_KEY,
			EntityType.Builder.<AshHound>of(AshHound::new, MobCategory.MONSTER)
					.sized(0.7f, 0.85f)
					.eyeHeight(0.7f)
					.fireImmune()
					.clientTrackingRange(10)
					.build(ASH_HOUND_KEY));

	/** Registers the attributes and the spawn placements. Called from {@code FoesFeature}. */
	public static void init() {
		FabricDefaultAttributeRegistry.register(HOLLOWHORN, Hollowhorn.createAttributes());
		FabricDefaultAttributeRegistry.register(ASH_HOUND, AshHound.createAttributes());
		SpawnPlacements.register(HOLLOWHORN, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, CreatureSpawns::checkHollowhornSpawnRules);
		SpawnPlacements.register(ASH_HOUND, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, CreatureSpawns::checkAshHoundSpawnRules);
	}

	private CreatureEntities() {
	}
}
