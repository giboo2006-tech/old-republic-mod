package com.oldrepublic.travel;

import com.oldrepublic.OldRepublicMod;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Particle types of the ship (spec MILESTONE_4.md 13.2). Providers are registered in
 * {@code client.travel.TravelFx}.
 */
public final class TravelParticles {
	/** A soft blue-white puff of nacelle exhaust that cools to grey as it spreads. */
	public static final SimpleParticleType ENGINE_EXHAUST = register("engine_exhaust");
	/** A bright orange spark that falls and bounces once (welding, a damaged nacelle). */
	public static final SimpleParticleType SPARK_SHOWER = register("spark_shower");

	private static SimpleParticleType register(String name) {
		return Registry.register(BuiltInRegistries.PARTICLE_TYPE, OldRepublicMod.id(name), FabricParticleTypes.simple());
	}

	static void init() {
	}

	private TravelParticles() {
	}
}
