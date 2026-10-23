package com.oldrepublic.client.force.barrier;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.force.particle.ForceParticle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * A mote of dust (or a leaf, {@code kinetic_leaf}) held at the surface of a Force Barrier: it drifts
 * slowly around the caster on a sphere of the barrier's radius, bobbing a little, until
 * {@link #fling} throws it outward (the pulse) or {@link #drop} lets it fall (the barrier ends). The
 * sphere is only ever shown by these motes and the air they ride: no shell, no glow. A leaf
 * {@link #flutter}s: it turns over as it rides and sways from side to side as it falls.
 */
public final class HeldDustParticle extends ForceParticle {
	private @Nullable Entity anchor;
	private double radius;
	private double yaw;
	private double pitch;
	private double yawSpeed;
	/** The lowest it wanders on the sphere (radians, up positive): above the ground the sphere dips into. */
	private double floor = -1.1;
	private double bob;
	private float heldAlpha = 0.75f;
	/** Sideways sway per tick while it flies or falls free (a leaf), or 0. */
	private float flutter;
	private float spin;
	private float phase;

	public HeldDustParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
		super(level, x, y, z, dx, dy, dz, sprites);
		alpha(0.75f);
		size(0.07f, 0.07f);
		life(80);
		randomFrame();
	}

	@Override
	public ForceParticle alpha(float alpha) {
		this.heldAlpha = alpha;
		return super.alpha(alpha);
	}

	/**
	 * Holds the mote on the sphere of {@code radius} around {@code anchor}'s chest at the angles
	 * given (radians), drifting sideways at {@code yawSpeed} radians a tick.
	 */
	public HeldDustParticle hold(Entity anchor, double radius, double yaw, double pitch, double yawSpeed) {
		this.anchor = anchor;
		this.radius = radius;
		this.yaw = yaw;
		this.pitch = pitch;
		this.yawSpeed = yawSpeed;
		this.bob = random.nextDouble() * Math.PI * 2;
		place();
		this.xo = this.x;
		this.yo = this.y;
		this.zo = this.z;
		return this;
	}

	/** Keeps it on the sphere no lower than {@code pitch} (radians), so it never wanders into the ground. */
	public HeldDustParticle floor(double pitch) {
		this.floor = Math.min(pitch, 1.1);
		this.pitch = Math.max(this.pitch, this.floor);
		place();
		this.xo = this.x;
		this.yo = this.y;
		this.zo = this.z;
		return this;
	}

	/** Flutters like a leaf: it turns over as it rides and sways from side to side by {@code amount} as it falls. */
	public HeldDustParticle flutter(float amount) {
		this.flutter = amount;
		this.spin = (random.nextFloat() - 0.5f) * 0.25f;
		this.phase = random.nextFloat() * Mth.TWO_PI;
		this.roll = random.nextFloat() * Mth.TWO_PI;
		this.oRoll = this.roll;
		return this;
	}

	/** Whether it still sits on a barrier. */
	public boolean held() {
		return anchor != null && isAlive();
	}

	/** Throws the mote outward from the centre at {@code speed} blocks a tick (the pulse). */
	public void fling(double speed) {
		Vec3 out = offset().normalize();
		release();
		this.xd = out.x * speed;
		this.yd = out.y * speed * 0.5 + 0.05;
		this.zd = out.z * speed;
		drag(0.86f);
		fall(0.04f, false);
		setLifetime(age + 16 + random.nextInt(8));
	}

	/** Lets the mote fall where it is (the barrier ended or broke). */
	public void drop(double scatter) {
		Vec3 out = offset().normalize();
		release();
		this.xd = out.x * scatter;
		this.yd = 0.02;
		this.zd = out.z * scatter;
		drag(0.9f);
		fall(0.06f, true);
		setLifetime(age + 20 + random.nextInt(10));
	}

	private void release() {
		this.anchor = null;
	}

	private Vec3 offset() {
		double cos = Math.cos(pitch);
		return new Vec3(Math.cos(yaw) * cos, Math.sin(pitch), Math.sin(yaw) * cos).scale(Math.max(0.1, radius));
	}

	private void place() {
		if (anchor == null) {
			return;
		}

		Vec3 centre = anchor.position().add(0, anchor.getBbHeight() * 0.6, 0);
		Vec3 at = centre.add(offset()).add(0, Math.sin(bob) * 0.06, 0);
		setPos(at.x, at.y, at.z);
	}

	@Override
	public void tick() {
		this.oRoll = this.roll;

		if (flutter > 0f) {
			phase += 0.3f;
			this.roll += spin * (anchor != null ? 0.4f : 1f) + Mth.sin(phase) * 0.05f;
		}

		if (anchor != null) {
			if (anchor.isRemoved() || !anchor.isAlive()) {
				drop(0.02);
			} else {
				this.xo = this.x;
				this.yo = this.y;
				this.zo = this.z;
				yaw += yawSpeed;
				// A slow wander up and down the sphere, never settling into a band.
				pitch = Mth.clamp(pitch + (random.nextDouble() - 0.5) * 0.02, floor, 1.2);
				bob += 0.12;
				place();
				age++;
				setAlpha(heldAlpha * fade(Mth.clamp(age / (float) lifetime, 0f, 1f)));

				if (age >= lifetime) {
					remove();
				}

				return;
			}
		}

		super.tick();

		if (flutter > 0f && isAlive() && !onGround) {
			// A falling leaf: it sways from side to side and slips sideways.
			this.xd += Mth.cos(phase) * flutter;
			this.zd += Mth.sin(phase * 0.73f) * flutter;
		}
	}

	@Override
	protected float fade(float t) {
		// In over a third of a second, however long it is held.
		float in = Math.min(1f, age / 6f);
		return t < 0.8f ? in : in * (1f - (t - 0.8f) / 0.2f);
	}
}
