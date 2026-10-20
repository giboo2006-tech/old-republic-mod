package com.oldrepublic.enemy.cartel;

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
 * The cartel enforcer's entity type (spec MILESTONE_5.md 12.1, 18.1): {@code cartel_enforcer}, a
 * humanoid of the Vessba kajidic in three kits. A faction NPC like the troopers (category MISC, no
 * natural spawning): the hubs, the kajidic posts, the missions and the spawner group place it.
 */
public final class CartelEntities {
	/** The entity id, also the {@code onFoeKilled} kind. */
	public static final String ID = "cartel_enforcer";
	public static final ResourceKey<EntityType<?>> CARTEL_ENFORCER_KEY = ResourceKey.create(Registries.ENTITY_TYPE, OldRepublicMod.id("cartel_enforcer"));

	public static final EntityType<CartelEnforcer> CARTEL_ENFORCER = Registry.register(BuiltInRegistries.ENTITY_TYPE, CARTEL_ENFORCER_KEY,
			EntityType.Builder.<CartelEnforcer>of(CartelEnforcer::new, MobCategory.MISC)
					.sized(0.6f, 1.95f)
					.eyeHeight(1.62f)
					.clientTrackingRange(10)
					.build(CARTEL_ENFORCER_KEY));

	/** Testing aid: a random kit. */
	public static final SpawnEggItem CARTEL_ENFORCER_SPAWN_EGG = ModRegistry.item("cartel_enforcer_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(CARTEL_ENFORCER));

	/** Registers the attributes (loading the class registers the type and the egg). Called from {@code FoesM5Feature}. */
	public static void init() {
		FabricDefaultAttributeRegistry.register(CARTEL_ENFORCER, CartelEnforcer.createAttributes());
	}

	private CartelEntities() {
	}
}
