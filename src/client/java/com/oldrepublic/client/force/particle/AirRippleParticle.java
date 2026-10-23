package com.oldrepublic.client.force.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;

/** A faint, colourless shell of bent air that grows as it travels. */
public final class AirRippleParticle extends ForceParticle {
	public AirRippleParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
		super(level, x, y, z, dx, dy, dz, sprites);
		color(0.92f, 0.94f, 0.96f);
		alpha(0.15f);
		size(0.3f, 0.9f);
		animated();
	}
}
