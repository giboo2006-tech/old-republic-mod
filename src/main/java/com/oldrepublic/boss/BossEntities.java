package com.oldrepublic.boss;

import com.oldrepublic.OldRepublicMod;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * The Collector fight's entity types (spec 8, 16): Orren Vasch, the pylon foci and the Archive debris.
 * All three are {@code noSave}: they live only inside an arena session (8.6).
 */
public final class BossEntities {
	public static final ResourceKey<EntityType<?>> ORREN_VASCH_KEY = ResourceKey.create(Registries.ENTITY_TYPE, OldRepublicMod.id("orren_vasch"));
	public static final ResourceKey<EntityType<?>> PYLON_FOCUS_KEY = ResourceKey.create(Registries.ENTITY_TYPE, OldRepublicMod.id("pylon_focus"));
	public static final ResourceKey<EntityType<?>> ARCHIVE_DEBRIS_KEY = ResourceKey.create(Registries.ENTITY_TYPE, OldRepublicMod.id("archive_debris"));

	/** Orren Vasch: spawned by his heart, never saved, fire-immune (8.4). */
	public static final EntityType<OrrenVasch> ORREN_VASCH = Registry.register(BuiltInRegistries.ENTITY_TYPE, ORREN_VASCH_KEY,
			EntityType.Builder.of(OrrenVasch::new, MobCategory.MONSTER)
					.sized(0.6f, 1.95f)
					.eyeHeight(1.62f)
					.clientTrackingRange(10)
					.fireImmune()
					.noSave()
					.build(ORREN_VASCH_KEY));

	/** The singing crystal's hit box on top of a resonance pylon while it sings or regrows. */
	public static final EntityType<PylonFocus> PYLON_FOCUS = Registry.register(BuiltInRegistries.ENTITY_TYPE, PYLON_FOCUS_KEY,
			EntityType.Builder.<PylonFocus>of(PylonFocus::new, MobCategory.MISC)
					.sized(0.8f, 0.8f)
					.clientTrackingRange(10)
					.updateInterval(20)
					.fireImmune()
					.noSave()
					.build(PYLON_FOCUS_KEY));

	/** A chunk of the Archive torn from a debris pile by the Hurl. */
	public static final EntityType<ArchiveDebris> ARCHIVE_DEBRIS = Registry.register(BuiltInRegistries.ENTITY_TYPE, ARCHIVE_DEBRIS_KEY,
			EntityType.Builder.<ArchiveDebris>of(ArchiveDebris::new, MobCategory.MISC)
					.sized(0.75f, 0.75f)
					.clientTrackingRange(8)
					.updateInterval(1)
					.fireImmune()
					.noSave()
					.build(ARCHIVE_DEBRIS_KEY));

	static void init() {
		FabricDefaultAttributeRegistry.register(ORREN_VASCH, OrrenVasch.createAttributes());
	}

	private BossEntities() {
	}
}
