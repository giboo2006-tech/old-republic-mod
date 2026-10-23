package com.oldrepublic.client.force.kinetic;

import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.oldrepublic.client.force.particle.ForceParticle;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * A streak of bent air ({@code kinetic_streak}): a thin pale line with a dark fringe, lying along its own motion and
 * longer the faster it flies, so a gust reads as rushing air. It faces the camera, rolled so the line follows the motion
 * on screen. Lit by the world: no glow.
 */
public final class AirStreakParticle extends ForceParticle {
	/** Distance from the camera within which a streak is not drawn, and from which it is drawn in full. */
	private static final float NEAR_GONE = 0.9f;
	private static final float NEAR_FULL = 3.2f;
	private float base = 0.3f;
	/** Extra length (as quad size) per block a tick of speed. */
	private float stretch = 0.35f;
	private final Vector3f heading = new Vector3f(1f, 0f, 0f);

	public AirStreakParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
		super(level, x, y, z, dx, dy, dz, sprites);
		color(0.93f, 0.94f, 0.95f);
		alpha(0.4f);
		size(0.3f, 0.3f);
		life(8);
		drag(0.84f);
		randomFrame();
		remember();
	}

	@Override
	public ForceParticle size(float start, float end) {
		this.base = start;
		return super.size(start, end);
	}

	/** Extra length per block a tick of speed. */
	public AirStreakParticle stretch(float stretch) {
		this.stretch = stretch;
		return this;
	}

	@Override
	public void tick() {
		super.tick();
		remember();
	}

	/** Keeps the last real heading, so a streak that has slowed to nothing still lies the way it flew. */
	private void remember() {
		Vector3f motion = new Vector3f((float) xd, (float) yd, (float) zd);

		if (motion.lengthSquared() > 1.0e-6f) {
			heading.set(motion.normalize());
		}
	}

	@Override
	public float getQuadSize(float partialTick) {
		float speed = (float) Math.sqrt(xd * xd + yd * yd + zd * zd);
		return base + Math.min(1.2f, speed * stretch);
	}

	@Override
	public void extract(QuadParticleRenderState state, Camera camera, float partialTick) {
		Vec3 cameraPos = camera.position();
		float px = (float) (Mth.lerp(partialTick, xo, x) - cameraPos.x);
		float py = (float) (Mth.lerp(partialTick, yo, y) - cameraPos.y);
		float pz = (float) (Mth.lerp(partialTick, zo, z) - cameraPos.z);
		Vector3f toCamera = new Vector3f(-px, -py, -pz);
		float distance = toCamera.length();
		// Right in front of the eyes a streak would fill the view: it fades out as it nears the camera.
		float near = Mth.clamp((distance - NEAR_GONE) / (NEAR_FULL - NEAR_GONE), 0f, 1f);

		if (near <= 0.01f) {
			return;
		}

		Vector3f along = new Vector3f(heading).sub(new Vector3f(toCamera).div(distance).mul(heading.dot(toCamera) / distance));

		if (along.lengthSquared() < 1.0e-6f) {
			return;
		}

		toCamera.div(distance);
		along.normalize();
		Vector3f up = new Vector3f(toCamera).cross(along);
		// Columns: local X along the motion, local Y across it, local Z towards the camera.
		Quaternionf rotation = new Quaternionf().setFromNormalized(new Matrix3f(along, up, toCamera));
		float full = this.alpha;
		this.alpha = full * near * near;
		extractRotatedQuad(state, rotation, px, py, pz, partialTick);
		this.alpha = full;
	}

	@Override
	protected float fade(float t) {
		float in = Math.min(1f, t * 5f);
		return in * (1f - t * t);
	}
}
