package com.oldrepublic.client.force.particle;

import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.world.phys.Vec3;

/**
 * A dark-red mote of Force Drain ({@code drain_mote}, MILESTONE_5.md 9.5), drawn out of the target toward the caster's
 * hand: once given a goal ({@link #seek}) it steers for it every tick, so the stream bends after a moving caster, and it
 * is gone as it reaches the hand. Without a goal it only drifts and fades.
 */
public final class DrainMoteParticle extends ForceParticle {
	/** Distance at which a mote has arrived. */
	private static final double ARRIVED = 0.2;
	/** How much of the wanted velocity a mote takes each tick (the rest is its own drift, so the stream wavers). */
	private static final double STEER = 0.3;

	private @Nullable Supplier<Vec3> goal;
	private double speed = 0.12;

	public DrainMoteParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
		super(level, x, y, z, dx, dy, dz, sprites);
		color(0.5f, 0.05f, 0.07f);
		alpha(0.85f);
		size(0.06f, 0.035f);
		life(30);
		randomFrame();
	}

	/** Steers the mote toward {@code goal} (read every tick) at up to {@code speed} blocks a tick. */
	public DrainMoteParticle seek(Supplier<Vec3> goal, double speed) {
		this.goal = goal;
		this.speed = speed;
		return this;
	}

	@Override
	public void tick() {
		if (goal != null && isAlive()) {
			Vec3 to = goal.get().subtract(x, y, z);
			double distance = to.length();

			if (distance < ARRIVED) {
				remove();
				return;
			}

			Vec3 want = to.scale(Math.min(speed, distance) / distance);
			this.xd += (want.x - xd) * STEER;
			this.yd += (want.y - yd) * STEER;
			this.zd += (want.z - zd) * STEER;
		}

		super.tick();
	}

	@Override
	protected float fade(float t) {
		float in = Math.min(1f, t * 8f);
		return t < 0.75f ? in : in * (1f - (t - 0.75f) / 0.25f);
	}
}
