package com.oldrepublic.boss.cantor;

import com.oldrepublic.OldRepublicMod;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * The Long Hymn's particle (spec MILESTONE_5.md 15.2): {@code spectral_wisp}, a pale streak of light
 * that drifts and fades (the First Cantor's robes, his choir, the shield's motes, his unravelling).
 * The provider is the client's ({@code client.boss.cantor}).
 */
public final class CantorParticles {
	/** A pale wisp of sung light (near-white art, tinted by the client where it needs a colour). */
	public static final SimpleParticleType SPECTRAL_WISP = register("spectral_wisp");

	private static SimpleParticleType register(String name) {
		return Registry.register(BuiltInRegistries.PARTICLE_TYPE, OldRepublicMod.id(name), FabricParticleTypes.simple());
	}

	/** Loads the class, which registers the particle. Called from {@code RaidFeature}. */
	public static void init() {
	}

	private CantorParticles() {
	}
}
