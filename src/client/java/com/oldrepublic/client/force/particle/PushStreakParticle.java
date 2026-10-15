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
 * Directional dust streak. The quad faces the camera but is rolled so the streak drawn in the
 * sprite lies along the particle's velocity.
 */
public final class PushStreakParticle extends ForceParticle {
	public PushStreakParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
		super(level, x, y, z, dx, dy, dz, sprites);
		color(0.88f, 0.85f, 0.78f);
		alpha(0.35f);
		size(0.25f, 0.35f);
		randomFrame();
	}

	@Override
	public void extract(QuadParticleRenderState state, Camera camera, float partialTick) {
		Vec3 cameraPos = camera.position();
		float px = (float) (Mth.lerp(partialTick, xo, x) - cameraPos.x);
		float py = (float) (Mth.lerp(partialTick, yo, y) - cameraPos.y);
		float pz = (float) (Mth.lerp(partialTick, zo, z) - cameraPos.z);
		Vector3f toCamera = new Vector3f(-px, -py, -pz);

		if (toCamera.lengthSquared() < 1.0e-6f) {
			super.extract(state, camera, partialTick);
			return;
		}

		toCamera.normalize();
		Vector3f velocity = new Vector3f((float) xd, (float) yd, (float) zd);
		Vector3f along = velocity.sub(new Vector3f(toCamera).mul(velocity.dot(toCamera)), new Vector3f());

		if (along.lengthSquared() < 1.0e-8f) {
			super.extract(state, camera, partialTick);
			return;
		}

		along.normalize();
		Vector3f up = new Vector3f(toCamera).cross(along);
		// Columns: local X along the motion, local Y up, local Z towards the camera.
		Quaternionf rotation = new Quaternionf().setFromNormalized(new Matrix3f(along, up, toCamera));
		extractRotatedQuad(state, rotation, px, py, pz, partialTick);
	}
}
