package com.oldrepublic.world;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.world.trial.ShadowEchoEntity;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** Entity types of the world feature. */
public final class WorldEntities {
	public static final ResourceKey<EntityType<?>> SHADOW_ECHO_KEY = ResourceKey.create(Registries.ENTITY_TYPE, OldRepublicMod.id("shadow_echo"));

	/** Never saved: an echo only lives as long as its trial session (see {@code TrialManager}). */
	public static final EntityType<ShadowEchoEntity> SHADOW_ECHO = Registry.register(BuiltInRegistries.ENTITY_TYPE, SHADOW_ECHO_KEY,
			EntityType.Builder.<ShadowEchoEntity>of(ShadowEchoEntity::new, MobCategory.MONSTER)
					.sized(0.6f, 1.8f)
					.eyeHeight(1.62f)
					.clientTrackingRange(8)
					.noSave()
					.noLootTable()
					.build(SHADOW_ECHO_KEY));

	static void init() {
		FabricDefaultAttributeRegistry.register(SHADOW_ECHO, ShadowEchoEntity.createAttributes());
	}

	private WorldEntities() {
	}
}
