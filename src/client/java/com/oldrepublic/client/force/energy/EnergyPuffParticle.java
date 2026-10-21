package com.oldrepublic.client.force.energy;

import com.oldrepublic.client.force.particle.ForceParticle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.Mth;

/**
 * A soft puff that drifts, turns slowly and spreads as it thins: scorch smoke, dust kicked off the ground, a wisp of mist
 * ({@code energy_smoke}, {@code energy_dust}, {@code energy_mist}). Tinted and sized by the effect that spawns it.
 */
public final class EnergyPuffParticle extends ForceParticle {
	private final float spin;

	public EnergyPuffParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
		super(level, x, y, z, dx, dy, dz, sprites);
		alpha(0.6f);
		size(0.2f, 0.45f);
		life(24);
		drag(0.9f);
		randomFrame();
		this.roll = random.nextFloat() * Mth.TWO_PI;
		this.oRoll = this.roll;
		this.spin = (random.nextFloat() - 0.5f) * 0.09f;
	}

	@Override
	public void tick() {
		oRoll = roll;
		roll += spin;
		super.tick();
	}

	@Override
	protected float fade(float t) {
		float in = Math.min(1f, t * 5f);
		return in * (1f - t) * (1f - 0.4f * t);
	}
}
