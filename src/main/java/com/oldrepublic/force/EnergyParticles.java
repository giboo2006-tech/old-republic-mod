package com.oldrepublic.force;

import com.oldrepublic.OldRepublicMod;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Particle types of the energy and mind powers' effects (Lightning, Drain, Heal, Valor, Rage, Fear, Mind Trick, Sense and
 * Danger Sense) added after Milestone 5; registered from {@link ForceParticles#init}, on both sides. Their light (bolts,
 * sparks, glowing motes) is drawn in code on the client; these are what it leaves in the air.
 */
public final class EnergyParticles {
	/** A soft puff of smoke, tinted at spawn time (scorch smoke after lightning, the smoke of Rage). */
	public static final SimpleParticleType SMOKE = register("energy_smoke");
	/** Grainy dust kicked off the ground, tinted the colour of the block it came from. */
	public static final SimpleParticleType DUST = register("energy_dust");
	/** A long, thin wisp of mist or dark vapour, tinted at spawn time (the cold dread of Fear, what Drain tears loose). */
	public static final SimpleParticleType MIST = register("energy_mist");

	private static SimpleParticleType register(String name) {
		return Registry.register(BuiltInRegistries.PARTICLE_TYPE, OldRepublicMod.id(name), FabricParticleTypes.simple());
	}

	public static void init() {
	}

	private EnergyParticles() {
	}
}
