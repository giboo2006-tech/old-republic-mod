package com.oldrepublic.client.force.kinetic;

import com.oldrepublic.client.force.particle.ForceParticle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.Mth;

/**
 * A pebble or chip of earth ({@code kinetic_pebble}): it tumbles through the air under gravity, lands and lies still, then
 * fades. It can also hover: held up in the air with a slight tremble (Telekinetic Lift, a charging push) until let go.
 * Tinted with the ground's colour and lit by the world.
 */
public final class PebbleParticle extends ForceParticle {
	private float spin;
	private float weight = 1f;
	/** Ticks left of hovering; 0 when it flies or falls. */
	private int hover;
	private double hoverY;

	public PebbleParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
		super(level, x, y, z, dx, dy, dz, sprites);
		color(0.5f, 0.47f, 0.42f);
		alpha(1f);
		size(0.07f, 0.07f);
		life(30);
		drag(0.98f);
		fall(1f, true);
		randomFrame();
		this.roll = random.nextFloat() * Mth.TWO_PI;
		this.oRoll = this.roll;
		this.spin = (random.nextFloat() - 0.5f) * 0.7f;
	}

	/** How fast it falls: 1 a stone, less for clods and grit. */
	public PebbleParticle weight(float weight) {
		this.weight = weight;
		fall(weight, true);
		return this;
	}

	/** Hangs in the air near where it is for {@code ticks}, rising by {@code rise} blocks, trembling, then falls. */
	public PebbleParticle hover(int ticks, double rise) {
		this.hover = Math.max(0, ticks);
		this.hoverY = y + rise;
		this.gravity = 0f;
		this.spin *= 0.15f;
		return this;
	}

	/** Ends a hover: it drops (with a little push of {@code dx, dy, dz}). */
	public void drop(double dx, double dy, double dz) {
		if (hover > 0) {
			hover = 0;
			this.xd = dx;
			this.yd = dy;
			this.zd = dz;
			this.gravity = weight;
			this.spin = (random.nextFloat() - 0.5f) * 0.6f;
		}
	}

	/** Whether it is still hovering. */
	public boolean hovering() {
		return hover > 0 && isAlive();
	}

	@Override
	public void tick() {
		this.oRoll = this.roll;

		if (hover > 0) {
			hover--;
			// Eases up to its hover height, trembling a little on the way.
			this.xd = (random.nextFloat() - 0.5f) * 0.012f;
			this.zd = (random.nextFloat() - 0.5f) * 0.012f;
			this.yd = (hoverY - y) * 0.12 + (random.nextFloat() - 0.5f) * 0.01f;

			if (hover == 0) {
				this.gravity = weight;
				this.spin = (random.nextFloat() - 0.5f) * 0.5f;
			}
		}

		super.tick();

		if (!isAlive()) {
			return;
		}

		if (!onGround) {
			this.roll += spin;
		}
	}

	@Override
	protected float fade(float t) {
		return t < 0.8f ? 1f : 1f - (t - 0.8f) / 0.2f;
	}
}
