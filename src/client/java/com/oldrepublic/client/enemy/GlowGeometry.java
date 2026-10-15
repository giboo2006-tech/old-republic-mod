package com.oldrepublic.client.enemy;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * Untextured additive glow shapes for {@code RenderTypes.lightning()} (position + colour quads).
 * Every face is emitted with both windings so it shows regardless of culling.
 */
final class GlowGeometry {
	/** An axis-aligned box in the current pose. */
	static void box(PoseStack.Pose pose, VertexConsumer buffer, float x0, float y0, float z0, float x1, float y1, float z1, int argb) {
		quad(pose, buffer, argb, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0);
		quad(pose, buffer, argb, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
		quad(pose, buffer, argb, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1);
		quad(pose, buffer, argb, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
		quad(pose, buffer, argb, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
		quad(pose, buffer, argb, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1);
	}

	/** Three crossed squares of half-size {@code r} centred on the origin: a soft star-like flare. */
	static void flare(PoseStack.Pose pose, VertexConsumer buffer, float r, int argb) {
		quad(pose, buffer, argb, -r, -r, 0, r, -r, 0, r, r, 0, -r, r, 0);
		quad(pose, buffer, argb, 0, -r, -r, 0, -r, r, 0, r, r, 0, r, -r);
		quad(pose, buffer, argb, -r, 0, -r, r, 0, -r, r, 0, r, -r, 0, r);
	}

	private static void quad(PoseStack.Pose pose, VertexConsumer buffer, int argb,
			float ax, float ay, float az, float bx, float by, float bz,
			float cx, float cy, float cz, float dx, float dy, float dz) {
		// API-CHECK: RenderTypes.lightning() uses the POSITION_COLOR vertex format and quads (as in 1.21).
		buffer.addVertex(pose, ax, ay, az).setColor(argb);
		buffer.addVertex(pose, bx, by, bz).setColor(argb);
		buffer.addVertex(pose, cx, cy, cz).setColor(argb);
		buffer.addVertex(pose, dx, dy, dz).setColor(argb);
		buffer.addVertex(pose, dx, dy, dz).setColor(argb);
		buffer.addVertex(pose, cx, cy, cz).setColor(argb);
		buffer.addVertex(pose, bx, by, bz).setColor(argb);
		buffer.addVertex(pose, ax, ay, az).setColor(argb);
	}

	private GlowGeometry() {
	}
}
