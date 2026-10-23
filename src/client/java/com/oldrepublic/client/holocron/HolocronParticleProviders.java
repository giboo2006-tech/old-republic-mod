package com.oldrepublic.client.holocron;

import com.oldrepublic.holocron.HolocronParticles;

import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;

/**
 * Client providers of the holocron particles (spec 13.2): {@code holocron_mote} (seam light and
 * alcove dust) and {@code projection_band} (the gatekeeper's horizontal banding). Both are
 * configured after spawning by {@link HolocronFx}.
 */
final class HolocronParticleProviders {
	static void init() {
		ParticleProviderRegistry.getInstance().register(HolocronParticles.HOLOCRON_MOTE,
				sprites -> (type, level, x, y, z, dx, dy, dz, random) -> new HolocronMoteParticle(level, x, y, z, dx, dy, dz, sprites));
		ParticleProviderRegistry.getInstance().register(HolocronParticles.PROJECTION_BAND,
				sprites -> (type, level, x, y, z, dx, dy, dz, random) -> new ProjectionBandParticle(level, x, y, z, dx, dy, dz, sprites));
	}

	private HolocronParticleProviders() {
	}
}
