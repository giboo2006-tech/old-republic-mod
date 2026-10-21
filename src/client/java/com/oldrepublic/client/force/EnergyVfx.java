package com.oldrepublic.client.force;

import com.oldrepublic.client.force.energy.EnergyPuffParticle;
import com.oldrepublic.client.force.energy.EnergyRender;
import com.oldrepublic.client.force.particle.ForceParticleProviders;
import com.oldrepublic.force.EnergyParticles;

/**
 * Client set-up of the energy and mind powers' effects added after Milestone 5 (particle providers for
 * {@link com.oldrepublic.force.EnergyParticles}, render and tick hooks); called from {@link ForceClient#init}.
 */
public final class EnergyVfx {
	public static void init() {
		ForceParticleProviders.register(EnergyParticles.SMOKE, EnergyPuffParticle::new);
		ForceParticleProviders.register(EnergyParticles.DUST, EnergyPuffParticle::new);
		ForceParticleProviders.register(EnergyParticles.MIST, EnergyPuffParticle::new);
		EnergyRender.init();
	}

	private EnergyVfx() {
	}
}
