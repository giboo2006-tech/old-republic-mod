package com.oldrepublic.client.force.kinetic;

import org.joml.Vector3fc;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * A front of pressure travelling out from a point: a sheet of {@link BentAir} that races out and slows as it spends itself,
 * the way a blast wave does, and thins away before it stops. A cap round an axis for a push's cone, the whole sphere for a
 * pulse from the body, a shallow cap for a wave off a wall. Drawn in light and dark sheets only: no glow.
 */
public final class PressureFront implements KineticRender.Shape {
	private final Vec3 origin;
	private final BentAir.Sheet sheet = new BentAir.Sheet();
	private float from = 0.3f;
	private float to = 6f;
	private int life = 10;
	/** Larger: the front leaves faster and slows sooner. */
	private float burst = 2.2f;
	private float strength = 1f;
	/** World light at the front, 0 to 1: the pale sheet only shows as much as the light allows. */
	private float light = 1f;
	/** Where in its life (0 to 1) it starts to thin away. */
	private float fadeFrom = 0.4f;
	private float thickness = 0.06f;
	private int age;

	public PressureFront(Vec3 origin, Vec3 axis) {
		this.origin = origin;
		sheet.axis(axis.x, axis.y, axis.z).spread(Mth.PI / 4f, 7, 28).seed(KineticKit.RANDOM.nextFloat() * 50f);
	}

	/** The cap's half-extent from the axis in degrees (180 or more: the whole sphere), and its grid. */
	public PressureFront spread(float degrees) {
		float radians = Math.min(Mth.PI, degrees * Mth.DEG_TO_RAD);
		int rings = radians >= Mth.PI - 1.0e-3f ? 12 : Math.max(4, Math.round(radians / 0.11f));
		sheet.spread(radians, Math.min(rings, 12), radians < 0.35f ? 18 : 28);
		return this;
	}

	/** Squash across the axis, upward (a low, wide wave below 1). */
	public PressureFront flatten(float flatten) {
		sheet.flatten(flatten);
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

	/** How sharply it leaves and slows (1 steady; 2 to 3 a blast). */
	public PressureFront burst(float burst) {
		this.burst = Math.max(1f, burst);
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

	/** Gap between the pale and the dark sheet, as a fraction of the radius. */
	public PressureFront thickness(float thickness) {
		this.thickness = thickness;
		return this;
	}

	public PressureFront wobble(float wobble) {
		sheet.wobble(wobble);
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

	@Override
	public void render(PoseStack.Pose pose, VertexConsumer buffer, Vec3 cam, Vector3fc eye, float partialTick) {
		float ticks = age + partialTick;
		float t = ticks / life;

		if (t >= 1f) {
			return;
		}

		float radius = radiusAt(ticks);
		float envelope = strength * Mth.clamp(ticks / 0.6f, 0f, 1f) * (1f - BentAir.smoothstep(fadeFrom, 1f, t));
		float time = ticks * 0.3f;
		float ox = (float) (origin.x - cam.x);
		float oy = (float) (origin.y - cam.y);
		float oz = (float) (origin.z - cam.z);
		float pale = envelope * (0.3f + 0.7f * light);
		BentAir.draw(pose, buffer, eye, sheet, ox, oy, oz, radius, BentAir.PALE, 0.13f, 0.6f, pale, time, 0f);
		BentAir.draw(pose, buffer, eye, sheet, ox, oy, oz, radius * (1f - thickness) - 0.03f, BentAir.DARK, 0.08f, 0.38f, envelope, time, 7.3f);
	}
}
