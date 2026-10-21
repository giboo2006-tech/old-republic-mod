package com.oldrepublic.client.force;

import com.oldrepublic.client.force.kinetic.AirStreakParticle;
import com.oldrepublic.client.force.kinetic.DustCloudParticle;
import com.oldrepublic.client.force.kinetic.KineticRender;
import com.oldrepublic.client.force.kinetic.PebbleParticle;
import com.oldrepublic.client.force.particle.ForceParticleProviders;
import com.oldrepublic.client.force.particle.StasisRimeParticle;
import com.oldrepublic.force.KineticParticles;

/**
 * Client set-up of the kinetic powers' effects added after Milestone 5 (particle providers for
 * {@link com.oldrepublic.force.KineticParticles}, render and tick hooks); called from {@link ForceClient#init}.
 */
public final class KineticVfx {
	public static void init() {
		ForceParticleProviders.register(KineticParticles.KINETIC_DUST, DustCloudParticle::new);
		ForceParticleProviders.register(KineticParticles.KINETIC_PEBBLE, PebbleParticle::new);
		ForceParticleProviders.register(KineticParticles.KINETIC_STREAK, AirStreakParticle::new);
		// Shards and rime hang on a creature held in Stasis exactly as its rime motes do.
		ForceParticleProviders.register(KineticParticles.KINETIC_SHARD, StasisRimeParticle::new);
		ForceParticleProviders.register(KineticParticles.KINETIC_RIME, StasisRimeParticle::new);
		KineticRender.init();
		KineticRender.onClear(PushFx::clear);
	}

	private KineticVfx() {
	}
}
