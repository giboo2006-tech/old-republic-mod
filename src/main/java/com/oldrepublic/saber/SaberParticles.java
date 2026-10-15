package com.oldrepublic.saber;

import com.oldrepublic.OldRepublicMod;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Particle types of the saber feature. The client creates these particles directly (with the blade
 * colour) in {@code client.saber.SaberFx}; the types exist so their sprites are loaded.
 */
public final class SaberParticles {
	/** Tiny hot sparks thrown off a clash, deflection or hit. */
	public static final SimpleParticleType SABER_SPARK = register("saber_spark");
	/** One-tick bright flash at the contact point. */
	public static final SimpleParticleType CONTACT_FLASH = register("contact_flash");

	private static SimpleParticleType register(String name) {
		return Registry.register(BuiltInRegistries.PARTICLE_TYPE, OldRepublicMod.id(name), FabricParticleTypes.simple());
	}

	static void init() {
	}

	private SaberParticles() {
	}
}
