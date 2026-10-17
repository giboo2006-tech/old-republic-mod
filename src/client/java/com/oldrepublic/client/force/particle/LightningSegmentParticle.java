package com.oldrepublic.client.force.particle;

import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * One straight piece of a Force Lightning arc: a full-bright quad centred on the segment, rolled so
 * the bolt drawn across the sprite lies along the segment (the push streak technique, aligned to a
 * stored direction instead of the velocity). Lives 2 to 3 ticks; the arc is rebuilt every 2 ticks.
 */
public final class LightningSegmentParticle extends ForceParticle {
	private Vector3f direction = new Vector3f(1f, 0f, 0f);

	public LightningSegmentParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
		super(level, x, y, z, 0, 0, 0, sprites);
		color(1f, 1f, 1f);
		alpha(0.9f);
		life(3);
		fullBright();
		randomFrame();
	}

	/** Places the segment between {@code from} and {@code to}. */
	public LightningSegmentParticle span(Vec3 from, Vec3 to) {
		Vec3 delta = to.subtract(from);
		double length = delta.length();

		if (length > 1.0e-4) {
			direction = new Vector3f((float) (delta.x / length), (float) (delta.y / length), (float) (delta.z / length));
		}

		// The quad spans twice its size; overlap neighbours slightly so the bolt has no gaps.
		float half = (float) (length * 0.55);
		size(half, half);
		return this;
	}

	@Override
	protected float fade(float t) {
		// Arcs snap on and off: no fade in, a short drop at the very end.
		return t < 0.67f ? 1f : 0.6f;
	}

	@Override
	public void extract(QuadParticleRenderState state, Camera camera, float partialTick) {
		Vec3 cameraPos = camera.position();
		float px = (float) (Mth.lerp(partialTick, xo, x) - cameraPos.x);
		float py = (float) (Mth.lerp(partialTick, yo, y) - cameraPos.y);
		float pz = (float) (Mth.lerp(partialTick, zo, z) - cameraPos.z);
		Vector3f toCamera = new Vector3f(-px, -py, -pz);

		if (toCamera.lengthSquared() < 1.0e-6f) {
			return;
		}

		toCamera.normalize();
		Vector3f along = new Vector3f(direction).sub(new Vector3f(toCamera).mul(direction.dot(toCamera)));

		if (along.lengthSquared() < 1.0e-6f) {
			// Seen end-on: a point of light is right.
			super.extract(state, camera, partialTick);
			return;
		}

		along.normalize();
		Vector3f up = new Vector3f(toCamera).cross(along);
		Quaternionf rotation = new Quaternionf().setFromNormalized(new Matrix3f(along, up, toCamera));
		extractRotatedQuad(state, rotation, px, py, pz, partialTick);
	}
}
