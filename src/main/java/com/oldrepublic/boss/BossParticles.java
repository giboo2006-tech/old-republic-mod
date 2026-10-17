package com.oldrepublic.boss;

import com.oldrepublic.OldRepublicMod;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * The Collector fight's particle types (spec 13.2). The client's {@code BossParticleProviders} draws
 * them (tinted where the art is near-white); B paints the frames and writes {@code particles/*.json}.
 * {@code crystal_glint} is reused from {@code WorldParticles.CRYSTAL_GLINT}.
 */
public final class BossParticles {
	/** Sparse heat-haze specks drifting off Vasch while the Resonance Ward holds (near-white, tinted). */
	public static final SimpleParticleType WARD_HAZE = register("ward_haze");
	/** A crystal mote with a short tail, streaming from a singing pylon to Vasch (tinted pale crystal). */
	public static final SimpleParticleType WARD_CURRENT = register("ward_current");
	/** A sandy dust puff of the Surge wall and the gather (natural colour, 4 frames by age). */
	public static final SimpleParticleType SURGE_DUST = register("surge_dust");
	/** A grit and dust streak along the Lash seam and ridge (natural colour). */
	public static final SimpleParticleType LASH_SEAM = register("lash_seam");
	/** The echo's smoke: forming, its edges, its dissolve (grey, tinted, 4 frames by age). */
	public static final SimpleParticleType ECHO_SMOKE = register("echo_smoke");

	private static SimpleParticleType register(String name) {
		return Registry.register(BuiltInRegistries.PARTICLE_TYPE, OldRepublicMod.id(name), FabricParticleTypes.simple());
	}

	static void init() {
	}

	private BossParticles() {
	}
}
