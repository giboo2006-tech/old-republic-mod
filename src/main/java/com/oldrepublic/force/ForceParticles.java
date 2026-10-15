package com.oldrepublic.force;

import com.oldrepublic.OldRepublicMod;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Particle types of the force feature. Registered in common code; the providers live on the client.
 * {@link #AIR_RIPPLE} is the shared "bent air" shell other features may reuse (e.g. the Awakening burst).
 */
public final class ForceParticles {
	/** Faint, colourless shell of bent air. */
	public static final SimpleParticleType AIR_RIPPLE = register("air_ripple");
	/** Directional dust streak that marks the edge of a push cone. */
	public static final SimpleParticleType PUSH_STREAK = register("push_streak");
	/** Burst of pressure and dust where a push hits a target. */
	public static final SimpleParticleType PUSH_IMPACT = register("push_impact");
	/** Small dust mote, tinted at spawn time (charge gathering and aftermath). */
	public static final SimpleParticleType GATHER_DUST = register("gather_dust");
	/** Dull mote that drops from the hand when a power fails. */
	public static final SimpleParticleType FAIL_MOTE = register("fail_mote");

	private static SimpleParticleType register(String name) {
		return Registry.register(BuiltInRegistries.PARTICLE_TYPE, OldRepublicMod.id(name), FabricParticleTypes.simple());
	}

	public static void init() {
	}

	private ForceParticles() {
	}
}
