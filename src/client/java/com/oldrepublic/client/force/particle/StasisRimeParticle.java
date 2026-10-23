package com.oldrepublic.client.force.particle;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * A rime-like mote of Force Stasis ({@code stasis_rime}, MILESTONE_5.md 9.3): it drifts in onto a held creature, settles
 * at its spot on the body and hangs there perfectly still (it only follows the creature when it is pushed or falls), until
 * {@link #shatter} lets it fall as grey grit. Small, pale and frosted; the world lights it (no glow).
 */
public final class StasisRimeParticle extends ForceParticle {
	/** Ticks a mote takes to fade in. */
	private static final float FADE_IN = 4f;

	private @Nullable Entity anchor;
	private Vec3 offset = Vec3.ZERO;
	private Vec3 from = Vec3.ZERO;
	private int settleTicks = 1;
	private float heldAlpha = 0.8f;
	/** Age at which the mote let go and started to fall, or -1 while it is held. */
	private int fallAge = -1;
	/** Turn per tick while it flies or falls. */
	private float spin;

	public StasisRimeParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
		super(level, x, y, z, dx, dy, dz, sprites);
		color(0.86f, 0.91f, 0.95f);
		alpha(0.8f);
		size(0.05f, 0.05f);
		life(60);
		randomFrame();
	}

	@Override
	public ForceParticle alpha(float alpha) {
		this.heldAlpha = alpha;
		return super.alpha(alpha);
	}

	/**
	 * Holds the mote at {@code offset} from {@code anchor}'s feet: it drifts there from where it spawned over
	 * {@code settleTicks}, then hangs still for at most {@code ticks} in all (the stasis's length, a watchdog: the break
	 * event normally shatters it first).
	 */
	public StasisRimeParticle hold(Entity anchor, Vec3 offset, int settleTicks, int ticks) {
		this.anchor = anchor;
		this.offset = offset;
		this.from = new Vec3(x, y, z);
		this.settleTicks = Math.max(1, settleTicks);
		this.xd = 0;
		this.yd = 0;
		this.zd = 0;
		setLifetime(Math.max(ticks, settleTicks + 1));
		return this;
	}

	/** A fresh stasis on the same creature: a held mote stays where it hangs for {@code ticks} more. */
	public void extend(int ticks) {
		if (held()) {
			setLifetime(age + Math.max(1, ticks));
		}
	}

	/** Where it is now. */
	public Vec3 at() {
		return new Vec3(x, y, z);
	}

	/** Whether it still hangs on a held creature. */
	public boolean held() {
		return anchor != null && isAlive();
	}

	/**
	 * Flung off as a splinter of frost when the stasis breaks: it flies with {@code (dx, dy, dz)}, tumbles under a little
	 * gravity and fades as it falls, keeping its pale colour.
	 */
	public StasisRimeParticle burst(double dx, double dy, double dz) {
		anchor = null;
		fallAge = age;
		this.xd = dx;
		this.yd = dy;
		this.zd = dz;
		this.spin = (random.nextFloat() - 0.5f) * 0.8f;
		drag(0.9f);
		fall(0.45f, true);
		setLifetime(age + 12 + random.nextInt(10));
		return this;
	}

	/** The stasis ended: the mote loses its frost and falls as grit, scattering a little. */
	public void shatter() {
		if (fallAge >= 0) {
			return;
		}

		anchor = null;
		fallAge = age;
		this.xd = (random.nextDouble() - 0.5) * 0.04;
		this.yd = 0.01 + random.nextDouble() * 0.02;
		this.zd = (random.nextDouble() - 0.5) * 0.04;
		this.spin = (random.nextFloat() - 0.5f) * 0.3f;
		color(0.6f, 0.62f, 0.64f);
		drag(0.94f);
		fall(0.06f, true);
		setLifetime(age + 16 + random.nextInt(10));
	}

	@Override
	public void tick() {
		if (anchor != null && (anchor.isRemoved() || !anchor.isAlive())) {
			shatter();
		}

		if (anchor != null) {
			this.xo = this.x;
			this.yo = this.y;
			this.zo = this.z;
			Vec3 spot = anchor.position().add(offset);
			float t = Mth.clamp((age + 1) / (float) settleTicks, 0f, 1f);
			// Eased in: the mote slows as it settles, then does not move at all.
			float s = 1f - (1f - t) * (1f - t);
			setPos(Mth.lerp(s, from.x, spot.x), Mth.lerp(s, from.y, spot.y), Mth.lerp(s, from.z, spot.z));
			age++;
			setAlpha(heldAlpha * Math.min(1f, age / FADE_IN));

			if (age >= lifetime) {
				shatter();
			}

			return;
		}

		this.oRoll = this.roll;
		super.tick();

		if (isAlive() && !onGround) {
			this.roll += spin;
		}
	}

	@Override
	public float getQuadSize(float partialTick) {
		float size = super.getQuadSize(partialTick);

		if (fallAge >= 0) {
			return size;
		}

		// Frost forming: it grows in as it settles.
		float t = Mth.clamp((age + partialTick) / settleTicks, 0f, 1f);
		return size * (0.25f + 0.75f * (1f - (1f - t) * (1f - t)));
	}

	@Override
	protected float fade(float t) {
		if (fallAge < 0) {
			return 1f;
		}

		// Falling: fades over what is left of its life.
		float left = Math.max(1f, lifetime - fallAge);
		return Mth.clamp(1f - (age - fallAge) / left, 0f, 1f);
	}
}
