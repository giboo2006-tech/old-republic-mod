package com.oldrepublic.client.force.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;

/** Small dust mote (tinted to the ground it came from) or a dull falling mote. */
public final class DustMoteParticle extends ForceParticle {
	public DustMoteParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
		super(level, x, y, z, dx, dy, dz, sprites);
		alpha(0.7f);
		size(0.07f, 0.05f);
		life(14);
		randomFrame();
	}

	@Override
	protected float fade(float t) {
		float in = Math.min(1f, t * 5f);
		return t < 0.7f ? in : in * (1f - (t - 0.7f) / 0.3f);
	}
}
