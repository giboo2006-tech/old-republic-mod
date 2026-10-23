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
 * One stroke of lightning on screen: a {@link Bolt} between two {@link Anchor}s for a couple of ticks. It strikes at full
 * strength (a touch brighter on its first tick) and dies away quickly, flickering from frame to frame, while new strokes take
 * its place; together they crackle. With flashing effects off a stroke fades in, holds a steady 0.85 and fades out instead.
 * The ends follow the hand and the target between ticks; the zigzag in between bends with them.
 */
public final class Stroke implements EnergyRender.Shape {
	private static final float STEADY_ALPHA = 0.85f;

	private final long owner;
	private final Anchor from;
	private final Anchor to;
	private final Vec3 from0;
	private final Vec3 to0;
	private final Bolt bolt;
	private final Palette palette;
	private final long born;
	private final int life;
	private final float bright;
	private final float width;
	private final float seed;
	private final boolean steady;
	/** This frame: each strand's points relative to the camera, how much width each keeps near the eye, and the strength. */
	private Vector3f[][] points = new Vector3f[0][];
	private float[][] nears = new float[0][];
	private float alpha;
	private boolean showing;

	private Stroke(long owner, Anchor from, Anchor to, Vec3 from0, Vec3 to0, Bolt bolt, Palette palette, int life, float bright, float width) {
		this.owner = owner;
		this.from = from;
		this.to = to;
		this.from0 = from0;
		this.to0 = to0;
		this.bolt = bolt;
		this.palette = palette;
		this.born = EnergyRender.ticks();
		this.life = Math.max(1, life);
		this.bright = bright;
		this.width = width;
		this.seed = EnergyKit.RANDOM.nextFloat() * 100f;
		this.steady = !Vfx.flashing();
	}

	/**
	 * A stroke from {@code from} to {@code to} with {@code forks} forks, lasting {@code life} ticks, {@code bright} and
	 * {@code width} times a main channel's; null when either end cannot be found or they meet.
	 */
	public static @Nullable Stroke of(long owner, Anchor from, Anchor to, Bolt.Style style, int forks, Palette palette, int life, float bright,
			float width) {
		Vec3 a = from.at(1f);
		Vec3 b = to.at(1f);

		if (a == null || b == null || a.distanceToSqr(b) < 0.0025) {
			return null;
		}

		Vector3f delta = new Vector3f((float) (b.x - a.x), (float) (b.y - a.y), (float) (b.z - a.z));
		return new Stroke(owner, from, to, a, b, Bolt.build(EnergyKit.RANDOM, delta, style, forks), palette, life, bright, width);
	}

	@Override
	public boolean tick(ClientLevel level) {
		return EnergyRender.ticks() - born < life;
	}

	@Override
	public long owner() {
		return owner;
	}

	@Override
	public void prepare(EnergyRender.Frame frame) {
		float age = EnergyRender.ticks() - born + frame.partial;
		alpha = envelope(age) * bright;
		Vec3 a = from.at(frame.partial);
		Vec3 b = to.at(frame.partial);
		showing = a != null && b != null && alpha > 0.004f;

		if (!showing) {
			return;
		}

		double ax = a.x - from0.x;
		double ay = a.y - from0.y;
		double az = a.z - from0.z;
		double bx = b.x - to0.x;
		double by = b.y - to0.y;
		double bz = b.z - to0.z;
		Bolt.Strand[] strands = bolt.strands;

		if (points.length != strands.length) {
			points = new Vector3f[strands.length][];
			nears = new float[strands.length][];
		}

		for (int s = 0; s < strands.length; s++) {
			Bolt.Strand strand = strands[s];
			int n = strand.size();
			Vector3f[] pts = new Vector3f[n];
			float[] near = new float[n];

			for (int i = 0; i < n; i++) {
				float t = strand.along[i];
				float u = 1f - t;
				pts[i] = frame.rel(from0.x + strand.xyz[i * 3] + ax * u + bx * t, from0.y + strand.xyz[i * 3 + 1] + ay * u + by * t,
						from0.z + strand.xyz[i * 3 + 2] + az * u + bz * t);
				near[i] = frame.near(pts[i]);
			}

			points[s] = pts;
			nears[s] = near;
		}
	}

	/** Strength at {@code age} ticks: a hard strike that dies away, flickering, or a steady fade in and out. */
	private float envelope(float age) {
		if (steady) {
			return STEADY_ALPHA * Math.min(1f, age * 1.5f) * Mth.clamp((life - age) / 1.5f, 0f, 1f);
		}

		float strike = age < 1f ? 1.15f : (float) Math.pow(Mth.clamp(1f - (age - 1f) / Math.max(1f, life - 1f), 0f, 1f), 1.4);
		float flicker = 0.8f + 0.2f * EnergyKit.noise(seed + age * 7.3f);
		return strike * flicker;
	}

	@Override
	public void glow(EnergyRender.Frame frame, PoseStack.Pose pose, VertexConsumer buffer) {
		draw(frame, pose, buffer, palette.glow(), palette.glowRadius(), 1f);
	}

	@Override
	public void solid(EnergyRender.Frame frame, PoseStack.Pose pose, VertexConsumer buffer) {
		draw(frame, pose, buffer, palette.core(), palette.coreRadius(), 1.1f);
	}

	private void draw(EnergyRender.Frame frame, PoseStack.Pose pose, VertexConsumer buffer, GlowRibbon.Profile profile, float radius, float gain) {
		if (!showing) {
			return;
		}

		Bolt.Strand[] strands = bolt.strands;

		for (int s = 0; s < strands.length && s < points.length; s++) {
			Bolt.Strand strand = strands[s];
			Vector3f[] pts = points[s];
			float[] near = nears[s];
			int n = pts.length;
			float[] radii = new float[n];
			float[] alphas = new float[n];
			float strength = Math.min(1.2f, alpha * strand.bright * gain);

			for (int i = 0; i < n; i++) {
				radii[i] = radius * width * strand.width[i] * near[i];
				alphas[i] = strength;
			}

			GlowRibbon.beam(pose, buffer, frame.eye, pts, radii, alphas, profile, false, false);
		}
	}
}
