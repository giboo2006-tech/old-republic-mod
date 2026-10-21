package com.oldrepublic.client.force.energy;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.oldrepublic.client.fx.GlowRibbon;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * A glint: a pinpoint of light that opens into a small four-pointed star and closes again, its points along the screen's
 * axes, as light catches on something for an instant (Serene Ward's protection on a body, a sensed block). Follows an
 * {@link Anchor}.
 */
public final class Glint implements EnergyRender.Shape {
	private static final Vector3f WORLD_UP = new Vector3f(0f, 1f, 0f);

	private final long owner;
	private final Anchor anchor;
	private final Palette palette;
	private final long born;
	private final int life;
	private final float size;
	private final float strength;
	private final float tilt;
	/** This frame. */
	private Vector3f at = new Vector3f();
	private float open;
	private boolean showing;

	/** A glint of {@code palette} on {@code anchor} for {@code life} ticks, its points {@code size} blocks long at the full. */
	public Glint(long owner, Anchor anchor, Palette palette, int life, float size, float strength) {
		this.owner = owner;
		this.anchor = anchor;
		this.palette = palette;
		this.born = EnergyRender.ticks();
		this.life = Math.max(2, life);
		this.size = size;
		this.strength = strength;
		this.tilt = (EnergyKit.RANDOM.nextFloat() - 0.5f) * 0.5f;
	}

	@Override
	public boolean tick(ClientLevel level) {
		return EnergyRender.ticks() - born < life;
	}

	@Override
	public long owner() {
		return owner;
	}

	@Override
	public void prepare(EnergyRender.Frame frame) {
		float t = Mth.clamp((EnergyRender.ticks() - born + frame.partial) / life, 0f, 1f);
		// Opens fast, closes slower.
		open = t < 0.3f ? t / 0.3f : 1f - (t - 0.3f) / 0.7f;
		open = open * open * (3f - 2f * open);
		Vec3 pos = anchor.at(frame.partial);
		showing = pos != null && open > 0.01f;

		if (showing) {
			at = frame.rel(pos);
		}
	}

	@Override
	public void glow(EnergyRender.Frame frame, PoseStack.Pose pose, VertexConsumer buffer) {
		if (!showing) {
			return;
		}

		float near = frame.near(at);
		Vector3f view = new Vector3f(at).sub(frame.eye);

		if (view.lengthSquared() < 1.0e-6f) {
			return;
		}

		view.normalize();
		Vector3f right = new Vector3f(view).cross(WORLD_UP);

		if (right.lengthSquared() < 1.0e-6f) {
			right.set(1f, 0f, 0f);
		}

		right.normalize();
		Vector3f up = new Vector3f(right).cross(view).normalize();
		// Turned a little, each glint its own way.
		Vector3f armA = new Vector3f(right).mul(Mth.cos(tilt)).add(new Vector3f(up).mul(Mth.sin(tilt)));
		Vector3f armB = new Vector3f(up).mul(Mth.cos(tilt)).sub(new Vector3f(right).mul(Mth.sin(tilt)));
		float reach = size * open * near;
		float thin = palette.coreRadius() * 1.6f * near * (0.5f + 0.5f * open);
		float alpha = strength * open;
		GlowRibbon.capsule(pose, buffer, frame.eye, new Vector3f(armA).mul(-reach).add(at), new Vector3f(armA).mul(reach).add(at), thin, palette.glow(),
				alpha);
		GlowRibbon.capsule(pose, buffer, frame.eye, new Vector3f(armB).mul(-reach * 0.8f).add(at), new Vector3f(armB).mul(reach * 0.8f).add(at), thin,
				palette.glow(), alpha);
		GlowRibbon.disc(pose, buffer, frame.eye, at, palette.glowRadius() * near * open, palette.flare(), alpha);
	}

	@Override
	public void solid(EnergyRender.Frame frame, PoseStack.Pose pose, VertexConsumer buffer) {
		if (showing) {
			GlowRibbon.disc(pose, buffer, frame.eye, at, palette.coreRadius() * frame.near(at) * open, palette.core(), Math.min(1f, strength * open));
		}
	}
}
