package com.oldrepublic.client.force.energy;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.oldrepublic.client.fx.GlowRibbon;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * A living strand between two {@link Anchor}s: a thin line that writhes as waves run along it (still at both ends), may
 * wind round the line between them, with brighter beads of light flowing down it from its start to its end. Life torn out
 * of a drained body, Shared Light reaching an ally, a thread of the Force running to what Sense found, a curl of dark vapour
 * climbing a frightened body. It grows out from its start, is held tick by tick by its effect ({@link #keep}) and fades
 * when let go. Set up with the fluent setters before it is added.
 */
public final class Tendril implements EnergyRender.Shape {
	private static final int POINTS = 22;
	private static final Vec3 UP = new Vec3(0, 1, 0);

	private final long owner;
	private final Anchor from;
	private final Anchor to;
	private final Palette palette;
	private final long born;
	private final float seed;
	private int life = 20;
	private float fadeIn = 3f;
	private float fadeOut = 5f;
	private float grow = 4f;
	private float width = 1f;
	private float endWidth = 1f;
	private float strength = 1f;
	/** Extra strength of a pulse, easing away. */
	private float surge;
	/** The writhing: how far it swings (blocks), how many waves along it, and how fast they run (waves a tick). */
	private float amplitude = 0.12f;
	private float waves = 1.5f;
	private float speed = 0.08f;
	/** Beads of light running down it: how many along it, how fast (lengths a tick), and how much brighter. */
	private float beads;
	private float beadSpeed = 0.06f;
	private float beadGain = 0.8f;
	/** Winding: turns round the line from end to end, how far from it (blocks), and how fast the turns travel (turns a tick). */
	private float coilTurns;
	private float coilRadius;
	private float coilSpeed;
	/** This frame. */
	private Vector3f[] points = new Vector3f[0];
	private float[] alphas = new float[0];
	private float[] radii = new float[0];
	private boolean showing;

	private Tendril(long owner, Anchor from, Anchor to, Palette palette) {
		this.owner = owner;
		this.from = from;
		this.to = to;
		this.palette = palette;
		this.born = EnergyRender.ticks();
		this.seed = EnergyKit.RANDOM.nextFloat() * 100f;
	}

	/** A strand of {@code palette} from {@code from} to {@code to}, belonging to {@code owner}. */
	public static Tendril between(long owner, Anchor from, Anchor to, Palette palette) {
		return new Tendril(owner, from, to, palette);
	}

	/** Lasts {@code ticks}, fading in over {@code in} and out over the last {@code out}, growing from its start over {@code grow}. */
	public Tendril life(int ticks, float in, float out, float grow) {
		this.life = Math.max(1, ticks);
		this.fadeIn = in;
		this.fadeOut = Math.max(0.5f, out);
		this.grow = grow;
		return this;
	}

	/** Width (a multiple of the palette's) at its start and at its end. */
	public Tendril width(float start, float end) {
		this.width = start;
		this.endWidth = end;
		return this;
	}

	public Tendril strength(float strength) {
		this.strength = strength;
		return this;
	}

	/** Swings up to {@code amplitude} blocks, {@code waves} waves along its length, running {@code speed} waves a tick. */
	public Tendril writhe(float amplitude, float waves, float speed) {
		this.amplitude = amplitude;
		this.waves = waves;
		this.speed = speed;
		return this;
	}

	/** {@code count} beads of light along it, flowing {@code speed} lengths a tick from start to end, {@code gain} brighter. */
	public Tendril beads(float count, float speed, float gain) {
		this.beads = count;
		this.beadSpeed = speed;
		this.beadGain = gain;
		return this;
	}

	/** Winds {@code turns} times round the line between its ends, {@code radius} blocks out, the turns travelling {@code speed} a tick. */
	public Tendril coil(float turns, float radius, float speed) {
		this.coilTurns = turns;
		this.coilRadius = radius;
		this.coilSpeed = speed;
		return this;
	}

	/** Keeps a held strand showing for at least {@code ticks} more. */
	public void keep(int ticks) {
		life = (int) Math.max(life, EnergyRender.ticks() - born + ticks);
	}

	/** Lets a held strand fade out now over {@code ticks}. */
	public void release(int ticks) {
		long age = EnergyRender.ticks() - born;
		life = (int) Math.min(life, age + Math.max(1, ticks));
		fadeOut = Math.max(0.5f, ticks);
	}

	/** Brighter and wider for a moment ({@code extra} more, easing back over the next ticks): a pulse. */
	public void surge(float extra) {
		surge = Math.max(surge, extra);
	}

	@Override
	public boolean tick(ClientLevel level) {
		surge *= 0.72f;
		return EnergyRender.ticks() - born < life;
	}

	@Override
	public long owner() {
		return owner;
	}

	@Override
	public void prepare(EnergyRender.Frame frame) {
		float age = EnergyRender.ticks() - born + frame.partial;
		float in = fadeIn <= 0f ? 1f : Mth.clamp(age / fadeIn, 0f, 1f);
		float out = Mth.clamp((life - age) / fadeOut, 0f, 1f);
		float alpha = strength * (1f + surge) * in * out;
		Vec3 a = from.at(frame.partial);
		Vec3 b = to.at(frame.partial);
		showing = a != null && b != null && alpha > 0.004f;

		if (!showing) {
			return;
		}

		Vec3 along = b.subtract(a);
		double length = along.length();

		if (length < 1.0e-3) {
			showing = false;
			return;
		}

		Vec3 axis = along.scale(1 / length);
		Vec3 u = axis.cross(UP);
		u = u.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : u.normalize();
		Vec3 v = u.cross(axis).normalize();
		float reach = grow <= 0f ? 1f : Mth.clamp(age / grow, 0f, 1f);
		int n = Math.max(2, Math.round(POINTS * reach));
		points = new Vector3f[n];
		alphas = new float[n];
		radii = new float[n];
		float time = frame.time;
		float swell = 1f + 0.6f * surge;

		for (int i = 0; i < n; i++) {
			float t = reach * i / (n - 1);
			float hold = Mth.sin(t * Mth.PI);
			float phase = Mth.TWO_PI * (waves * t - speed * time) + seed;
			double side = amplitude * hold * (Mth.sin(phase) + 0.35f * Mth.sin(phase * 2.3f + seed));
			double lift = amplitude * hold * 0.7f * Mth.cos(phase * 0.8f + seed * 1.7f);

			if (coilRadius > 0f) {
				float turn = Mth.TWO_PI * (coilTurns * t - coilSpeed * time) + seed;
				float spread = coilRadius * (float) Math.sqrt(Math.max(0f, hold));
				side += spread * Mth.cos(turn);
				lift += spread * Mth.sin(turn);
			}

			Vec3 p = a.add(along.scale(t)).add(u.scale(side)).add(v.scale(lift));
			points[i] = frame.rel(p);
			float bead = 0f;

			if (beads > 0f) {
				float flow = (t * beads - time * beadSpeed * beads) % 1f;
				flow = flow < 0f ? flow + 1f : flow;
				bead = (float) Math.pow(Math.max(0f, 1f - Math.abs(flow - 0.5f) * 3f), 2.0);
			}

			alphas[i] = alpha * (0.6f + 0.4f * hold) * (1f + beadGain * bead);
			radii[i] = Mth.lerp(t, width, endWidth) * (1f + 0.6f * bead) * swell * frame.near(points[i]);
		}
	}

	@Override
	public void glow(EnergyRender.Frame frame, PoseStack.Pose pose, VertexConsumer buffer) {
		draw(frame, pose, buffer, palette.glow(), palette.glowRadius());
	}

	@Override
	public void solid(EnergyRender.Frame frame, PoseStack.Pose pose, VertexConsumer buffer) {
		draw(frame, pose, buffer, palette.core(), palette.coreRadius());
	}

	private void draw(EnergyRender.Frame frame, PoseStack.Pose pose, VertexConsumer buffer, GlowRibbon.Profile profile, float radius) {
		if (!showing || radius <= 0f) {
			return;
		}

		int n = points.length;
		float[] r = new float[n];
		float[] a = new float[n];

		for (int i = 0; i < n; i++) {
			r[i] = radii[i] * radius;
			a[i] = Math.min(1.2f, alphas[i]);
		}

		GlowRibbon.beam(pose, buffer, frame.eye, points, r, a, profile, false, false);
	}
}
