package com.oldrepublic.client.force.energy;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.oldrepublic.client.fx.GlowRibbon;
import com.oldrepublic.client.fx.Vfx;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * A point of light on an {@link Anchor}: the glare of a hand full of lightning, the flash where a bolt strikes, the warmth
 * gathered at a chest. It fades in, holds and fades out over its life (a strike starts at full strength and dies away),
 * may grow or shrink, may flicker (never with flashing effects off), and can be drawn a little toward the camera so a
 * flare on a body shows in front of it. A held flare is kept alive tick by tick ({@link #keep}) by the effect that owns it.
 * Set up with the fluent setters before it is added.
 */
public final class Flare implements EnergyRender.Shape {
	private final long owner;
	private final Anchor anchor;
	private final Palette palette;
	private final long born;
	private final float seed;
	private int life = 4;
	private float fadeIn;
	private float fadeOut = 4f;
	/** How sharply the fade-out falls (1 linear, 2 a quick drop then a tail). */
	private float fall = 1f;
	private float radius = 0.3f;
	private float endRadius = -1f;
	private float strength = 1f;
	private float core;
	private float flicker;
	private float lift;
	/** This frame. */
	private Vector3f at = new Vector3f();
	private float alpha;
	private float size;
	private boolean showing;

	private Flare(long owner, Anchor anchor, Palette palette) {
		this.owner = owner;
		this.anchor = anchor;
		this.palette = palette;
		this.born = EnergyRender.ticks();
		this.seed = EnergyKit.RANDOM.nextFloat() * 100f;
	}

	/** A flare of {@code palette} on {@code anchor}, belonging to {@code owner}. */
	public static Flare on(long owner, Anchor anchor, Palette palette) {
		return new Flare(owner, anchor, palette);
	}

	/** Lasts {@code ticks}, fading in over {@code in} ticks and out over the last {@code out}. */
	public Flare life(int ticks, float in, float out) {
		this.life = Math.max(1, ticks);
		this.fadeIn = in;
		this.fadeOut = Math.max(0.5f, out);
		return this;
	}

	/** A strike: full strength at once, dying away over its {@code ticks}, fast at first. */
	public Flare burst(int ticks) {
		life(ticks, 0f, ticks);
		this.fall = 2f;
		return this;
	}

	/** Radius in blocks (the light's full reach), growing or shrinking to {@code end} over the life. */
	public Flare radius(float start, float end) {
		this.radius = start;
		this.endRadius = end;
		return this;
	}

	public Flare radius(float radius) {
		return radius(radius, radius);
	}

	/** Strength, 0 to about 1.2. */
	public Flare strength(float strength) {
		this.strength = strength;
		return this;
	}

	/** A small solid core of this radius, so the point still shows by day. */
	public Flare core(float radius) {
		this.core = radius;
		return this;
	}

	/** Flickers by up to {@code amount} (0 to 1) of its strength from frame to frame; nothing with flashing effects off. */
	public Flare flicker(float amount) {
		this.flicker = Vfx.flashing() ? amount : 0f;
		return this;
	}

	/** Drawn {@code blocks} toward the camera from its anchor. */
	public Flare lift(float blocks) {
		this.lift = blocks;
		return this;
	}

	/** Keeps a held flare showing for at least {@code ticks} more. */
	public void keep(int ticks) {
		life = (int) Math.max(life, EnergyRender.ticks() - born + ticks);
	}

	/** Lets a held flare fade out now over {@code ticks}. */
	public void release(int ticks) {
		long age = EnergyRender.ticks() - born;
		life = (int) Math.min(life, age + Math.max(1, ticks));
		fadeOut = Math.max(0.5f, ticks);
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
		float age = EnergyRender.ticks() - born + frame.partial;
		Vec3 pos = anchor.at(frame.partial);
		float in = fadeIn <= 0f ? 1f : Mth.clamp(age / fadeIn, 0f, 1f);
		float out = (float) Math.pow(Mth.clamp((life - age) / fadeOut, 0f, 1f), fall);
		alpha = strength * in * out * (1f - flicker * 0.5f * (1f + EnergyKit.noise(seed + age * 6.1f)));
		showing = pos != null && alpha > 0.004f;

		if (!showing) {
			return;
		}

		at = frame.rel(pos);

		if (lift > 0f && at.lengthSquared() > lift * lift * 4f) {
			at.sub(new Vector3f(at).normalize().mul(lift));
		}

		float grown = endRadius < 0f ? radius : Mth.lerp(Mth.clamp(age / life, 0f, 1f), radius, endRadius);
		size = grown * frame.near(at);
	}

	@Override
	public void glow(EnergyRender.Frame frame, PoseStack.Pose pose, VertexConsumer buffer) {
		if (showing) {
			GlowRibbon.disc(pose, buffer, frame.eye, at, size, palette.flare(), alpha);
		}
	}

	@Override
	public void solid(EnergyRender.Frame frame, PoseStack.Pose pose, VertexConsumer buffer) {
		if (showing && core > 0f) {
			GlowRibbon.disc(pose, buffer, frame.eye, at, core * size / Math.max(0.001f, radius), palette.core(), Math.min(1f, alpha));
		}
	}
}
