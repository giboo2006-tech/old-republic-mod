package com.oldrepublic.client.force.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;

/**
 * Force Sense: a thin speck of dust running from near the caster toward a sensed point, given its
 * velocity and lifetime by the spawner so it arrives as it fades. Settles into the point's cluster.
 */
public final class SenseStreamParticle extends ForceParticle {
	public SenseStreamParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
		super(level, x, y, z, dx, dy, dz, sprites);
		color(0.86f, 0.84f, 0.78f);
		alpha(0.55f);
		size(0.022f, 0.016f);
		life(14);
		fullBright();
		randomFrame();
	}

	@Override
	protected float fade(float t) {
		float in = Math.min(1f, t * 8f);
		return t < 0.75f ? in : in * (1f - (t - 0.75f) / 0.25f);
	}
}
