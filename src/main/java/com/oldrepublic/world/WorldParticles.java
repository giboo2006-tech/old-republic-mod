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
	/** Warm, slow motes of sunlight in ruin light shafts and above lumen moss. */
	public static final SimpleParticleType LIGHT_MOTE = register("light_mote");
	/** A small leaf tumbling down in the ruin courtyard. */
	public static final SimpleParticleType LEAF_FALL = register("leaf_fall");
	/** Grey-brown dust sifting from tomb ceilings, urns and opened stone. */
	public static final SimpleParticleType TOMB_DUST = register("tomb_dust");
	/** Fine pale dust seeping from the mortar lines of revealed veiled stone and opening seals. */
	public static final SimpleParticleType VEIL_DUST = register("veil_dust");
	/** Heavy grit: pebbles and chips with gravity (vent tells, counterweights, ledges, rubble). */
	public static final SimpleParticleType VENT_GRIT = register("vent_grit");
	/** A dull grey-green miasma puff from a tomb vent. */
	public static final SimpleParticleType MIASMA = register("miasma");
	/** A short orange-white spark from a broken wreck conduit. */
	public static final SimpleParticleType CONDUIT_SPARK = register("conduit_spark");

	private static SimpleParticleType register(String name) {
		return Registry.register(BuiltInRegistries.PARTICLE_TYPE, OldRepublicMod.id(name), FabricParticleTypes.simple());
	}

	static void init() {
	}

	private WorldParticles() {
	}
}
