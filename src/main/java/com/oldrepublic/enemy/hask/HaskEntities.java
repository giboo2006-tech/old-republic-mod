package com.oldrepublic.enemy.hask;

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
 * The Clan Hask warrior's entity type (spec MILESTONE_5.md 12.1, 18.1): {@code hask_warrior}, a
 * Mandalorian of the clan in three kits. A faction NPC (category MISC, no natural spawning): the
 * holdfast's posts, hunts, contracts, the duel of honour and the clan's way place it.
 */
public final class HaskEntities {
	/** The entity id, also the {@code onFoeKilled} kind. */
	public static final String ID = "hask_warrior";
	public static final ResourceKey<EntityType<?>> HASK_WARRIOR_KEY = ResourceKey.create(Registries.ENTITY_TYPE, OldRepublicMod.id("hask_warrior"));

	public static final EntityType<HaskWarrior> HASK_WARRIOR = Registry.register(BuiltInRegistries.ENTITY_TYPE, HASK_WARRIOR_KEY,
			EntityType.Builder.<HaskWarrior>of(HaskWarrior::new, MobCategory.MISC)
					.sized(0.6f, 1.95f)
					.eyeHeight(1.62f)
					.clientTrackingRange(10)
					.build(HASK_WARRIOR_KEY));

	/** Testing aid: a random kit. */
	public static final SpawnEggItem HASK_WARRIOR_SPAWN_EGG = ModRegistry.item("hask_warrior_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(HASK_WARRIOR));

	/** Registers the attributes (loading the class registers the type and the egg). Called from {@code FoesM5Feature}. */
	public static void init() {
		FabricDefaultAttributeRegistry.register(HASK_WARRIOR, HaskWarrior.createAttributes());
	}

	private HaskEntities() {
	}
}
