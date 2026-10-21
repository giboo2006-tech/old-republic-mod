package com.oldrepublic.client.force.kinetic;

import com.oldrepublic.client.force.particle.ForceParticle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.Mth;

/**
 * A billowing cloud of dust ({@code kinetic_dust}): it bursts out at the speed it was thrown, swells quickly and then more
 * slowly, turns as it rolls, loses its speed to the air and settles or drifts up, thinning away. Tinted with the ground's
 * colour and lit by the world (no glow), so it is pale by day and dim at night like real dust.
 */
public final class DustCloudParticle extends ForceParticle {
	private float from = 0.3f;
	private float to = 0.9f;
	private float spin;
	private float lift;
	/** Where in its life (0 to 1) it is thickest; it thins away after. */
	private float hold = 0.25f;

	public DustCloudParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
		super(level, x, y, z, dx, dy, dz, sprites);
		color(0.62f, 0.57f, 0.49f);
		alpha(0.6f);
		size(0.3f, 0.9f);
		life(40);
		drag(0.86f);
		fall(0f, true);
		randomFrame();
		this.roll = random.nextFloat() * Mth.TWO_PI;
		this.oRoll = this.roll;
		this.spin = (random.nextFloat() - 0.5f) * 0.06f;
	}

	@Override
	public ForceParticle size(float start, float end) {
		this.from = start;
		this.to = end;
		return super.size(start, end);
	}

	/** Turn per tick, in radians (it slows as the cloud does). */
	public DustCloudParticle spin(float radians) {
		this.spin = radians;
		return this;
	}

	/** A steady drift per tick once the throw is spent: up for fine dust, down for heavy grit. */
	public DustCloudParticle lift(float perTick) {
		this.lift = perTick;
		return this;
	}

	/** Where in its life (0 to 1) it stops thickening and starts to thin away. */
	public DustCloudParticle hold(float t) {
		this.hold = Mth.clamp(t, 0.05f, 0.9f);
		return this;
	}

	@Override
	public void tick() {
		this.oRoll = this.roll;
		super.tick();

		if (!isAlive()) {
			return;
		}

		this.roll += spin;
		this.spin *= 0.96f;
		this.yd += lift * (1f - friction);
	}

	@Override
	public float getQuadSize(float partialTick) {
		float t = Mth.clamp((age + partialTick) / (float) lifetime, 0f, 1f);
		// Swells fast while the throw lasts, then slowly.
		float grown = 1f - (1f - t) * (1f - t) * (1f - t);
		return Mth.lerp(grown, from, to);
	}

	@Override
	protected float fade(float t) {
		float in = Math.min(1f, t * 10f);

		if (t <= hold) {
			return in;
		}

		float out = 1f - (t - hold) / (1f - hold);
		return in * out * (0.4f + 0.6f * out);
	}
}
