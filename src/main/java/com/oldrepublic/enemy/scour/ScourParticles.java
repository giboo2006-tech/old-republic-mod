package com.oldrepublic.enemy.scour;

import com.oldrepublic.OldRepublicMod;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

/** The sand scour's particle (spec MILESTONE_5.md 15.2): {@code sand_burst}. The provider is the client's ({@code client.enemy.scour}). */
public final class ScourParticles {
	/** A thrown clump of sand that arcs and falls (the burst, the burrow, the matriarch's sweep). */
	public static final SimpleParticleType SAND_BURST = register("sand_burst");

	private static SimpleParticleType register(String name) {
		return Registry.register(BuiltInRegistries.PARTICLE_TYPE, OldRepublicMod.id(name), FabricParticleTypes.simple());
	}

	/** Loads the class, which registers the particle. Called from {@code FoesM5Feature}. */
	public static void init() {
	}

	private ScourParticles() {
	}
}
