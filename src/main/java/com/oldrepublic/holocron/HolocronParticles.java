package com.oldrepublic.holocron;

import com.oldrepublic.OldRepublicMod;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

/** Particle types of the holocron feature (spec 13.2). Registered in common code; the providers live on the client. */
public final class HolocronParticles {
	/** A slow, softly lit mote of seam light, tinted by the client per holocron kind. */
	public static final SimpleParticleType HOLOCRON_MOTE = register("holocron_mote");
	/** One faint horizontal band of the projected gatekeeper (steady when flashing effects are off). */
	public static final SimpleParticleType PROJECTION_BAND = register("projection_band");

	private static SimpleParticleType register(String name) {
		return Registry.register(BuiltInRegistries.PARTICLE_TYPE, OldRepublicMod.id(name), FabricParticleTypes.simple());
	}

	static void init() {
	}

	private HolocronParticles() {
	}
}
