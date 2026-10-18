package com.oldrepublic.equipment;

import com.oldrepublic.OldRepublicMod;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Particle types of the gear (spec MILESTONE_4.md 13.2). Providers are registered in
 * {@code client.equipment.GearParticleProviders}. W6's hunter flamer and jet hop use
 * {@link #FLAME_JET} by id.
 */
public final class EquipmentParticles {
	/** A soft white puff of steam that swells and thins as it rises (blaster vents and overheats). */
	public static final SimpleParticleType STEAM_VENT = register("steam_vent");
	/** A short tongue of flame that shrinks, darkens to smoke and slows (jet pack bursts). */
	public static final SimpleParticleType FLAME_JET = register("flame_jet");

	private static SimpleParticleType register(String name) {
		return Registry.register(BuiltInRegistries.PARTICLE_TYPE, OldRepublicMod.id(name), FabricParticleTypes.simple());
	}

	static void init() {
	}

	private EquipmentParticles() {
	}
}
