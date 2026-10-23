package com.oldrepublic.client.force.kinetic;

import org.jspecify.annotations.Nullable;
import org.joml.Vector3fc;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * A front of pressure travelling out from a point, the way a blast wave does: it races out and slows as it spends itself,
 * and thins away before it stops. It is drawn as a broken spread of short curved lines of bent air lying across the way it
 * travels (a pale core with a dark fringe on one side, the light-and-dark edge of refraction), scattered over part of a
 * sphere: a cap round an axis for a push's cone, or a band round the axis for a pulse from the body. There is no sheet, no
 * dome and no unbroken ring: only fragments of the front with gaps between them, alpha-blended and dimmed by the world's
 * light, never a glow.
 */
public final class PressureFront implements KineticRender.Shape {
	/** The core's colour: bright air. */
	private static final int PALE = 0xEEF1F4;
	/** The fringe's colour: the shadowed side of the bend. */
	private static final int DARK = 0x14181C;
	/** Points along one fragment. */
	private static final int POINTS = 6;
	/** Offsets across a fragment, as fractions of its half-width, and the share of the alpha each carries. */
	private static final float[] ACROSS = {-1f, -0.35f, 0.15f, 0.55f, 1f};
	private static final float[] ACROSS_ALPHA = {0f, 0.55f, 1f, 0.45f, 0f};
	/** Which colour each across offset takes: true the pale core, false the dark fringe. */
	private static final boolean[] ACROSS_PALE = {false, false, true, true, true};

	private final Vec3 origin;
	private final float ax;
	private final float ay;
	private final float az;
	private final float ux;
	private final float uy;
	private final float uz;
	private final float vx;
	private final float vy;
	private final float vz;
	/** Polar angles (radians from the axis) the fragments lie between. */
	private float polarFrom;
	private float polarTo = Mth.PI / 4f;
	/** Squash across the axis along {@code v}. */
	private float flatten = 1f;
	private float from = 0.3f;
	private float to = 6f;
	private int life = 10;
	private float burst = 2.2f;
	private float strength = 1f;
	private float light = 1f;
	private float fadeFrom = 0.4f;
	/** Half-width of a fragment, in blocks, at the start and at the end of the front's travel. */
	private float width = 0.035f;
	/** How far fragments stray in and out of the front, as a fraction of the radius. */
	private float wobble = 0.08f;
	/** How many sectors round the axis the fragments keep to (0: every way round), and each sector's width (radians). */
	private int sectors;
	private float sectorWidth;
	private int age;
	/** Per fragment: polar angle, azimuth, angular half-length, radius jitter, alpha, a phase for its drift. */
	private float @Nullable [] fragments;

	public PressureFront(Vec3 origin, Vec3 axis) {
		this.origin = origin;
		double len = Math.max(1.0e-5, axis.length());
		float x = (float) (axis.x / len);
		float y = (float) (axis.y / len);
		float z = (float) (axis.z / len);

		if (axis.lengthSqr() < 1.0e-8) {
			x = 0f;
			y = 0f;
			z = 1f;
		}

		ax = x;
		ay = y;
		az = z;
		// u = axis x up (horizontal, square to the axis), unless the axis is (nearly) vertical.
		float cx = -z;
		float cz = x;
		float clen = (float) Math.sqrt(cx * cx + cz * cz);

		if (clen < 1.0e-3f) {
			ux = 1f;
			uy = 0f;
			uz = 0f;
		} else {
			ux = cx / clen;
			uy = 0f;
			uz = cz / clen;
		}

		// v = u x axis.
		vx = uy * az - uz * ay;
		vy = uz * ax - ux * az;
		vz = ux * ay - uy * ax;
	}

	/** The cap's half-extent from the axis in degrees (180: every way round the axis). */
	public PressureFront spread(float degrees) {
		this.polarFrom = 0f;
		this.polarTo = Mth.clamp(degrees, 1f, 180f) * Mth.DEG_TO_RAD;
		return this;
	}

