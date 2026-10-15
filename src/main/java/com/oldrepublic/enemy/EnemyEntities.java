package com.oldrepublic.enemy;

import com.oldrepublic.OldRepublicMod;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.levelgen.Heightmap;

/** Entity types, attributes, spawn placement and the blaster damage type of the enemy feature. */
public final class EnemyEntities {
	public static final ResourceKey<EntityType<?>> CRYSTAL_POACHER_KEY = ResourceKey.create(Registries.ENTITY_TYPE, OldRepublicMod.id("crystal_poacher"));
	public static final ResourceKey<EntityType<?>> BLASTER_BOLT_KEY = ResourceKey.create(Registries.ENTITY_TYPE, OldRepublicMod.id("blaster_bolt"));

	/** {@code data/oldrepublic/damage_type/blaster.json}. */
	public static final ResourceKey<DamageType> BLASTER_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, OldRepublicMod.id("blaster"));

	public static final EntityType<CrystalPoacher> CRYSTAL_POACHER = Registry.register(BuiltInRegistries.ENTITY_TYPE, CRYSTAL_POACHER_KEY,
			EntityType.Builder.of(CrystalPoacher::new, MobCategory.MONSTER)
					.sized(0.6f, 1.95f)
					.eyeHeight(1.62f)
					.clientTrackingRange(8)
					.build(CRYSTAL_POACHER_KEY));

	public static final EntityType<BlasterBolt> BLASTER_BOLT = Registry.register(BuiltInRegistries.ENTITY_TYPE, BLASTER_BOLT_KEY,
			EntityType.Builder.<BlasterBolt>of(BlasterBolt::new, MobCategory.MISC)
					.sized(0.25f, 0.25f)
					.clientTrackingRange(6)
					.updateInterval(1)
					.alwaysUpdateVelocity(true)
					.noSave()
					.build(BLASTER_BOLT_KEY));

	static void init() {
		FabricDefaultAttributeRegistry.register(CRYSTAL_POACHER, CrystalPoacher.createAttributes());
		SpawnPlacements.register(CRYSTAL_POACHER, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, CrystalPoacher::checkPoacherSpawnRules);
	}

	private EnemyEntities() {
	}
}
