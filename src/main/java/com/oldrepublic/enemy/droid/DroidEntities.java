package com.oldrepublic.enemy.droid;

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
 * The droids' entity types (spec MILESTONE_4.md 16.1): {@code security_droid} (fire-immune; placed by
 * sites, ambushes and spawn eggs, never natural) and {@code seeker_droid} (never saved).
 */
public final class DroidEntities {
	public static final ResourceKey<EntityType<?>> SECURITY_DROID_KEY = ResourceKey.create(Registries.ENTITY_TYPE, OldRepublicMod.id("security_droid"));
	public static final ResourceKey<EntityType<?>> SEEKER_DROID_KEY = ResourceKey.create(Registries.ENTITY_TYPE, OldRepublicMod.id("seeker_droid"));

	public static final EntityType<SecurityDroid> SECURITY_DROID = Registry.register(BuiltInRegistries.ENTITY_TYPE, SECURITY_DROID_KEY,
			EntityType.Builder.<SecurityDroid>of(SecurityDroid::new, MobCategory.MONSTER)
					.sized(0.7f, 2.3f)
					.eyeHeight(2.05f)
					.fireImmune()
					.clientTrackingRange(10)
					.build(SECURITY_DROID_KEY));

	public static final EntityType<SeekerDroid> SEEKER_DROID = Registry.register(BuiltInRegistries.ENTITY_TYPE, SEEKER_DROID_KEY,
			EntityType.Builder.<SeekerDroid>of(SeekerDroid::new, MobCategory.MISC)
					.sized(0.5f, 0.5f)
					.eyeHeight(0.25f)
					.fireImmune()
					.noSave()
					.clientTrackingRange(10)
					.build(SEEKER_DROID_KEY));

	/** Testing aid: spawns a security droid (a hunter's escort, hostile to everyone). */
	public static final SpawnEggItem SECURITY_DROID_SPAWN_EGG = ModRegistry.item("security_droid_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(SECURITY_DROID));

	/** Registers the attributes (loading the class registers the types and the egg). Called from {@code FoesFeature}. */
	public static void init() {
		FabricDefaultAttributeRegistry.register(SECURITY_DROID, SecurityDroid.createAttributes());
		FabricDefaultAttributeRegistry.register(SEEKER_DROID, SeekerDroid.createAttributes());
	}

	private DroidEntities() {
	}
}
