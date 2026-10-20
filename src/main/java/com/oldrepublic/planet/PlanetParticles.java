package com.oldrepublic.planet;

import com.oldrepublic.OldRepublicMod;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * Particle types of the planets (spec MILESTONE_4.md 13.2, MILESTONE_5.md 15.2). Providers are registered in
 * {@code client.planet.PlanetParticleProviders}.
 */
public final class PlanetParticles {
	/** A grey flake of Varkhast's ashfall, drifting and tumbling on the wind. */
	public static final SimpleParticleType ASH_FLAKE = register("ash_flake");
	/** A warm gold glint on aurelite, in time with a Singing Hollow chime. */
	public static final SimpleParticleType AURELITE_GLINT = register("aurelite_glint");
	/** A black glass sliver with a dull red edge (duskglass, the rift, a corrupted waystone's veins). */
	public static final SimpleParticleType DUSKGLASS_SHARD = register("duskglass_shard");
	/** A soft streak of Ghessar's sand blown on a sandstorm's wind. */
	public static final SimpleParticleType SAND_GUST = register("sand_gust");
	/** A soft pale mote of a waking verse stone's song (its glow, the channel's stream, the silenced grit). */
	public static final SimpleParticleType VERSE_MOTE = register("verse_mote");

	private static SimpleParticleType register(String name) {
		return Registry.register(BuiltInRegistries.PARTICLE_TYPE, OldRepublicMod.id(name), FabricParticleTypes.simple());
	}

	static void init() {
	}

	private PlanetParticles() {
	}
}
