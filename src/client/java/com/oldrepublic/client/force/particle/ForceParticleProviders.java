package com.oldrepublic.client.force.particle;

import com.oldrepublic.force.ForceParticles;

import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;

/** Registers the client providers for {@link ForceParticles}. Sprites come from {@code particles/<id>.json}. */
public final class ForceParticleProviders {
	@FunctionalInterface
	private interface Factory {
		ForceParticle create(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites);
	}

	public static void init() {
		register(ForceParticles.AIR_RIPPLE, AirRippleParticle::new);
		register(ForceParticles.PUSH_STREAK, PushStreakParticle::new);
		register(ForceParticles.PUSH_IMPACT, PushImpactParticle::new);
		register(ForceParticles.GATHER_DUST, DustMoteParticle::new);
		register(ForceParticles.FAIL_MOTE, DustMoteParticle::new);
	}

	private static void register(SimpleParticleType type, Factory factory) {
		ParticleProviderRegistry.PendingParticleProvider<SimpleParticleType> pending = sprites -> {
			ParticleProvider<SimpleParticleType> provider = (options, level, x, y, z, dx, dy, dz, random) -> factory.create(level, x, y, z, dx, dy, dz, sprites);
			return provider;
		};
		ParticleProviderRegistry.getInstance().register(type, pending);
	}

	private ForceParticleProviders() {
	}
}
