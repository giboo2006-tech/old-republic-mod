package com.oldrepublic.boss.kaast;

import com.oldrepublic.OldRepublicMod;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * The camp fight's entity types (spec MILESTONE_4.md 16.1), none of them saved: {@code torvin_kaast},
 * {@code kaast_droid} (Rattle) and {@code camp_mine}. The session respawns them.
 */
public final class KaastEntities {
	public static final ResourceKey<EntityType<?>> TORVIN_KAAST_KEY = key("torvin_kaast");
	public static final ResourceKey<EntityType<?>> KAAST_DROID_KEY = key("kaast_droid");
	public static final ResourceKey<EntityType<?>> CAMP_MINE_KEY = key("camp_mine");

	public static final EntityType<TorvinKaast> TORVIN_KAAST = Registry.register(BuiltInRegistries.ENTITY_TYPE, TORVIN_KAAST_KEY,
			EntityType.Builder.<TorvinKaast>of(TorvinKaast::new, MobCategory.MONSTER)
					.sized(0.65f, 1.98f)
					.eyeHeight(1.65f)
					.noSave()
					.clientTrackingRange(12)
					.build(TORVIN_KAAST_KEY));

	public static final EntityType<Rattle> KAAST_DROID = Registry.register(BuiltInRegistries.ENTITY_TYPE, KAAST_DROID_KEY,
			EntityType.Builder.<Rattle>of(Rattle::new, MobCategory.MISC)
					.sized(0.5f, 0.5f)
					.eyeHeight(0.25f)
					.fireImmune()
					.noSave()
					.clientTrackingRange(10)
					.build(KAAST_DROID_KEY));

	public static final EntityType<CampMine> CAMP_MINE = Registry.register(BuiltInRegistries.ENTITY_TYPE, CAMP_MINE_KEY,
			EntityType.Builder.<CampMine>of(CampMine::new, MobCategory.MISC)
					.sized(0.5f, 0.2f)
					.noSave()
					.clientTrackingRange(8)
					.build(CAMP_MINE_KEY));

	private static ResourceKey<EntityType<?>> key(String path) {
		return ResourceKey.create(Registries.ENTITY_TYPE, OldRepublicMod.id(path));
	}

	/** Registers the attributes (loading the class registers the types). Called from {@code FoesFeature}. */
	public static void init() {
		FabricDefaultAttributeRegistry.register(TORVIN_KAAST, TorvinKaast.createAttributes());
		FabricDefaultAttributeRegistry.register(KAAST_DROID, Rattle.createAttributes());
	}

	private KaastEntities() {
	}
}
