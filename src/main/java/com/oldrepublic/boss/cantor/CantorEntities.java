package com.oldrepublic.boss.cantor;

import com.oldrepublic.OldRepublicMod;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * The Long Hymn's entity types (spec MILESTONE_5.md 12.3, 18.1), never saved (a restart ends the
 * raid, the M3 rule): {@code first_cantor}, the towering spectral Sith bound into the song-engine,
 * and {@code cantor_choir}, the echoes who sing his shield.
 */
public final class CantorEntities {
	public static final ResourceKey<EntityType<?>> FIRST_CANTOR_KEY = ResourceKey.create(Registries.ENTITY_TYPE, OldRepublicMod.id("first_cantor"));
	public static final ResourceKey<EntityType<?>> CANTOR_CHOIR_KEY = ResourceKey.create(Registries.ENTITY_TYPE, OldRepublicMod.id("cantor_choir"));

	public static final EntityType<FirstCantor> FIRST_CANTOR = Registry.register(BuiltInRegistries.ENTITY_TYPE, FIRST_CANTOR_KEY,
			EntityType.Builder.<FirstCantor>of(FirstCantor::new, MobCategory.MONSTER)
					.sized(1.0f, 2.6f)
					.eyeHeight(2.3f)
					.fireImmune()
					.noSave()
					.clientTrackingRange(12)
					.build(FIRST_CANTOR_KEY));

	public static final EntityType<CantorChoir> CANTOR_CHOIR = Registry.register(BuiltInRegistries.ENTITY_TYPE, CANTOR_CHOIR_KEY,
			EntityType.Builder.<CantorChoir>of(CantorChoir::new, MobCategory.MONSTER)
					.sized(0.6f, 1.9f)
					.eyeHeight(1.65f)
					.fireImmune()
					.noSave()
					.clientTrackingRange(10)
					.build(CANTOR_CHOIR_KEY));

	/** Registers the attributes (loading the class registers the types). Called from {@code RaidFeature}. */
	public static void init() {
		FabricDefaultAttributeRegistry.register(FIRST_CANTOR, FirstCantor.createAttributes());
		FabricDefaultAttributeRegistry.register(CANTOR_CHOIR, CantorChoir.createAttributes());
	}

	private CantorEntities() {
	}
}
