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
	/** The warm voice of the Calling toward the Crossing: a pale gold glint drifting along the pull. */
	public static final SimpleParticleType VOICE_WARM = register("voice_warm");
	/** The cold voice: grey-blue dust drifting along the pull; also the dust a departing mentor kicks up. */
	public static final SimpleParticleType VOICE_DUST = register("voice_dust");
	/** Ash drifting toward the raider camp (Smoke on the Ridge's pull). */
	public static final SimpleParticleType ASH_MOTE = register("ash_mote");
	/** A short ember streak slanting down toward Courier's Fall (the {@code FALL} Calling). */
	public static final SimpleParticleType FALLING_EMBER = register("falling_ember");

	private static SimpleParticleType register(String name) {
		return Registry.register(BuiltInRegistries.PARTICLE_TYPE, OldRepublicMod.id(name), FabricParticleTypes.simple());
	}

	static void init() {
	}

	private QuestParticles() {
	}
}
