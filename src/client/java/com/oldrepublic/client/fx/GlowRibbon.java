package com.oldrepublic.client.fx;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.util.Mth;

/**
 * Soft, round-looking light shapes made of flat position-and-colour quads that always face the camera: a beam along a line
 * of points with rounded ends, its colour falling off from the centre line to the edge along a {@link Profile}. Drawn into
 * {@link GlowRenderTypes#glow()} it reads as light (a saber's glow, a lightning bolt, a spark streak); drawn into
 * {@code RenderTypes.debugQuads()} as a solid core with a soft edge (the saber's white core and coloured fringe, which
 * must show against a bright sky). Every quad is drawn once; both render types draw both faces.
 *
 * <p>The quads are turned toward the camera point by point, so a beam keeps its width from any side; looking straight down
 * the line its two end caps make a round disc. {@link #eye} finds the camera in the pose's own space: in the level and in
 * the first-person hand alike, the camera sits at the origin of the space the pose stack maps into.
 */
public final class GlowRibbon {
	/**
	 * A cross-section: {@code at} are distances from the centre line as fractions of the radius, from 0 (the centre) out
	 * to 1 (the edge), and {@code argb} the colour at each, usually fading to alpha 0 at the edge.
	 */
	public record Profile(float[] at, int[] argb) {
		public Profile {
			if (at.length < 2 || at.length != argb.length || at[0] != 0f) {
				throw new IllegalArgumentException("A profile starts at the centre (0) and has a colour for each distance");
			}
		}
	}

	/** Half-circle steps in a rounded end. */
	private static final int CAP_STEPS = 8;
	private static final Vector3f UP = new Vector3f(0f, 1f, 0f);
	private static final Vector3f EAST = new Vector3f(1f, 0f, 0f);

	/** The camera's position in the local space of {@code pose} (the origin of the space the pose maps into, carried back). */
	public static Vector3f eye(Matrix4fc pose) {
		Vector3f eye = new Matrix4f(pose).invert().transformPosition(new Vector3f());
		return eye.isFinite() ? eye : new Vector3f(0f, 0f, 1000f);
	}

	/** A straight beam from {@code a} to {@code b}, {@code radius} wide each side, rounded at both ends. */
	public static void capsule(PoseStack.Pose pose, VertexConsumer buffer, Vector3fc eye, Vector3fc a, Vector3fc b, float radius, Profile profile,
			float alpha) {
		beam(pose, buffer, eye, new Vector3fc[] {a, b}, new float[] {radius, radius}, new float[] {alpha, alpha}, profile, true, true);
	}

	/**
	 * A beam through {@code points}, each with its own radius and alpha (a multiplier on the profile's alpha), with a rounded
	 * end at the first and last point when asked. Fewer than two points draw nothing.
	 */
	public static void beam(PoseStack.Pose pose, VertexConsumer buffer, Vector3fc eye, Vector3fc[] points, float[] radii, float[] alphas, Profile profile,
			boolean capStart, boolean capEnd) {
		int n = points.length;

		if (n < 2) {
			return;
		}

		Vector3f[] sides = new Vector3f[n];
		Vector3f[] tangents = new Vector3f[n];

		for (int i = 0; i < n; i++) {
			Vector3f tangent = new Vector3f(points[Math.min(n - 1, i + 1)]).sub(points[Math.max(0, i - 1)]);

			if (tangent.lengthSquared() < 1.0E-12f) {
				tangent.set(UP);
			}

			tangents[i] = tangent.normalize();
			sides[i] = side(eye, points[i], tangents[i]);
		}

		for (int i = 0; i + 1 < n; i++) {
			strip(pose, buffer, points[i], sides[i], radii[i], alphas[i], points[i + 1], sides[i + 1], radii[i + 1], alphas[i + 1], profile);
		}

		if (capStart) {
			cap(pose, buffer, eye, points[0], sides[0], new Vector3f(tangents[0]).negate(), radii[0], alphas[0], profile);
		}

		if (capEnd) {
			cap(pose, buffer, eye, points[n - 1], sides[n - 1], tangents[n - 1], radii[n - 1], alphas[n - 1], profile);
		}
	}

	/** A round disc of {@code radius} facing the camera (a point of light, a flash). */
	public static void disc(PoseStack.Pose pose, VertexConsumer buffer, Vector3fc eye, Vector3fc centre, float radius, Profile profile, float alpha) {
		Vector3f view = new Vector3f(eye).sub(centre);
		Vector3f across = side(eye, centre, Math.abs(view.normalize().y) < 0.9f ? UP : EAST);
		Vector3f out = new Vector3f(view).cross(across).normalize();
		cap(pose, buffer, eye, centre, across, out, radius, alpha, profile);
		cap(pose, buffer, eye, centre, across, out.negate(), radius, alpha, profile);
	}

