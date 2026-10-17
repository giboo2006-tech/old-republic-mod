package com.oldrepublic.identity;

import com.oldrepublic.OldRepublicMod;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** Entity types of the identity feature. */
public final class IdentityEntities {
	public static final ResourceKey<EntityType<?>> FRONTIER_SETTLER_KEY = ResourceKey.create(Registries.ENTITY_TYPE, OldRepublicMod.id("frontier_settler"));

	public static final EntityType<FrontierSettler> FRONTIER_SETTLER = Registry.register(BuiltInRegistries.ENTITY_TYPE, FRONTIER_SETTLER_KEY,
			EntityType.Builder.of(FrontierSettler::new, MobCategory.CREATURE)
					.sized(0.6f, 1.95f)
					.eyeHeight(1.62f)
					.clientTrackingRange(8)
					.build(FRONTIER_SETTLER_KEY));

	static void init() {
		FabricDefaultAttributeRegistry.register(FRONTIER_SETTLER, FrontierSettler.createAttributes());
	}

	private IdentityEntities() {
	}
}