	/** Only the band between {@code fromDegrees} and {@code toDegrees} from the axis (a pulse round the body, not a ball). */
	public PressureFront band(float fromDegrees, float toDegrees) {
		this.polarFrom = Mth.clamp(fromDegrees, 0f, 179f) * Mth.DEG_TO_RAD;
		this.polarTo = Mth.clamp(toDegrees, fromDegrees + 1f, 180f) * Mth.DEG_TO_RAD;
		return this;
	}

	/** Squash across the axis, upward (a low, wide wave below 1). */
	public PressureFront flatten(float flatten) {
		this.flatten = flatten;
		return this;
	}

	/** Radius at the start and at the end of its life. */
	public PressureFront radius(float from, float to) {
		this.from = from;
		this.to = to;
		return this;
	}

	public PressureFront life(int ticks) {
		this.life = Math.max(2, ticks);
		return this;
	}

	/** How sharply it leaves and slows: 1 steady, 2 to 3 a blast; below 1 it gathers speed (air sucked in). */
	public PressureFront burst(float burst) {
		this.burst = Math.max(0.3f, burst);
		return this;
	}

	public PressureFront strength(float strength) {
		this.strength = strength;
		return this;
	}

	public PressureFront light(float light) {
		this.light = Mth.clamp(light, 0f, 1f);
		return this;
	}

	public PressureFront fadeFrom(float t) {
		this.fadeFrom = Mth.clamp(t, 0f, 0.95f);
		return this;
	}

	/** Half-width of each line of bent air, in blocks. */
	public PressureFront thickness(float halfWidth) {
		this.width = Math.max(0.005f, halfWidth);
		return this;
	}

	public PressureFront wobble(float wobble) {
		this.wobble = wobble;
		return this;
	}

	/**
	 * Keeps the fragments to {@code count} sectors round the axis, each about {@code degrees} wide, spaced unevenly with gaps
	 * between them, so the front never closes round its axis: seen along the axis (a creature struck straight away from the
	 * viewer, or the air sucked toward the viewer's own hand) it is a few broken arcs on some sides, not a halo.
	 */
	public PressureFront sectors(int count, float degrees) {
		this.sectors = Math.max(0, count);
		this.sectorWidth = Mth.clamp(degrees, 5f, 360f) * Mth.DEG_TO_RAD;
		return this;
	}

	/** The radius {@code ticks} after its start. */
	public float radiusAt(float ticks) {
		float t = Mth.clamp(ticks / life, 0f, 1f);
		return from + (to - from) * (1f - (float) Math.pow(1f - t, burst));
	}

	@Override
	public boolean tick() {
		return ++age < life;
	}

	/** Scatters the fragments evenly over the part of the sphere asked for, more of them the larger it is. */
	private float[] scatter() {
		float cosFrom = Mth.cos(polarFrom);
		float cosTo = Mth.cos(polarTo);
		float solid = Mth.TWO_PI * (cosFrom - cosTo);
		// The share of the way round the axis the sectors cover.
		float cover = sectors > 0 ? Math.min(1f, sectors * sectorWidth / Mth.TWO_PI) : 1f;
		int count = Mth.clamp(Math.round(solid * 15f * cover), 8, 64);
		float[] out = new float[count * 6];
		// The sectors' middles: evenly round the axis from a random start, each nudged a little, so their gaps differ.
		float[] middles = new float[Math.max(1, sectors)];
		float start = KineticKit.RANDOM.nextFloat() * Mth.TWO_PI;
		float spacing = Mth.TWO_PI / Math.max(1, sectors);
		float nudge = Math.max(0f, spacing - sectorWidth) * 0.3f;

		for (int s = 0; s < sectors; s++) {
			middles[s] = start + s * spacing + (KineticKit.RANDOM.nextFloat() - 0.5f) * 2f * nudge;
		}

		for (int i = 0; i < count; i++) {
			// Even over the area: the cosine of the polar angle is spread evenly.
			float c = cosFrom + (cosTo - cosFrom) * KineticKit.RANDOM.nextFloat();
			out[i * 6] = (float) Math.acos(Mth.clamp(c, -1f, 1f));
			out[i * 6 + 1] = sectors > 0 ? middles[KineticKit.RANDOM.nextInt(sectors)] + (KineticKit.RANDOM.nextFloat() - 0.5f) * sectorWidth
					: KineticKit.RANDOM.nextFloat() * Mth.TWO_PI;
			out[i * 6 + 2] = 0.07f + KineticKit.RANDOM.nextFloat() * 0.13f;
			out[i * 6 + 3] = (KineticKit.RANDOM.nextFloat() - 0.5f) * 2f * wobble;
			out[i * 6 + 4] = 0.45f + KineticKit.RANDOM.nextFloat() * 0.55f;
			out[i * 6 + 5] = KineticKit.RANDOM.nextFloat() * Mth.TWO_PI;
		}

		return out;
	}

