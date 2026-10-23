package com.oldrepublic.client.force.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;

/** Pressure-and-dust puff where a push lands. */
public final class PushImpactParticle extends ForceParticle {
	public PushImpactParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
		super(level, x, y, z, dx, dy, dz, sprites);
		color(0.86f, 0.82f, 0.74f);
		alpha(0.45f);
		size(0.3f, 0.7f);
		life(7);
		drag(0.82f);
		animated();
	}

	@Override
	protected float fade(float t) {
		return 1f - t * t;
	}
}
