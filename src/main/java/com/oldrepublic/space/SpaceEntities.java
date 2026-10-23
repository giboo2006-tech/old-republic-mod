package com.oldrepublic.space;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.registry.ModRegistry;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;

/**
 * Space's entity types (spec MILESTONE_5.md 5.5, 18.1): the three starfighters, the blockade gunship
 * and its shield generator, the two ship projectiles and the nav buoy, none of them saved (encounter
 * sessions rebuild their fighters, 14.1), plus the fighters' spawn eggs (testing aids), the
 * {@code ship_weapon} damage type and the {@code #oldrepublic:ship_targets} tag. Every ship is in the
 * {@code MISC} category, so Peaceful never removes the blockade's scripted pass.
 */
public final class SpaceEntities {
	public static final ResourceKey<EntityType<?>> HYMN_INTERCEPTOR_KEY = key("hymn_interceptor");
	public static final ResourceKey<EntityType<?>> KAJIDIC_RAIDER_KEY = key("kajidic_raider");
	public static final ResourceKey<EntityType<?>> HASK_FIGHTER_KEY = key("hask_fighter");
	public static final ResourceKey<EntityType<?>> BLOCKADE_GUNSHIP_KEY = key("blockade_gunship");
	public static final ResourceKey<EntityType<?>> GUNSHIP_GENERATOR_KEY = key("gunship_generator");
	public static final ResourceKey<EntityType<?>> SHIP_BOLT_KEY = key("ship_bolt");
	public static final ResourceKey<EntityType<?>> SHIP_MISSILE_KEY = key("ship_missile");
	public static final ResourceKey<EntityType<?>> NAV_BUOY_KEY = key("nav_buoy");

	public static final EntityType<HymnInterceptor> HYMN_INTERCEPTOR = Registry.register(BuiltInRegistries.ENTITY_TYPE, HYMN_INTERCEPTOR_KEY,
			EntityType.Builder.<HymnInterceptor>of(HymnInterceptor::new, MobCategory.MISC)
					.sized(3.0f, 1.2f)
					.eyeHeight(0.6f)
					.fireImmune()
					.noSave()
					.clientTrackingRange(12)
					.updateInterval(2)
					.build(HYMN_INTERCEPTOR_KEY));

	public static final EntityType<KajidicRaider> KAJIDIC_RAIDER = Registry.register(BuiltInRegistries.ENTITY_TYPE, KAJIDIC_RAIDER_KEY,
			EntityType.Builder.<KajidicRaider>of(KajidicRaider::new, MobCategory.MISC)
					.sized(3.4f, 1.6f)
					.eyeHeight(0.8f)
					.fireImmune()
					.noSave()
					.clientTrackingRange(12)
					.updateInterval(2)
					.build(KAJIDIC_RAIDER_KEY));

	public static final EntityType<HaskFighter> HASK_FIGHTER = Registry.register(BuiltInRegistries.ENTITY_TYPE, HASK_FIGHTER_KEY,
			EntityType.Builder.<HaskFighter>of(HaskFighter::new, MobCategory.MISC)
					.sized(3.0f, 1.3f)
					.eyeHeight(0.65f)
					.fireImmune()
					.noSave()
					.clientTrackingRange(12)
					.updateInterval(2)
					.build(HASK_FIGHTER_KEY));

	/** The gunship's box is as long as the hull; bolts and missiles test the hull's own turned box instead ({@link BlockadeGunship#hullHit}). */
	public static final EntityType<BlockadeGunship> BLOCKADE_GUNSHIP = Registry.register(BuiltInRegistries.ENTITY_TYPE, BLOCKADE_GUNSHIP_KEY,
			EntityType.Builder.<BlockadeGunship>of(BlockadeGunship::new, MobCategory.MISC)
					.sized(24.0f, 7.0f)
					.eyeHeight(3.5f)
					.fireImmune()
					.noSave()
					.clientTrackingRange(16)
					.updateInterval(2)
					.build(BLOCKADE_GUNSHIP_KEY));

