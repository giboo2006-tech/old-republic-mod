package com.oldrepublic.boss.ulvaine;

import com.oldrepublic.OldRepublicMod;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * Darth Ulvaine's entity types (spec MILESTONE_5.md 12.2, 18.1), never saved (her fight respawns
 * them): {@code darth_ulvaine} and {@code hymn_resonator}, the Crescendo's crystal spires.
 */
public final class UlvaineEntities {
	public static final ResourceKey<EntityType<?>> DARTH_ULVAINE_KEY = ResourceKey.create(Registries.ENTITY_TYPE, OldRepublicMod.id("darth_ulvaine"));
	public static final ResourceKey<EntityType<?>> HYMN_RESONATOR_KEY = ResourceKey.create(Registries.ENTITY_TYPE, OldRepublicMod.id("hymn_resonator"));

	public static final EntityType<DarthUlvaine> DARTH_ULVAINE = Registry.register(BuiltInRegistries.ENTITY_TYPE, DARTH_ULVAINE_KEY,
			EntityType.Builder.<DarthUlvaine>of(DarthUlvaine::new, MobCategory.MONSTER)
					.sized(0.6f, 1.95f)
					.eyeHeight(1.7f)
					.fireImmune()
					.noSave()
					.clientTrackingRange(12)
					.build(DARTH_ULVAINE_KEY));

	public static final EntityType<HymnResonator> HYMN_RESONATOR = Registry.register(BuiltInRegistries.ENTITY_TYPE, HYMN_RESONATOR_KEY,
			EntityType.Builder.<HymnResonator>of(HymnResonator::new, MobCategory.MISC)
					.sized(0.9f, 2.4f)
					.eyeHeight(1.8f)
					.fireImmune()
					.noSave()
					.clientTrackingRange(10)
					.build(HYMN_RESONATOR_KEY));

	/** Registers the attributes (loading the class registers the types). Called from {@link UlvaineFeature}. */
	public static void init() {
		FabricDefaultAttributeRegistry.register(DARTH_ULVAINE, DarthUlvaine.createAttributes());
		FabricDefaultAttributeRegistry.register(HYMN_RESONATOR, HymnResonator.createAttributes());
	}

	private UlvaineEntities() {
	}
}
