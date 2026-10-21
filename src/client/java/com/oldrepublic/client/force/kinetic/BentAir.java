package com.oldrepublic.client.force.kinetic;

import org.joml.Vector3fc;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.util.Mth;

/**
 * Draws a sheet of bent air: part of a sphere (a cap round an axis, or the whole of it) made of camera-independent quads
 * whose alpha follows how the view meets the surface. Seen face on it is a faint, uneven haze; where the view grazes it (its
 * outline) the sheet thickens into a sharp line, the way a pressure wave or heat haze shows against what is behind it. One
 * pale sheet and one dark sheet just inside it give the light-and-dark double edge of refraction. Smooth noise breaks the
 * sheet into drifting patches and makes its surface uneven, so it never reads as a clean ring, a dome or a halo; the cap's
 * own edge fades to nothing.
 */
public final class BentAir {
	/** The pale sheet's colour: bright air. */
	public static final int PALE = 0xEEF1F4;
	/** The dark sheet's colour: the shadowed side of the bend. */
	public static final int DARK = 0x14181C;

	/** The shape of one sheet. Directions are unit vectors; {@code u} and {@code v} square to {@code axis} and each other. */
	public static final class Sheet {
		float ax;
		float ay;
		float az;
		float ux;
		float uy;
		float uz;
		float vx;
		float vy;
		float vz;
		/** Half-extent of the cap from the axis in radians; {@code PI} or more draws the whole sphere. */
		float spread;
		/** Squash of the part across the axis along {@code v} (1 round, 0.4 wide and low when {@code v} is up). */
		float flatten = 1f;
		/** How uneven the surface is, as a fraction of the radius. */
		float wobble = 0.14f;
		int rings;
		int segments;
		float seed;

		/** Sets the axis and finds two directions square to it ({@code v} as near to up as the axis allows). */
		public Sheet axis(double x, double y, double z) {
			float len = (float) Math.sqrt(x * x + y * y + z * z);

			if (len < 1.0e-5f) {
				x = 0;
				y = 0;
				z = 1;
				len = 1f;
			}

			ax = (float) x / len;
			ay = (float) y / len;
			az = (float) z / len;
			// u = axis x up (horizontal, square to the axis), unless the axis is (nearly) vertical.
			float cx = -az;
			float cz = ax;
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
			return this;
		}

		public Sheet spread(float radians, int rings, int segments) {
			this.spread = radians;
			this.rings = Math.max(1, rings);
			this.segments = Math.max(3, segments);
			return this;
		}

		public Sheet flatten(float flatten) {
			this.flatten = flatten;
			return this;
		}

		public Sheet wobble(float wobble) {
			this.wobble = wobble;
			return this;
		}

		public Sheet seed(float seed) {
			this.seed = seed;
			return this;
		}
	}

	/**
	 * Draws one sheet of {@code sheet}'s shape at {@code radius} round the camera-relative point {@code (ox, oy, oz)}.
	 * {@code face} and {@code edge} are the alphas where the view meets the sheet head on and where it grazes it, both
	 * scaled by {@code alpha}; {@code time} makes the patches drift; {@code layer} keeps the pale and the dark sheets'
	 * patches apart.
	 */
	public static void draw(PoseStack.Pose pose, VertexConsumer buffer, Vector3fc eye, Sheet sheet, float ox, float oy, float oz, float radius, int rgb,
			float face, float edge, float alpha, float time, float layer) {
		if (alpha <= 0.002f || radius <= 0.01f) {
			return;
		}

		boolean whole = sheet.spread >= Mth.PI - 1.0e-3f;
		float spread = Math.min(sheet.spread, Mth.PI);
		int rows = sheet.rings + 1;
		int cols = sheet.segments + 1;
		float[] xs = new float[rows * cols];
		float[] ys = new float[rows * cols];
		float[] zs = new float[rows * cols];
		int[] colours = new int[rows * cols];
		float ex = eye.x();
		float ey = eye.y();
		float ez = eye.z();

		for (int i = 0; i < rows; i++) {
			float a = spread * i / sheet.rings;
			float ca = Mth.cos(a);
			float sa = Mth.sin(a);
			// The cap's own edge fades to nothing; the whole sphere has none.
			float rim = whole ? 1f : 1f - smoothstep(spread * 0.5f, spread, a);

			for (int j = 0; j < cols; j++) {
				float phi = Mth.TWO_PI * (j % sheet.segments) / sheet.segments;
				float cp = Mth.cos(phi);
				float sp = Mth.sin(phi);
				float dx = sheet.ax * ca + (sheet.ux * cp + sheet.vx * sp * sheet.flatten) * sa;
				float dy = sheet.ay * ca + (sheet.uy * cp + sheet.vy * sp * sheet.flatten) * sa;
				float dz = sheet.az * ca + (sheet.uz * cp + sheet.vz * sp * sheet.flatten) * sa;
				float dl = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
				dx /= dl;
				dy /= dl;
				dz /= dl;
				// An uneven surface whose bumps drift outward from the axis as time goes on.
				float n = KineticNoise.layered(cp * 2.6f + sheet.seed, sp * 2.6f + layer, a * 3.5f - time);
				float r = radius * (1f + sheet.wobble * (n - 0.5f) * 2f);
				float x = ox + dx * r;
				float y = oy + dy * r;
				float z = oz + dz * r;
				float vx = x - ex;
				float vy = y - ey;
				float vz = z - ez;
				float vl = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
				float facing = vl < 1.0e-4f ? 1f : Math.abs(dx * vx + dy * vy + dz * vz) / vl;
				float graze = 1f - facing;
				graze *= graze;
				// Torn into patches with gaps between them, so no outline runs unbroken.
				float patch = smoothstep(0.4f, 0.75f, KineticNoise.layered(cp * 3.1f - sheet.seed, sp * 3.1f + layer * 1.7f, a * 4.2f - time * 1.3f));
				float value = alpha * rim * (face + (edge - face) * graze) * patch;
				int k = i * cols + j;
				xs[k] = x;
				ys[k] = y;
				zs[k] = z;
				colours[k] = Mth.clamp(Math.round(value * 255f), 0, 255) << 24 | rgb & 0xFFFFFF;
			}
		}

		for (int i = 0; i + 1 < rows; i++) {
			for (int j = 0; j + 1 < cols; j++) {
				int k00 = i * cols + j;
				int k01 = k00 + 1;
				int k10 = k00 + cols;
				int k11 = k10 + 1;

				if ((colours[k00] | colours[k01] | colours[k10] | colours[k11]) >>> 24 == 0) {
					continue;
				}

				vertex(pose, buffer, xs, ys, zs, colours, k00);
				vertex(pose, buffer, xs, ys, zs, colours, k10);
				vertex(pose, buffer, xs, ys, zs, colours, k11);
				vertex(pose, buffer, xs, ys, zs, colours, k01);
			}
		}
	}

	public static float smoothstep(float e0, float e1, float v) {
		float t = Mth.clamp((v - e0) / (e1 - e0), 0f, 1f);
		return t * t * (3f - 2f * t);
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, float[] xs, float[] ys, float[] zs, int[] colours, int k) {
		buffer.addVertex(pose, xs[k], ys[k], zs[k]).setColor(colours[k]);
	}

	private BentAir() {
	}
}
