package com.oldrepublic.client.force.energy;

import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.oldrepublic.client.fx.GlowRibbon;
import com.oldrepublic.client.fx.Vfx;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * A mote of light with a short fading trail: drawn in to a hand or a chest (steering for an {@link Anchor}, perhaps
 * spiralling round it on the way), circling a body (rising round it, or drifting dazed round a head), or drifting free
 * (rising, falling, wandering). It fades in, may twinkle, and either fades out at the end of its life or vanishes into what
 * it was drawn to. Set up with the fluent setters before it is added.
 */
public final class Mote implements EnergyRender.Shape {
	/** Ticks of the path the trail shows. */
	private static final int TRAIL = 5;
	private static final Vec3 UP = new Vec3(0, 1, 0);

	private final long owner;
	private final Palette palette;
	private final float seed;
	private double x;
	private double y;
	private double z;
	private double xo;
	private double yo;
	private double zo;
	private double vx;
	private double vy;
	private double vz;
	/** The last positions, newest last, for the trail. */
	private final double[] trail = new double[TRAIL * 3];
	private int trailed;
	private int age;
	private int life = 20;
	private float size = 1f;
	private float endSize = 1f;
	private float strength = 1f;
	private float gravity;
	private float drag = 1f;
	private float wander;
	private float fadeIn = 3f;
	private float fadeOut = 6f;
	private boolean twinkle;
	private boolean trailOn = true;
	private @Nullable Anchor seek;
	private float speed;
	private float steer = 0.25f;
	private float swirl;
	private float arrive = 0.12f;
	/** Circling: the centre, the radius and how it grows, the angle and its turn a tick, the height and its rise a tick. */
	private @Nullable Anchor orbit;
	private float orbitRadius;
	private float orbitGrow;
	private float orbitAngle;
	private float orbitSpin;
	private float orbitHeight;
	private float orbitRise;
	/** This frame. */
	private Vector3f[] points = new Vector3f[0];
	private float alpha;
	private float near;
	private float sizeNow;
	private boolean showing;

	private Mote(long owner, Vec3 pos, Vec3 velocity, Palette palette) {
		this.owner = owner;
		this.x = this.xo = pos.x;
		this.y = this.yo = pos.y;
		this.z = this.zo = pos.z;
		this.vx = velocity.x;
		this.vy = velocity.y;
		this.vz = velocity.z;
		this.palette = palette;
		this.seed = EnergyKit.RANDOM.nextFloat() * 100f;

		for (int i = 0; i < TRAIL; i++) {
			trail[i * 3] = x;
			trail[i * 3 + 1] = y;
			trail[i * 3 + 2] = z;
		}
	}

	/** A mote of {@code palette} at {@code pos}, moving {@code velocity} blocks a tick. */
	public static Mote at(long owner, Vec3 pos, Vec3 velocity, Palette palette) {
		return new Mote(owner, pos, velocity, palette);
	}

	/** Lives {@code ticks}, fading in over {@code in} ticks and out over the last {@code out}. */
	public Mote life(int ticks, float in, float out) {
		this.life = Math.max(1, ticks);
		this.fadeIn = in;
		this.fadeOut = Math.max(0.5f, out);
		return this;
	}

	/** Size, as a multiple of the palette's widths, from birth to death. */
	public Mote size(float start, float end) {
		this.size = start;
		this.endSize = end;
		return this;
	}

	public Mote strength(float strength) {
		this.strength = strength;
		return this;
	}

	/** Falls by {@code gravity} a tick (negative rises) and keeps {@code drag} of its speed each tick while it drifts free. */
	public Mote drift(float gravity, float drag) {
		this.gravity = gravity;
		this.drag = drag;
		return this;
	}

	/** Wanders by up to {@code amount} blocks a tick, a tick at a time. */
	public Mote wander(float amount) {
		this.wander = amount;
		return this;
	}

	/** Twinkles (never with flashing effects off). */
	public Mote twinkle() {
		this.twinkle = Vfx.flashing();
		return this;
	}

	/** No trail: a point of light only. */
	public Mote noTrail() {
		this.trailOn = false;
		return this;
	}

	/**
	 * Steers for {@code target} at {@code speed} blocks a tick (turning by {@code steer}, 0 to 1, each tick), spiralling round
	 * it by {@code swirl} (a share of its speed, sideways; negative the other way round), and vanishes within {@code arrive}
	 * blocks of it.
	 */
	public Mote seek(Anchor target, float speed, float steer, float swirl, float arrive) {
		this.seek = target;
		this.speed = speed;
		this.steer = steer;
		this.swirl = swirl;
		this.arrive = arrive;
		return this;
	}

	/**
	 * Circles {@code centre}: starting {@code angle} radians round it (0 east, a quarter turn south) at {@code radius} blocks
	 * and {@code height} blocks above it, turning {@code spin} radians a tick (negative the other way), rising {@code rise}
	 * and moving out {@code grow} blocks a tick, a little unevenly. If the centre is lost it drifts on free.
	 */
	public Mote orbit(Anchor centre, float radius, float angle, float height, float spin, float rise, float grow) {
		this.orbit = centre;
		this.orbitRadius = radius;
		this.orbitAngle = angle;
		this.orbitHeight = height;
		this.orbitSpin = spin;
		this.orbitRise = rise;
		this.orbitGrow = grow;
		Vec3 c = centre.at(1f);

		if (c != null) {
			x = xo = c.x + Mth.cos(angle) * radius;
			y = yo = c.y + height;
			z = zo = c.z + Mth.sin(angle) * radius;

			for (int i = 0; i < TRAIL; i++) {
				trail[i * 3] = x;
				trail[i * 3 + 1] = y;
				trail[i * 3 + 2] = z;
			}
		}

		return this;
	}

