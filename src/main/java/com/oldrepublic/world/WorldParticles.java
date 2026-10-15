package com.oldrepublic.world;

import com.oldrepublic.OldRepublicMod;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Particle types of the world feature. Providers are registered in
 * {@code client.world.WorldParticleProviders}. Other features may use them by id
 * (the saber workbench uses {@code crystal_glint}).
 */
public final class WorldParticles {
	/** Slow, pale motes drifting up from crystal formations. */
	public static final SimpleParticleType CRYSTAL_MOTE = register("crystal_mote");
	/** A brief star-shaped glint on a crystal face. */
	public static final SimpleParticleType CRYSTAL_GLINT = register("crystal_glint");
	/** Fast, bright sparks of an unstable discharge. */
	public static final SimpleParticleType CRYSTAL_SPARK = register("crystal_spark");
	/** Dark, smoky wisps around the Shadow Echo and the trial chamber. */
	public static final SimpleParticleType SHADOW_WISP = register("shadow_wisp");

	private static SimpleParticleType register(String name) {
		return Registry.register(BuiltInRegistries.PARTICLE_TYPE, OldRepublicMod.id(name), FabricParticleTypes.simple());
	}

	static void init() {
	}

	private WorldParticles() {
	}
}
