package com.oldrepublic.client.enemy.droid;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.world.phys.Vec3;

/**
 * Untextured additive light shapes for {@code RenderTypes.lightning()} (position and colour quads),
 * shared by the Milestone 4 foes: a sensor eye, a fuse or mine light, Rattle's scan line. Small
 * lights only: no rings, outlines or halos on the ground. Every face is emitted with both windings
 * so it shows whatever the culling.
 */
public final class GlowShapes {
	/** An axis-aligned box in the current pose. */
	public static void box(PoseStack.Pose pose, VertexConsumer buffer, float x0, float y0, float z0, float x1, float y1, float z1, int argb) {
		quad(pose, buffer, argb, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0);
		quad(pose, buffer, argb, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
		quad(pose, buffer, argb, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1);
		quad(pose, buffer, argb, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
		quad(pose, buffer, argb, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
		quad(pose, buffer, argb, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1);
	}

	/** Three crossed squares of half-size {@code r} centred on the origin: a small soft point of light. */
	public static void flare(PoseStack.Pose pose, VertexConsumer buffer, float r, int argb) {
		quad(pose, buffer, argb, -r, -r, 0, r, -r, 0, r, r, 0, -r, r, 0);
		quad(pose, buffer, argb, 0, -r, -r, 0, -r, r, 0, r, r, 0, r, -r);
		quad(pose, buffer, argb, -r, 0, -r, r, 0, -r, r, 0, r, -r, 0, r);
	}

	/**
	 * A thin beam from {@code from} to {@code to} (model space of the pose): two crossed quads of
	 * half-width {@code r}, fading from {@code argbFrom} to {@code argbTo}.
	 */
	public static void beam(PoseStack.Pose pose, VertexConsumer buffer, Vec3 from, Vec3 to, float r, int argbFrom, int argbTo) {
		Vec3 dir = to.subtract(from);

		if (dir.lengthSqr() < 1.0e-6) {
			return;
		}

		Vec3 axis = dir.normalize();
		Vec3 helper = Math.abs(axis.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
		Vec3 a = axis.cross(helper).normalize().scale(r);
		Vec3 b = axis.cross(a).normalize().scale(r);

		for (Vec3 side : new Vec3[] {a, b}) {
			vertex(pose, buffer, from.subtract(side), argbFrom);
			vertex(pose, buffer, from.add(side), argbFrom);
			vertex(pose, buffer, to.add(side), argbTo);
			vertex(pose, buffer, to.subtract(side), argbTo);
			vertex(pose, buffer, to.subtract(side), argbTo);
			vertex(pose, buffer, to.add(side), argbTo);
			vertex(pose, buffer, from.add(side), argbFrom);
			vertex(pose, buffer, from.subtract(side), argbFrom);
		}
	}

	/** {@code argb} with its alpha scaled by {@code alpha} (0 to 1). */
	public static int fade(int argb, float alpha) {
		int a = Math.round(((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, alpha)));
		return (a << 24) | (argb & 0xFFFFFF);
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, Vec3 at, int argb) {
		buffer.addVertex(pose, (float) at.x, (float) at.y, (float) at.z).setColor(argb);
	}

	private static void quad(PoseStack.Pose pose, VertexConsumer buffer, int argb,
			float ax, float ay, float az, float bx, float by, float bz,
			float cx, float cy, float cz, float dx, float dy, float dz) {
		// RenderTypes.lightning() takes POSITION_COLOR quads, as LightningBoltRenderer#quad writes them (and GlowGeometry).
		buffer.addVertex(pose, ax, ay, az).setColor(argb);
		buffer.addVertex(pose, bx, by, bz).setColor(argb);
		buffer.addVertex(pose, cx, cy, cz).setColor(argb);
		buffer.addVertex(pose, dx, dy, dz).setColor(argb);
		buffer.addVertex(pose, dx, dy, dz).setColor(argb);
		buffer.addVertex(pose, cx, cy, cz).setColor(argb);
		buffer.addVertex(pose, bx, by, bz).setColor(argb);
		buffer.addVertex(pose, ax, ay, az).setColor(argb);
	}

	private GlowShapes() {
	}
}
