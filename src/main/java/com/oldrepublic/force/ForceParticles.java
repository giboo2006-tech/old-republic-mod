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
	/** Dust streak peeling off a pulled target toward the caster (Force Pull). */
	public static final SimpleParticleType PULL_STREAK = register("pull_streak");
	/** Faint mote left behind a leaping player (Force Jump). */
	public static final SimpleParticleType JUMP_TRAIL = register("jump_trail");
	/** One straight segment of a lightning arc, drawn along the segment (Force Lightning). */
	public static final SimpleParticleType LIGHTNING_SEGMENT = register("lightning_segment");
	/** Bright spark speck: charge crackle at the fingertips and contact sparks (Force Lightning). */
	public static final SimpleParticleType LIGHTNING_SPARK = register("lightning_spark");
	/** Sparse pale speck that clings to a warded body (Serene Ward). */
	public static final SimpleParticleType WARD_GLINT = register("ward_glint");
	/** Slow-drifting dust mote of a Force Sense cluster, tinted at spawn time (attitude, block gold). */
	public static final SimpleParticleType SENSE_MOTE = register("sense_mote");
	/** Thin dust speck that runs from the caster toward a sensed point (Force Sense). */
	public static final SimpleParticleType SENSE_STREAM = register("sense_stream");
	/** Rising bent-air shimmer under a lifted target (Telekinetic Lift); never a disc. */
	public static final SimpleParticleType LIFT_SHIMMER = register("lift_shimmer");

	private static SimpleParticleType register(String name) {
		return Registry.register(BuiltInRegistries.PARTICLE_TYPE, OldRepublicMod.id(name), FabricParticleTypes.simple());
	}

	public static void init() {
		EnergyParticles.init();
		KineticParticles.init();
	}

	private ForceParticles() {
	}
}
