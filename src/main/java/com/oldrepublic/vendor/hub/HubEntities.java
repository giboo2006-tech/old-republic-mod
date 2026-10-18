package com.oldrepublic.vendor.hub;

import com.oldrepublic.OldRepublicMod;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * The hub people's entity types (spec MILESTONE_4.md 16.1): {@code hub_host}, {@code apprentice},
 * {@code acolyte_hub}, {@code proving_guard} and {@code vendor}. No natural spawns: the hubs'
 * {@code hub_spawner} blocks make them through {@link HubNpcs}.
 */
public final class HubEntities {
	public static final EntityType<HubNpc> HUB_HOST = register("hub_host", HubRole.HOST);
	public static final EntityType<HubNpc> APPRENTICE = register("apprentice", HubRole.APPRENTICE);
	public static final EntityType<HubNpc> ACOLYTE_HUB = register("acolyte_hub", HubRole.ACOLYTE);
	public static final EntityType<HubNpc> PROVING_GUARD = register("proving_guard", HubRole.GUARD);
	public static final EntityType<HubNpc> VENDOR = register("vendor", HubRole.VENDOR);

	private static EntityType<HubNpc> register(String name, HubRole role) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, OldRepublicMod.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
				EntityType.Builder.<HubNpc>of((type, level) -> new HubNpc(type, level, role), MobCategory.MISC)
						.sized(0.6f, 1.95f)
						.eyeHeight(1.62f)
						.clientTrackingRange(10)
						.fireImmune()
						.build(key));
	}

	/** The type of a role. */
	public static EntityType<HubNpc> type(HubRole role) {
		return switch (role) {
			case HOST -> HUB_HOST;
			case APPRENTICE -> APPRENTICE;
			case ACOLYTE -> ACOLYTE_HUB;
			case GUARD -> PROVING_GUARD;
			case VENDOR -> VENDOR;
		};
	}

	public static void init() {
		for (HubRole role : HubRole.values()) {
			FabricDefaultAttributeRegistry.register(type(role), HubNpc.createAttributes());
		}
	}

	private HubEntities() {
	}
}
