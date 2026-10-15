package com.oldrepublic.enemy;

import com.oldrepublic.OldRepublicMod;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

/** Particle types of the enemy feature. Providers live in {@code client.enemy.EnemyParticleProviders}. */
public final class EnemyParticles {
	/** Red-orange flare at a blaster muzzle: the attack tell and the shot flash. */
	public static final SimpleParticleType MUZZLE_GLOW = register("muzzle_glow");
	/** Hot sparks thrown off where a bolt strikes. */
	public static final SimpleParticleType BOLT_IMPACT = register("bolt_impact");
	/** Dark smoke wisp with a faint ember, left on blocks a bolt has scorched. */
	public static final SimpleParticleType BOLT_SCORCH = register("bolt_scorch");

	private static SimpleParticleType register(String name) {
		return Registry.register(BuiltInRegistries.PARTICLE_TYPE, OldRepublicMod.id(name), FabricParticleTypes.simple());
	}

	static void init() {
	}

	private EnemyParticles() {
	}
}
