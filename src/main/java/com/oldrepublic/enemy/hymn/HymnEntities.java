package com.oldrepublic.enemy.hymn;

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
 * The Hymnsworn's entity type (spec MILESTONE_5.md 12.1, 18.1): {@code hymnsworn}, Ulvaine's cult in
 * three kinds. Never spawns naturally and never in Peaceful: the verse stones, the shrines, the
 * Hymnal and the raid place it.
 */
public final class HymnEntities {
	/** The entity id, also the {@code onFoeKilled} kind. */
	public static final String HYMNSWORN_ID = "hymnsworn";
	public static final ResourceKey<EntityType<?>> HYMNSWORN_KEY = ResourceKey.create(Registries.ENTITY_TYPE, OldRepublicMod.id("hymnsworn"));

	public static final EntityType<Hymnsworn> HYMNSWORN = Registry.register(BuiltInRegistries.ENTITY_TYPE, HYMNSWORN_KEY,
			EntityType.Builder.<Hymnsworn>of(Hymnsworn::new, MobCategory.MONSTER)
					.sized(0.6f, 1.95f)
					.eyeHeight(1.62f)
					.notInPeaceful()
					.clientTrackingRange(10)
					.build(HYMNSWORN_KEY));

	/** Testing aid: a random kind. */
	public static final SpawnEggItem HYMNSWORN_SPAWN_EGG = ModRegistry.item("hymnsworn_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(HYMNSWORN));

	/** Registers the attributes (loading the class registers the type and the egg). Called from {@code FoesM5Feature}. */
	public static void init() {
		FabricDefaultAttributeRegistry.register(HYMNSWORN, Hymnsworn.createAttributes());
	}

	private HymnEntities() {
	}
}
