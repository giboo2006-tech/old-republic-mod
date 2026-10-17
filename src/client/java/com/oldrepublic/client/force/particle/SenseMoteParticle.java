package com.oldrepublic.client.force.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.Mth;

/**
 * Force Sense: one slow-drifting mote of a sensed point's cluster, tinted at spawn by what it marks
 * (ember, amber, grey-white, pale blue, pale gold). It carries its own faint light, so it reads in a
 * dark tomb, and swells and fades softly: never a glow outline or a ring.
 */
public final class SenseMoteParticle extends ForceParticle {
	public SenseMoteParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
		super(level, x, y, z, dx, dy, dz, sprites);
		color(0.9f, 0.88f, 0.82f);
		alpha(0.65f);
		size(0.035f, 0.025f);
		life(30);
		drag(0.94f);
		fullBright();
		randomFrame();
	}

	@Override
	protected float fade(float t) {
		return Mth.sin(t * Mth.PI);
	}
}
