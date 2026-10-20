package com.oldrepublic.enemy.scour;

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
 * The sand scour's entity type (spec MILESTONE_5.md 12.1, 18.1): {@code sand_scour}, a monster of
 * Ghessar's dunes and canyons (its matriarch is the same type, scaled up). Never in Peaceful.
 */
public final class ScourEntities {
	/** The entity id, also the {@code onFoeKilled} kind. */
	public static final String ID = "sand_scour";
	public static final ResourceKey<EntityType<?>> SAND_SCOUR_KEY = ResourceKey.create(Registries.ENTITY_TYPE, OldRepublicMod.id("sand_scour"));

	public static final EntityType<SandScour> SAND_SCOUR = Registry.register(BuiltInRegistries.ENTITY_TYPE, SAND_SCOUR_KEY,
			EntityType.Builder.<SandScour>of(SandScour::new, MobCategory.MONSTER)
					.sized(1.1f, 0.8f)
					.eyeHeight(0.6f)
					.notInPeaceful()
					.clientTrackingRange(10)
					.build(SAND_SCOUR_KEY));

	/** Testing aid: a burrowed scour. */
	public static final SpawnEggItem SAND_SCOUR_SPAWN_EGG = ModRegistry.item("sand_scour_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(SAND_SCOUR));

	/** Registers the attributes and the spawn placement. Called from {@code FoesM5Feature}. */
	public static void init() {
		FabricDefaultAttributeRegistry.register(SAND_SCOUR, SandScour.createAttributes());
		SpawnPlacements.register(SAND_SCOUR, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Scours::checkSpawnRules);
	}

	private ScourEntities() {
	}
}