	/** The unit direction across a line through {@code p} along {@code axis}, square to both the line and the view ray. */
	public static Vector3f side(Vector3fc eye, Vector3fc p, Vector3fc axis) {
		Vector3f view = new Vector3f(eye).sub(p);
		Vector3f side = new Vector3f(axis).cross(view);

		if (side.lengthSquared() <= 1.0E-12f * Math.max(1.0E-6f, view.lengthSquared())) {
			// Looking straight down the line: any direction across it.
			side.set(axis).cross(Math.abs(axis.y()) < 0.9f ? UP : EAST);
		}

		return side.normalize();
	}

	/** {@code argb} with its alpha multiplied by {@code alpha} (0 to 1). */
	public static int fade(int argb, float alpha) {
		int a = Mth.clamp(Math.round((argb >>> 24) * alpha), 0, 255);
		return a << 24 | argb & 0xFFFFFF;
	}

	/** One stretch of beam from {@code a} to {@code b}: a column of quads per step of the profile, each side of the centre. */
	private static void strip(PoseStack.Pose pose, VertexConsumer buffer, Vector3fc a, Vector3fc sideA, float radiusA, float alphaA, Vector3fc b,
			Vector3fc sideB, float radiusB, float alphaB, Profile profile) {
		float[] at = profile.at();
		int[] argb = profile.argb();

		for (int sign = -1; sign <= 1; sign += 2) {
			for (int k = 0; k + 1 < at.length; k++) {
				float a0 = at[k] * sign;
				float a1 = at[k + 1] * sign;
				vertex(pose, buffer, a, sideA, a0 * radiusA, fade(argb[k], alphaA));
				vertex(pose, buffer, a, sideA, a1 * radiusA, fade(argb[k + 1], alphaA));
				vertex(pose, buffer, b, sideB, a1 * radiusB, fade(argb[k + 1], alphaB));
				vertex(pose, buffer, b, sideB, a0 * radiusB, fade(argb[k], alphaB));
			}
		}
	}

	/**
	 * A half disc at {@code centre} from {@code side} round through {@code outward} (made square to the view ray) to minus
	 * {@code side}: rings at the profile's distances, the centre a point.
	 */
	private static void cap(PoseStack.Pose pose, VertexConsumer buffer, Vector3fc eye, Vector3fc centre, Vector3fc side, Vector3fc outward, float radius,
			float alpha, Profile profile) {
		Vector3f view = new Vector3f(eye).sub(centre);
		Vector3f out = new Vector3f(view).cross(side);

		if (out.lengthSquared() < 1.0E-12f) {
			out.set(outward);
		}

		out.normalize();

		if (out.dot(outward) < 0f) {
			out.negate();
		}

		float[] at = profile.at();
		int[] argb = profile.argb();
		Vector3f d0 = new Vector3f();
		Vector3f d1 = new Vector3f();

		for (int s = 0; s < CAP_STEPS; s++) {
			direction(side, out, Mth.PI * s / CAP_STEPS, d0);
			direction(side, out, Mth.PI * (s + 1) / CAP_STEPS, d1);

			for (int k = 0; k + 1 < at.length; k++) {
				int c0 = fade(argb[k], alpha);
				int c1 = fade(argb[k + 1], alpha);
				vertex(pose, buffer, centre, d0, at[k] * radius, c0);
				vertex(pose, buffer, centre, d0, at[k + 1] * radius, c1);
				vertex(pose, buffer, centre, d1, at[k + 1] * radius, c1);
				vertex(pose, buffer, centre, d1, at[k] * radius, c0);
			}
		}
	}

	private static void direction(Vector3fc side, Vector3fc out, float angle, Vector3f dest) {
		float c = Mth.cos(angle);
		float s = Mth.sin(angle);
		dest.set(side.x() * c + out.x() * s, side.y() * c + out.y() * s, side.z() * c + out.z() * s);
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, Vector3fc p, Vector3fc dir, float distance, int argb) {
		buffer.addVertex(pose, p.x() + dir.x() * distance, p.y() + dir.y() * distance, p.z() + dir.z() * distance).setColor(argb);
	}

	private GlowRibbon() {
	}
}
