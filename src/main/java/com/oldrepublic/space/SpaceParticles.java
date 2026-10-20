package com.oldrepublic.space;

import com.oldrepublic.OldRepublicMod;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Space's particle type (spec MILESTONE_5.md 15.4): the rest of its effects reuse the M1 to M4
 * particles ({@code spark_shower}, {@code air_ripple}, {@code grenade_smoke}, {@code engine_exhaust}).
 * The provider is registered in {@code client.space.SpaceClient}.
 */
public final class SpaceParticles {
	/** A torn plate of hull, dark grey with a scorched edge, tumbling slowly away with no gravity (a bolt on a hull, a ship destroyed). */
	public static final SimpleParticleType HULL_DEBRIS = Registry.register(BuiltInRegistries.PARTICLE_TYPE, OldRepublicMod.id("hull_debris"),
			FabricParticleTypes.simple());

	/** Loads the class, registering the type. */
	static void init() {
	}

	private SpaceParticles() {
	}
}
