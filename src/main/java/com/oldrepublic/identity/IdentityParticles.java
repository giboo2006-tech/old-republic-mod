package com.oldrepublic.identity;

import com.oldrepublic.OldRepublicMod;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

/** Particle types of the identity feature. Providers are registered in {@code client.identity.IdentityClient}. */
public final class IdentityParticles {
	/** Short frayed rope strands that fall from a settler's wrists when the bonds are cut. */
	public static final SimpleParticleType ROPE_FIBRE = register("rope_fibre");

	private static SimpleParticleType register(String name) {
		return Registry.register(BuiltInRegistries.PARTICLE_TYPE, OldRepublicMod.id(name), FabricParticleTypes.simple());
	}

	static void init() {
	}

	private IdentityParticles() {
	}
}