	@Override
	public boolean tick(ClientLevel level) {
		if (++age >= life) {
			return false;
		}

		System.arraycopy(trail, 3, trail, 0, (TRAIL - 1) * 3);
		trail[(TRAIL - 1) * 3] = x;
		trail[(TRAIL - 1) * 3 + 1] = y;
		trail[(TRAIL - 1) * 3 + 2] = z;
		trailed = Math.min(TRAIL, trailed + 1);
		xo = x;
		yo = y;
		zo = z;

		if (orbit != null) {
			Vec3 c = orbit.at(1f);

			if (c != null) {
				orbitAngle += orbitSpin * (1f + 0.25f * EnergyKit.noise(seed + age * 0.31f));
				orbitHeight += orbitRise;
				orbitRadius = Math.max(0f, orbitRadius + orbitGrow);
				float r = orbitRadius * (1f + 0.12f * EnergyKit.noise(seed * 1.7f + age * 0.23f));
				double nx = c.x + Mth.cos(orbitAngle) * r;
				double ny = c.y + orbitHeight;
				double nz = c.z + Mth.sin(orbitAngle) * r;
				// Keeps its speed if the centre is lost.
				vx = nx - x;
				vy = ny - y;
				vz = nz - z;
				x = nx;
				y = ny;
				z = nz;
				return true;
			}

			orbit = null;
		}

		Vec3 target = seek != null ? seek.at(1f) : null;

		if (target != null) {
			Vec3 to = target.subtract(x, y, z);
			double distance = to.length();

			if (distance < arrive) {
				return false;
			}

			Vec3 desired = to.scale(speed / distance);

			if (swirl != 0f) {
				Vec3 around = to.cross(UP);

				if (around.lengthSqr() > 1.0e-6) {
					desired = desired.add(around.normalize().scale(swirl * speed * Math.min(1.0, distance)));
				}
			}

			vx += (desired.x - vx) * steer;
			vy += (desired.y - vy) * steer;
			vz += (desired.z - vz) * steer;
		} else {
			seek = null;
			vy -= gravity;
			vx *= drag;
			vy *= drag;
			vz *= drag;
		}

		if (wander > 0f) {
			vx += EnergyKit.jitter1(wander);
			vy += EnergyKit.jitter1(wander);
			vz += EnergyKit.jitter1(wander);
		}

		x += vx;
		y += vy;
		z += vz;
		return true;
	}

	@Override
	public long owner() {
		return owner;
	}

	@Override
	public void prepare(EnergyRender.Frame frame) {
		float age = this.age + frame.partial;
		float in = fadeIn <= 0f ? 1f : Mth.clamp(age / fadeIn, 0f, 1f);
		float out = Mth.clamp((life - age) / fadeOut, 0f, 1f);
		float glitter = twinkle ? 0.65f + 0.35f * EnergyKit.noise(seed + age * 0.9f) : 1f;
		alpha = strength * in * out * glitter;
		showing = alpha > 0.004f;

		if (!showing) {
			return;
		}

		Vector3f head = frame.rel(Mth.lerp(frame.partial, xo, x), Mth.lerp(frame.partial, yo, y), Mth.lerp(frame.partial, zo, z));
		int n = trailOn ? trailed : 0;
		points = new Vector3f[n + 1];

		for (int i = 0; i < n; i++) {
			int k = TRAIL - n + i;
			points[i] = frame.rel(trail[k * 3], trail[k * 3 + 1], trail[k * 3 + 2]);
		}

		points[n] = head;
		near = frame.near(head);
		sizeNow = Mth.lerp(Mth.clamp(age / life, 0f, 1f), size, endSize) * near;
	}

	@Override
	public void glow(EnergyRender.Frame frame, PoseStack.Pose pose, VertexConsumer buffer) {
		if (!showing) {
			return;
		}

		Vector3f head = points[points.length - 1];
		GlowRibbon.disc(pose, buffer, frame.eye, head, palette.glowRadius() * sizeNow, palette.flare(), alpha);

		if (points.length > 1) {
			trail(pose, buffer, frame, palette.glow(), palette.glowRadius() * 0.45f, alpha * 0.8f);
		}
	}

	@Override
	public void solid(EnergyRender.Frame frame, PoseStack.Pose pose, VertexConsumer buffer) {
		if (!showing) {
			return;
		}

		GlowRibbon.disc(pose, buffer, frame.eye, points[points.length - 1], palette.coreRadius() * sizeNow, palette.core(), Math.min(1f, alpha));

		if (points.length > 1) {
			trail(pose, buffer, frame, palette.core(), palette.coreRadius() * 0.6f, Math.min(1f, alpha) * 0.7f);
		}
	}

	/** The trail: thin and faint at its tail, as wide as {@code radius} and as strong as {@code strength} at the head. */
	private void trail(PoseStack.Pose pose, VertexConsumer buffer, EnergyRender.Frame frame, GlowRibbon.Profile profile, float radius, float strength) {
		int n = points.length;
		float[] radii = new float[n];
		float[] alphas = new float[n];

		for (int i = 0; i < n; i++) {
			float t = (float) i / (n - 1);
			radii[i] = radius * sizeNow * (0.25f + 0.75f * t);
			alphas[i] = strength * t * t;
		}

		GlowRibbon.beam(pose, buffer, frame.eye, points, radii, alphas, profile, false, false);
	}
}
