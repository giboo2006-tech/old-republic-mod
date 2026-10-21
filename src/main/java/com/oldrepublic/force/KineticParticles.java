package com.oldrepublic.force;

import com.oldrepublic.OldRepublicMod;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Particle types of the kinetic powers' effects (Push, Pull, Lift, Jump, Speed, Stasis, Choke and Barrier) added after
 * Milestone 5; registered from {@link ForceParticles#init}, on both sides. The providers live on the client
 * ({@code KineticVfx}). Every sprite is near-white with alpha, tinted in code: dust and grit take the colour of the ground
 * they came from.
 */
public final class KineticParticles {
	/** A billowing cloud of dust that grows, turns slowly and settles; tinted with the ground's colour. */
	public static final SimpleParticleType KINETIC_DUST = register("kinetic_dust");
	/** A small faceted chip of stone or earth that tumbles, falls and bounces; tinted with the ground's colour. */
	public static final SimpleParticleType KINETIC_PEBBLE = register("kinetic_pebble");
	/** A thin line of bent air lying along its own motion, longer the faster it flies. */
	public static final SimpleParticleType KINETIC_STREAK = register("kinetic_streak");
	/** A splinter of frost that cracks off a creature when its Force Stasis breaks. */
	public static final SimpleParticleType KINETIC_SHARD = register("kinetic_shard");
	/** A crust of hoarfrost on a creature held in Force Stasis. */
	public static final SimpleParticleType KINETIC_RIME = register("kinetic_rime");
	/** A leaf torn off grassy ground and carried by the air; tinted green or brown in code. */
	public static final SimpleParticleType KINETIC_LEAF = register("kinetic_leaf");

	private static SimpleParticleType register(String name) {
		return Registry.register(BuiltInRegistries.PARTICLE_TYPE, OldRepublicMod.id(name), FabricParticleTypes.simple());
	}

	public static void init() {
	}

	private KineticParticles() {
	}
}
