package com.oldrepublic.travel;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * The courier shuttle's entity type (spec MILESTONE_4.md 4.1, 16.1): one 7 × 4 × 7 box (the model
 * overhangs it), tracked from 10 chunks, updated every tick so the flight path is smooth, fire-proof
 * and persistent (its truth is {@link ShipRegistry}). Vanilla re-creates it with
 * {@code EntitySpawnReason.DIMENSION_TRAVEL} on a jump, which the plain constructor allows.
 */
public final class ShipEntities {
	public static final ResourceKey<EntityType<?>> COURIER_SHUTTLE_KEY = ResourceKey.create(Registries.ENTITY_TYPE, OldRepublicMod.id("courier_shuttle"));

	public static final EntityType<CourierShuttle> COURIER_SHUTTLE = Registry.register(BuiltInRegistries.ENTITY_TYPE, COURIER_SHUTTLE_KEY,
			EntityType.Builder.<CourierShuttle>of(CourierShuttle::new, MobCategory.MISC)
					.sized(7f, 4f)
					.clientTrackingRange(10)
					.updateInterval(1)
					.fireImmune()
					.build(COURIER_SHUTTLE_KEY));

	static void init() {
	}

	private ShipEntities() {
	}
}
