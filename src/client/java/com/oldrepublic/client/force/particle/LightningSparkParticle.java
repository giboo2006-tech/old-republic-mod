package com.oldrepublic.client.force.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;

/** A tiny bright speck: fingertip crackle while lightning charges, contact sparks, disarm sparks. */
public final class LightningSparkParticle extends ForceParticle {
	public LightningSparkParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
		super(level, x, y, z, dx, dy, dz, sprites);
		color(0.9f, 0.9f, 1f);
		alpha(1f);
		size(0.035f, 0.015f);
		life(4 + random.nextInt(4));
		drag(0.82f);
		fullBright();
		randomFrame();
	}

	@Override
	protected float fade(float t) {
		return 1f - t * t;
	}
}
