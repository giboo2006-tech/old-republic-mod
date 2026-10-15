package com.oldrepublic.quest;

import com.oldrepublic.OldRepublicMod;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.SimpleParticleType;

/** Particle types of the quest feature. Providers are registered in {@code client.quest.QuestParticleProviders}. */
public final class QuestParticles {
	/** Pale, slow motes drawn inward during meditation. */
	public static final SimpleParticleType FORCE_MOTE = register("force_mote");
	/** Faint glints that drift toward the nearest crystal cave during the Calling. */
	public static final SimpleParticleType RESONANCE_SHIMMER = register("resonance_shimmer");

	private static SimpleParticleType register(String name) {
		return Registry.register(BuiltInRegistries.PARTICLE_TYPE, OldRepublicMod.id(name), FabricParticleTypes.simple());
	}

	static void init() {
	}

	private QuestParticles() {
	}
}
