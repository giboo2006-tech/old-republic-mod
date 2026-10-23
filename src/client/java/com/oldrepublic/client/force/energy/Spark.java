package com.oldrepublic.client.force.energy;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.oldrepublic.client.fx.GlowRibbon;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * A spark or an ember: a speck of light thrown off with a speed of its own, pulled down, slowed by the air, bouncing once
 * or twice off the ground and dying where it lands. Drawn as a short streak along its motion (longer the faster it goes),
 * hot first ({@code hot}), then cooling ({@code cool}) as it fades.
 */
public final class Spark implements EnergyRender.Shape {
	/** How many ticks of motion the streak shows. */
	private static final float STREAK = 1.3f;
	private static final float MIN_STREAK = 0.015f;

	private final long owner;
	private final Palette hot;
	private final Palette cool;
	private final int life;
	private final float size;
	private final float gravity;
	private final float drag;
	private double x;
	private double y;
	private double z;
	private double xo;
	private double yo;
	private double zo;
	private double vx;
	private double vy;
	private double vz;
	private int age;
	private int bounces;
	/** This frame. */
	private Vector3f head = new Vector3f();
	private Vector3f tail = new Vector3f();
	private float alpha;
	private float near;
	private boolean showing;

	/**
	 * A spark at {@code pos} moving {@code velocity} blocks a tick, living {@code life} ticks, {@code size} times a normal
	 * spark's width, falling by {@code gravity} a tick and keeping {@code drag} of its speed each tick.
	 */
	public Spark(long owner, Vec3 pos, Vec3 velocity, int life, float size, float gravity, float drag, Palette hot, Palette cool) {
		this.owner = owner;
		this.x = this.xo = pos.x;
		this.y = this.yo = pos.y;
		this.z = this.zo = pos.z;
		this.vx = velocity.x;
		this.vy = velocity.y;
		this.vz = velocity.z;
		this.life = Math.max(1, life);
		this.size = size;
		this.gravity = gravity;
		this.drag = drag;
		this.hot = hot;
		this.cool = cool;
	}

	@Override
	public boolean tick(ClientLevel level) {
		if (++age >= life) {
			return false;
		}

		xo = x;
		yo = y;
		zo = z;
		vy -= gravity;
		vx *= drag;
		vy *= drag;
		vz *= drag;
		double nx = x + vx;
		double ny = y + vy;
		double nz = z + vz;

		if (EnergyKit.solid(level, nx, ny, nz)) {
			if (vy < 0 && !EnergyKit.solid(level, nx, y, nz)) {
				// Landed: bounce low and skid, losing heat.
				ny = Math.floor(ny) + 1.01;
				vy = -vy * 0.32;
				vx *= 0.55;
				vz *= 0.55;
			} else {
				vx *= -0.25;
				vz *= -0.25;
				nx = x;
				nz = z;
			}

			if (++bounces > 2) {
				return false;
			}
		}

		x = nx;
		y = ny;
		z = nz;
		return true;
	}

	@Override
	public long owner() {
		return owner;
	}

	@Override
	public void prepare(EnergyRender.Frame frame) {
		float t = (age + frame.partial) / life;
		alpha = (1f - t * t) * (bounces > 0 ? 0.75f : 1f);
		showing = alpha > 0.01f;

		if (!showing) {
			return;
		}

		float p = frame.partial;
		head = frame.rel(Mth.lerp(p, xo, x), Mth.lerp(p, yo, y), Mth.lerp(p, zo, z));
		Vector3f motion = new Vector3f((float) vx, (float) vy, (float) vz).mul(STREAK);

		if (motion.lengthSquared() < MIN_STREAK * MIN_STREAK) {
			motion.set(0f, -MIN_STREAK, 0f);
		}

		tail = new Vector3f(head).sub(motion);
		near = frame.near(head);
	}

	@Override
	public void glow(EnergyRender.Frame frame, PoseStack.Pose pose, VertexConsumer buffer) {
		if (showing) {
			Palette palette = paletteNow(frame);
			GlowRibbon.capsule(pose, buffer, frame.eye, tail, head, palette.glowRadius() * size * near, palette.glow(), alpha);
		}
	}

	@Override
	public void solid(EnergyRender.Frame frame, PoseStack.Pose pose, VertexConsumer buffer) {
		if (showing) {
			Palette palette = paletteNow(frame);
			GlowRibbon.capsule(pose, buffer, frame.eye, tail, head, palette.coreRadius() * size * near, palette.core(), alpha);
		}
	}

	private Palette paletteNow(EnergyRender.Frame frame) {
		return (age + frame.partial) / life < 0.4f ? hot : cool;
	}
}
