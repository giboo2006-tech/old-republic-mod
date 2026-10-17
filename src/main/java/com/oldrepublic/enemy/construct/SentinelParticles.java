package com.oldrepublic.enemy.construct;

import com.oldrepublic.OldRepublicMod;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

/** Particle types of the Ruin Sentinel. The provider lives in {@code client.boss.sentinel.LumenSparkProvider}. */
public final class SentinelParticles {
	/** A bright lumen spark (white art, tinted pale blue for Watchers and red for Tomb sentinels by the client). */
	public static final SimpleParticleType LUMEN_SPARK = register("lumen_spark");

	private static SimpleParticleType register(String name) {
		return Registry.register(BuiltInRegistries.PARTICLE_TYPE, OldRepublicMod.id(name), FabricParticleTypes.simple());
	}

	static void init() {
	}

	private SentinelParticles() {
	}
}
