package com.oldrepublic.client.force.kinetic;

import org.joml.Vector3fc;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * A knot of air pressed together at a charging palm: a small, restless ball of {@link BentAir} that swells and grows
 * denser with the charge and churns faster, following the hand. It lives only while it is fed ({@link #feed} every tick)
 * and fades within a few ticks after, so a cancelled or released charge never leaves it hanging.
 */
public final class AirKnot implements KineticRender.Shape {
	/** Ticks it lingers after the last {@link #feed}. */
	private static final int LINGER = 3;

	private final BentAir.Sheet sheet = new BentAir.Sheet();
	private Vec3 at;
	private Vec3 lastAt;
	private float charge;
	private float light = 1f;
	private int unfed;
	private int age;

	public AirKnot(Vec3 at) {
		this.at = at;
		this.lastAt = at;
		sheet.axis(0, 1, 0).spread(Mth.PI, 8, 16).wobble(0.22f).seed(KineticKit.RANDOM.nextFloat() * 50f);
	}

	/** Keeps it alive one more tick, at {@code at}, with the charge (0 to 1) and the world light there. */
	public void feed(Vec3 at, float charge, float light) {
		this.lastAt = this.at;
		this.at = at;
		this.charge = charge;
		this.light = light;
		this.unfed = 0;
	}

	/** Whether it has ended. */
	public boolean gone() {
		return unfed > LINGER;
	}

	@Override
	public boolean tick() {
		age++;
		unfed++;

		if (unfed > 1) {
			// Not fed this tick: it holds where it was.
			lastAt = at;
		}

		return !gone();
	}

	@Override
	public void render(PoseStack.Pose pose, VertexConsumer buffer, Vec3 cam, Vector3fc eye, float partialTick) {
		float fading = unfed <= 1 ? 1f : 1f - Mth.clamp((unfed - 1 + partialTick) / LINGER, 0f, 1f);
		float alpha = fading * Mth.clamp(age / 3f, 0f, 1f) * (0.45f + 0.55f * charge);

		if (alpha <= 0.01f) {
			return;
		}

		float time = (age + partialTick) * (0.35f + 0.5f * charge);
		// A restless swell: the ball breathes unevenly as the air is pressed in.
		float breath = 1f + 0.12f * Mth.sin(time * 2.1f) + 0.06f * Mth.sin(time * 3.7f + 1.3f);
		float radius = (0.16f + 0.16f * charge) * breath;
		float ox = (float) (Mth.lerp(partialTick, lastAt.x, at.x) - cam.x);
		float oy = (float) (Mth.lerp(partialTick, lastAt.y, at.y) - cam.y);
		float oz = (float) (Mth.lerp(partialTick, lastAt.z, at.z) - cam.z);
		float pale = alpha * (0.35f + 0.65f * light);
		BentAir.draw(pose, buffer, eye, sheet, ox, oy, oz, radius, BentAir.PALE, 0.08f, 0.5f, pale, time, 0f);
		BentAir.draw(pose, buffer, eye, sheet, ox, oy, oz, radius * 0.86f, BentAir.DARK, 0.06f, 0.36f, alpha, time, 7.3f);
	}
}
