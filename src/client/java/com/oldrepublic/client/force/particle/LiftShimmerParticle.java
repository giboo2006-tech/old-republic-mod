package com.oldrepublic.client.force.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.Mth;

/**
 * Telekinetic Lift: a faint, colourless wisp of bent air rising under a lifted target. Many of them,
 * staggered at different heights, make the shimmer column; none lies flat at the ground, so the
 * column never reads as a disc or a ring.
 */
public final class LiftShimmerParticle extends ForceParticle {
	public LiftShimmerParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
		super(level, x, y, z, dx, dy, dz, sprites);
		color(0.93f, 0.94f, 0.95f);
		alpha(0.14f);
		size(0.18f, 0.32f);
		life(16);
		drag(0.96f);
		animated();
	}

	@Override
	protected float fade(float t) {
		return Mth.sin(t * Mth.PI);
	}
}