	@Override
	public void render(PoseStack.Pose pose, VertexConsumer buffer, Vec3 cam, Vector3fc eye, float partialTick) {
		float ticks = age + partialTick;
		float t = ticks / life;

		if (t >= 1f) {
			return;
		}

		if (fragments == null) {
			fragments = scatter();
		}

		float radius = radiusAt(ticks);
		float envelope = strength * Mth.clamp(ticks / 0.6f, 0f, 1f) * (1f - smoothstep(fadeFrom, 1f, t));

		if (envelope <= 0.004f || radius <= 0.02f) {
			return;
		}

		float ox = (float) (origin.x - cam.x);
		float oy = (float) (origin.y - cam.y);
		float oz = (float) (origin.z - cam.z);
		float paleShade = 0.3f + 0.7f * light;
		float halfWidth = width * (0.7f + 0.6f * Mth.clamp(radius / Math.max(0.5f, Math.max(from, to)), 0f, 1f));
		float[] xs = new float[POINTS];
		float[] ys = new float[POINTS];
		float[] zs = new float[POINTS];
		float span = polarTo - polarFrom;

		for (int f = 0; f < fragments.length; f += 6) {
			float polar = fragments[f];
			float azimuth = fragments[f + 1] + Mth.sin(ticks * 0.21f + fragments[f + 5]) * 0.05f;
			float half = fragments[f + 2];
			float r = radius * (1f + fragments[f + 3]);
			// Fragments thin out toward the cap's own edge (and the band's edges), so it ends raggedly.
			float edge = span >= Mth.PI - 1.0e-3f ? 1f
					: Math.min(1f, Math.min(polarTo - polar, polarFrom > 0f ? polar - polarFrom : span) / Math.max(0.05f, span * 0.3f));
			float alpha = envelope * fragments[f + 4] * Mth.clamp(edge, 0f, 1f);

			if (alpha <= 0.004f) {
				continue;
			}

			// The point on the front and the way across the travel (along the circle round the axis).
			float sp = Mth.sin(polar);
			float cp = Mth.cos(polar);
			float sa = Mth.sin(azimuth);
			float ca = Mth.cos(azimuth);
			float px = ax * cp + (ux * ca + vx * sa * flatten) * sp;
			float py = ay * cp + (uy * ca + vy * sa * flatten) * sp;
			float pz = az * cp + (uz * ca + vz * sa * flatten) * sp;
			float pl = (float) Math.sqrt(px * px + py * py + pz * pz);
			px /= pl;
			py /= pl;
			pz /= pl;
			float tx = -ux * sa + vx * ca * flatten;
			float ty = -uy * sa + vy * ca * flatten;
			float tz = -uz * sa + vz * ca * flatten;
			// Square the tangent to the point, so the arc follows the sphere's surface.
			float dot = tx * px + ty * py + tz * pz;
			tx -= px * dot;
			ty -= py * dot;
			tz -= pz * dot;
			float tl = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);

			if (tl < 1.0e-4f) {
				continue;
			}

			tx /= tl;
			ty /= tl;
			tz /= tl;

			for (int i = 0; i < POINTS; i++) {
				// A great-circle arc through the point.
				float d = (float) Math.tan(half * (2f * i / (POINTS - 1) - 1f));
				float qx = px + tx * d;
				float qy = py + ty * d;
				float qz = pz + tz * d;
				float ql = r / (float) Math.sqrt(qx * qx + qy * qy + qz * qz);
				xs[i] = ox + qx * ql;
				ys[i] = oy + qy * ql;
				zs[i] = oz + qz * ql;
			}

			line(pose, buffer, eye, xs, ys, zs, halfWidth, alpha, paleShade);
		}
	}

	/** One fragment: a camera-facing strip along the points, tapered at both ends, pale on one side and dark on the other. */
	private static void line(PoseStack.Pose pose, VertexConsumer buffer, Vector3fc eye, float[] xs, float[] ys, float[] zs, float halfWidth, float alpha,
			float paleShade) {
		int across = ACROSS.length;
		float[] sx = new float[POINTS];
		float[] sy = new float[POINTS];
		float[] sz = new float[POINTS];
		float[] taper = new float[POINTS];

		for (int i = 0; i < POINTS; i++) {
			int a = Math.max(0, i - 1);
			int b = Math.min(POINTS - 1, i + 1);
			float tx = xs[b] - xs[a];
			float ty = ys[b] - ys[a];
			float tz = zs[b] - zs[a];
			float cx = eye.x() - xs[i];
			float cy = eye.y() - ys[i];
			float cz = eye.z() - zs[i];
			// Across the line, square to it and to the view.
			float nx = ty * cz - tz * cy;
			float ny = tz * cx - tx * cz;
			float nz = tx * cy - ty * cx;
			float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);

			if (nl < 1.0e-6f) {
				nx = 0f;
				ny = 1f;
				nz = 0f;
				nl = 1f;
			}

			sx[i] = nx / nl * halfWidth;
			sy[i] = ny / nl * halfWidth;
			sz[i] = nz / nl * halfWidth;
			float u = i / (float) (POINTS - 1);
			taper[i] = (float) Math.pow(Mth.sin(u * Mth.PI), 0.7);
		}

		for (int i = 0; i + 1 < POINTS; i++) {
			for (int k = 0; k + 1 < across; k++) {
				vertex(pose, buffer, xs, ys, zs, sx, sy, sz, i, ACROSS[k], colour(k, alpha * taper[i], paleShade));
				vertex(pose, buffer, xs, ys, zs, sx, sy, sz, i + 1, ACROSS[k], colour(k, alpha * taper[i + 1], paleShade));
				vertex(pose, buffer, xs, ys, zs, sx, sy, sz, i + 1, ACROSS[k + 1], colour(k + 1, alpha * taper[i + 1], paleShade));
				vertex(pose, buffer, xs, ys, zs, sx, sy, sz, i, ACROSS[k + 1], colour(k + 1, alpha * taper[i], paleShade));
			}
		}
	}

	private static int colour(int k, float alpha, float paleShade) {
		float a = alpha * ACROSS_ALPHA[k];

		if (ACROSS_PALE[k]) {
			int grey = Mth.clamp(Math.round(255f * paleShade), 0, 255);
			int rgb = PALE;
			int r = (rgb >> 16 & 255) * grey / 255;
			int g = (rgb >> 8 & 255) * grey / 255;
			int b = (rgb & 255) * grey / 255;
			return Mth.clamp(Math.round(a * 255f), 0, 255) << 24 | r << 16 | g << 8 | b;
		}

		return Mth.clamp(Math.round(a * 0.8f * 255f), 0, 255) << 24 | DARK;
	}

	private static float smoothstep(float e0, float e1, float v) {
		float t = Mth.clamp((v - e0) / (e1 - e0), 0f, 1f);
		return t * t * (3f - 2f * t);
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, float[] xs, float[] ys, float[] zs, float[] sx, float[] sy, float[] sz, int i,
			float across, int argb) {
		buffer.addVertex(pose, xs[i] + sx[i] * across, ys[i] + sy[i] * across, zs[i] + sz[i] * across).setColor(argb);
	}
}
