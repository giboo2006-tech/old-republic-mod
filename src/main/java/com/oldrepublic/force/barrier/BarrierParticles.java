package com.oldrepublic.force.barrier;

import com.oldrepublic.OldRepublicMod;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Particle type of the Milestone 4 powers (spec MILESTONE_4.md 13.2): {@code held_dust}, dust and
 * leaf grit held at the surface of a Force Barrier and thrown outward by its pulse, also the grit
 * lifted under a choked target. Registered in common code; the provider lives on the client.
 */
public final class BarrierParticles {
	/** A slow-drifting dust or leaf speck, tinted at spawn time. */
	public static final SimpleParticleType HELD_DUST = Registry.register(BuiltInRegistries.PARTICLE_TYPE, OldRepublicMod.id("held_dust"),
			FabricParticleTypes.simple());

	/** Loads the class, which registers the type. */
	public static void init() {
	}

	private BarrierParticles() {
	}
}
