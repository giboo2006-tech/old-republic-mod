package com.oldrepublic.client.force;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.force.energy.DangerPrickle;
import com.oldrepublic.client.force.energy.EnergyPuffParticle;
import com.oldrepublic.client.force.energy.EnergyRender;
import com.oldrepublic.client.force.particle.ForceParticleProviders;
import com.oldrepublic.force.EnergyParticles;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

/**
 * Client set-up of the energy and mind powers' effects added after Milestone 5 (particle providers for
 * {@link com.oldrepublic.force.EnergyParticles}, the render and tick hooks of their light, and Danger Sense's prickle at the
 * edge of the view); called from {@link ForceClient#init}.
 */
public final class EnergyVfx {
	public static void init() {
		ForceParticleProviders.register(EnergyParticles.SMOKE, EnergyPuffParticle::new);
		ForceParticleProviders.register(EnergyParticles.DUST, EnergyPuffParticle::new);
		ForceParticleProviders.register(EnergyParticles.MIST, EnergyPuffParticle::new);
		EnergyRender.init();
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, OldRepublicMod.id("danger_prickle"), DangerPrickle::extract);
	}

	private EnergyVfx() {
	}
}
