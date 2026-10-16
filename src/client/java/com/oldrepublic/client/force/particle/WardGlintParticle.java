package com.oldrepublic.client.force.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.Mth;

/** Serene Ward: a sparse pale-gold speck that twinkles once on the body's outline. */
public final class WardGlintParticle extends ForceParticle {
	public WardGlintParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
		super(level, x, y, z, dx, dy, dz, sprites);
		color(1f, 0.93f, 0.74f);
		alpha(0.75f);
		size(0.02f, 0.05f);
		life(12 + random.nextInt(6));
		fullBright();
		animated();
	}

	@Override
	protected float fade(float t) {
		return Mth.sin(t * Mth.PI);
	}
}
