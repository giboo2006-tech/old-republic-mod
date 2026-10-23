package com.oldrepublic.client.enemy.hymn;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.world.phys.Vec3;

/**
 * Untextured additive glow shapes for {@code RenderTypes.lightning()} (position and colour quads), the
 * M1 {@code GlowGeometry} technique: every face is emitted in both windings so it shows from either
 * side. Used by W8's muzzle glows, charged plates, arcs and beams.
 */
public final class FoeGlow {
	/** An axis-aligned box in the current pose. */
	public static void box(PoseStack.Pose pose, VertexConsumer buffer, float x0, float y0, float z0, float x1, float y1, float z1, int argb) {
		quad(pose, buffer, argb, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0);
		quad(pose, buffer, argb, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
		quad(pose, buffer, argb, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1);
		quad(pose, buffer, argb, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
		quad(pose, buffer, argb, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
		quad(pose, buffer, argb, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1);
	}

	/** Three crossed squares of half-size {@code r} centred on the origin: a soft star-like flare. */
	public static void flare(PoseStack.Pose pose, VertexConsumer buffer, float r, int argb) {
		quad(pose, buffer, argb, -r, -r, 0, r, -r, 0, r, r, 0, -r, r, 0);
		quad(pose, buffer, argb, 0, -r, -r, 0, -r, r, 0, r, r, 0, r, -r);
		quad(pose, buffer, argb, -r, 0, -r, r, 0, -r, r, 0, r, -r, 0, r);
	}

	/** Three crossed squares of half-size {@code r} centred on {@code at} (world-relative pose). */
	public static void flare(PoseStack.Pose pose, VertexConsumer buffer, Vec3 at, float r, int argb) {
		float x = (float) at.x;
		float y = (float) at.y;
		float z = (float) at.z;
		quad(pose, buffer, argb, x - r, y - r, z, x + r, y - r, z, x + r, y + r, z, x - r, y + r, z);
		quad(pose, buffer, argb, x, y - r, z - r, x, y - r, z + r, x, y + r, z + r, x, y + r, z - r);
		quad(pose, buffer, argb, x - r, y, z - r, x + r, y, z - r, x + r, y, z + r, x - r, y, z + r);
	}

	/**
	 * A thin glowing strip from {@code a} to {@code b}, {@code width} across: two crossed ribbons (one
	 * flat, one upright) so it reads from every side without the camera's direction.
	 */
	public static void strip(PoseStack.Pose pose, VertexConsumer buffer, Vec3 a, Vec3 b, float width, int argb) {
		Vec3 d = b.subtract(a);

		if (d.lengthSqr() < 1.0e-8) {
			return;
		}

		Vec3 dir = d.normalize();
		Vec3 side = dir.cross(new Vec3(0, 1, 0));
		side = side.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : side.normalize();
		Vec3 up = side.cross(dir).normalize();
		ribbon(pose, buffer, a, b, side.scale(width * 0.5), argb);
		ribbon(pose, buffer, a, b, up.scale(width * 0.5), argb);
	}

	/** One flat ribbon from {@code a} to {@code b}, offset by {@code half} to each side. */
	public static void ribbon(PoseStack.Pose pose, VertexConsumer buffer, Vec3 a, Vec3 b, Vec3 half, int argb) {
		quad(pose, buffer, argb,
				(float) (a.x - half.x), (float) (a.y - half.y), (float) (a.z - half.z),
				(float) (b.x - half.x), (float) (b.y - half.y), (float) (b.z - half.z),
				(float) (b.x + half.x), (float) (b.y + half.y), (float) (b.z + half.z),
				(float) (a.x + half.x), (float) (a.y + half.y), (float) (a.z + half.z));
	}

	/** A quad with its four corners given, in both windings. */
	public static void quad(PoseStack.Pose pose, VertexConsumer buffer, int argb,
			float ax, float ay, float az, float bx, float by, float bz,
			float cx, float cy, float cz, float dx, float dy, float dz) {
		// The M1 GlowGeometry vertex layout: RenderTypes.lightning() takes position and colour quads.
		buffer.addVertex(pose, ax, ay, az).setColor(argb);
		buffer.addVertex(pose, bx, by, bz).setColor(argb);
		buffer.addVertex(pose, cx, cy, cz).setColor(argb);
		buffer.addVertex(pose, dx, dy, dz).setColor(argb);
		buffer.addVertex(pose, dx, dy, dz).setColor(argb);
		buffer.addVertex(pose, cx, cy, cz).setColor(argb);
		buffer.addVertex(pose, bx, by, bz).setColor(argb);
		buffer.addVertex(pose, ax, ay, az).setColor(argb);
	}

	/** {@code rgb} (0xRRGGBB) with alpha {@code alpha} (0 to 1). */
	public static int argb(float alpha, int rgb) {
		int a = Math.max(0, Math.min(255, Math.round(alpha * 255f)));
		return (a << 24) | (rgb & 0xFFFFFF);
	}

	private FoeGlow() {
	}
}