	public static final EntityType<GunshipGenerator> GUNSHIP_GENERATOR = Registry.register(BuiltInRegistries.ENTITY_TYPE, GUNSHIP_GENERATOR_KEY,
			EntityType.Builder.<GunshipGenerator>of(GunshipGenerator::new, MobCategory.MISC)
					.sized(2.6f, 2.4f)
					.eyeHeight(1.2f)
					.fireImmune()
					.noSave()
					.clientTrackingRange(16)
					.updateInterval(1)
					.build(GUNSHIP_GENERATOR_KEY));

	public static final EntityType<ShipBolt> SHIP_BOLT = Registry.register(BuiltInRegistries.ENTITY_TYPE, SHIP_BOLT_KEY,
			EntityType.Builder.<ShipBolt>of(ShipBolt::new, MobCategory.MISC)
					.sized(0.3f, 0.3f)
					.noSave()
					.clientTrackingRange(10)
					.updateInterval(5)
					.build(SHIP_BOLT_KEY));

	public static final EntityType<ShipMissile> SHIP_MISSILE = Registry.register(BuiltInRegistries.ENTITY_TYPE, SHIP_MISSILE_KEY,
			EntityType.Builder.<ShipMissile>of(ShipMissile::new, MobCategory.MISC)
					.sized(0.5f, 0.5f)
					.noSave()
					.clientTrackingRange(10)
					.updateInterval(1)
					.build(SHIP_MISSILE_KEY));

	public static final EntityType<NavBuoy> NAV_BUOY = Registry.register(BuiltInRegistries.ENTITY_TYPE, NAV_BUOY_KEY,
			EntityType.Builder.<NavBuoy>of(NavBuoy::new, MobCategory.MISC)
					.sized(1.2f, 2.4f)
					.fireImmune()
					.noSave()
					.clientTrackingRange(16)
					.updateInterval(20)
					.build(NAV_BUOY_KEY));

	/** Testing aids: a lone fighter, hostile, in no encounter. */
	public static final SpawnEggItem HYMN_INTERCEPTOR_SPAWN_EGG = ModRegistry.item("hymn_interceptor_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(HYMN_INTERCEPTOR));
	public static final SpawnEggItem KAJIDIC_RAIDER_SPAWN_EGG = ModRegistry.item("kajidic_raider_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(KAJIDIC_RAIDER));
	public static final SpawnEggItem HASK_FIGHTER_SPAWN_EGG = ModRegistry.item("hask_fighter_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(HASK_FIGHTER));

	/** The only damage ship weapons deal; nothing else hurts a ship target. */
	public static final ResourceKey<DamageType> SHIP_WEAPON = ResourceKey.create(Registries.DAMAGE_TYPE, OldRepublicMod.id("ship_weapon"));
	/** What ship weapons may hurt (5.3): the starfighters, the gunship and its generator. */
	public static final TagKey<EntityType<?>> SHIP_TARGETS = TagKey.create(Registries.ENTITY_TYPE, OldRepublicMod.id("ship_targets"));

	private static ResourceKey<EntityType<?>> key(String path) {
		return ResourceKey.create(Registries.ENTITY_TYPE, OldRepublicMod.id(path));
	}

	/** Registers the attributes (loading the class registers the types and the eggs). Called from {@link SpaceFeature}. */
	static void init() {
		FabricDefaultAttributeRegistry.register(HYMN_INTERCEPTOR, Starfighter.createAttributes());
		FabricDefaultAttributeRegistry.register(KAJIDIC_RAIDER, Starfighter.createAttributes());
		FabricDefaultAttributeRegistry.register(HASK_FIGHTER, Starfighter.createAttributes());
		FabricDefaultAttributeRegistry.register(BLOCKADE_GUNSHIP, BlockadeGunship.createAttributes());
		FabricDefaultAttributeRegistry.register(GUNSHIP_GENERATOR, GunshipGenerator.createAttributes());
	}

	private SpaceEntities() {
	}
}
