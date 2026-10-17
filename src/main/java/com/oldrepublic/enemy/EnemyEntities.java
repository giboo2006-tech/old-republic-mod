package com.oldrepublic.enemy;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.enemy.trooper.FragGrenade;
import com.oldrepublic.enemy.trooper.TrooperEntity;

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

/**
 * Entity types, attributes, spawn placement and the blaster damage type of the enemy feature.
 * Troopers ({@code republic_trooper}, {@code sith_trooper}) have no spawn placement: they never
 * spawn naturally through vanilla (squads, officers and patrols are placed by code).
 */
public final class EnemyEntities {
	public static final ResourceKey<EntityType<?>> CRYSTAL_POACHER_KEY = ResourceKey.create(Registries.ENTITY_TYPE, OldRepublicMod.id("crystal_poacher"));
	public static final ResourceKey<EntityType<?>> BLASTER_BOLT_KEY = ResourceKey.create(Registries.ENTITY_TYPE, OldRepublicMod.id("blaster_bolt"));

	public static final ResourceKey<EntityType<?>> SITH_ACOLYTE_KEY = ResourceKey.create(Registries.ENTITY_TYPE, OldRepublicMod.id("sith_acolyte"));
	public static final ResourceKey<EntityType<?>> POACHER_CAPTAIN_KEY = ResourceKey.create(Registries.ENTITY_TYPE, OldRepublicMod.id("poacher_captain"));

	public static final ResourceKey<EntityType<?>> REPUBLIC_TROOPER_KEY = ResourceKey.create(Registries.ENTITY_TYPE, OldRepublicMod.id("republic_trooper"));
	public static final ResourceKey<EntityType<?>> SITH_TROOPER_KEY = ResourceKey.create(Registries.ENTITY_TYPE, OldRepublicMod.id("sith_trooper"));
	public static final ResourceKey<EntityType<?>> FRAG_GRENADE_KEY = ResourceKey.create(Registries.ENTITY_TYPE, OldRepublicMod.id("frag_grenade"));

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

	/** Encounter and command spawns only (no natural spawning). */
	public static final EntityType<SithAcolyte> SITH_ACOLYTE = Registry.register(BuiltInRegistries.ENTITY_TYPE, SITH_ACOLYTE_KEY,
			EntityType.Builder.of(SithAcolyte::new, MobCategory.MONSTER)
					.sized(0.6f, 1.95f)
					.eyeHeight(1.62f)
					.clientTrackingRange(8)
					.build(SITH_ACOLYTE_KEY));

	public static final EntityType<PoacherCaptain> POACHER_CAPTAIN = Registry.register(BuiltInRegistries.ENTITY_TYPE, POACHER_CAPTAIN_KEY,
			EntityType.Builder.of(PoacherCaptain::new, MobCategory.MONSTER)
					.sized(0.6f, 1.95f)
					.eyeHeight(1.62f)
					.clientTrackingRange(8)
					.build(POACHER_CAPTAIN_KEY));

	/** Republic troopers (spec 7.1): MISC, so not a monster; allowed in Peaceful. */
	public static final EntityType<TrooperEntity> REPUBLIC_TROOPER = Registry.register(BuiltInRegistries.ENTITY_TYPE, REPUBLIC_TROOPER_KEY,
			EntityType.Builder.<TrooperEntity>of(TrooperEntity::new, MobCategory.MISC)
					.sized(0.6f, 1.95f)
					.eyeHeight(1.62f)
					.clientTrackingRange(10)
					.build(REPUBLIC_TROOPER_KEY));

	/** Imperial troopers: the same class, Allegiance EMPIRE and Faction SITH_EMPIRE. */
	public static final EntityType<TrooperEntity> SITH_TROOPER = Registry.register(BuiltInRegistries.ENTITY_TYPE, SITH_TROOPER_KEY,
			EntityType.Builder.<TrooperEntity>of(TrooperEntity::new, MobCategory.MISC)
					.sized(0.6f, 1.95f)
					.eyeHeight(1.62f)
					.clientTrackingRange(10)
					.build(SITH_TROOPER_KEY));

	/** The grenadier's frag grenade (part B's entity). Never saved. */
	public static final EntityType<FragGrenade> FRAG_GRENADE = Registry.register(BuiltInRegistries.ENTITY_TYPE, FRAG_GRENADE_KEY,
			EntityType.Builder.<FragGrenade>of(FragGrenade::new, MobCategory.MISC)
					.sized(0.25f, 0.25f)
					.clientTrackingRange(8)
					.noSave()
					.build(FRAG_GRENADE_KEY));

	static void init() {
		FabricDefaultAttributeRegistry.register(CRYSTAL_POACHER, CrystalPoacher.createAttributes());
		FabricDefaultAttributeRegistry.register(SITH_ACOLYTE, SithAcolyte.createAttributes());
		FabricDefaultAttributeRegistry.register(POACHER_CAPTAIN, PoacherCaptain.createAttributes());
		FabricDefaultAttributeRegistry.register(REPUBLIC_TROOPER, TrooperEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(SITH_TROOPER, TrooperEntity.createAttributes());
		SpawnPlacements.register(CRYSTAL_POACHER, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, CrystalPoacher::checkPoacherSpawnRules);
	}

	private EnemyEntities() {
	}
}
