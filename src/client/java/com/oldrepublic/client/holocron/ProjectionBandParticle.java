package com.oldrepublic.client.holocron;

import com.oldrepublic.client.fx.Vfx;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.Mth;

/**
 * {@code projection_band}: one faint horizontal band of light across the projected gatekeeper
 * (spec 4.4, 13.2). The texture is a thin soft line; the quad turns only around the vertical axis,
 * so the band stays level whatever the camera's pitch. It drifts slowly upward through the figure
 * and fades in and out. With flashing effects on it breathes gently; with flashing off its alpha
 * is steady, never flickering.
 */
public final class ProjectionBandParticle extends SingleQuadParticle {
	private static final int FULL_BRIGHT = 0xF000F0;

	private float peakAlpha = 0.28f;
	private final float phase;

	ProjectionBandParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
		super(level, x, y, z, sprites.get(level.getRandom()));
		this.xd = dx;
		this.yd = dy;
		this.zd = dz;
		this.gravity = 0f;
		this.friction = 1f;
		this.hasPhysics = false;
		this.lifetime = 28;
		this.quadSize = 0.32f;
		this.alpha = 0f;
		this.phase = random.nextFloat() * Mth.TWO_PI;
	}

	/** Tints the band with a 0xRRGGBB colour. */
	public ProjectionBandParticle tint(int rgb) {
		setColor(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f);
		return this;
	}

	/** Half the band's width in blocks (the figure's shoulder width). */
	public ProjectionBandParticle width(float halfWidth) {
		this.quadSize = halfWidth;
		return this;
	}

	public ProjectionBandParticle alpha(float peak) {
		this.peakAlpha = peak;
		return this;
	}

	public ProjectionBandParticle life(int ticks) {
		setLifetime(Math.max(1, ticks));
		return this;
	}

	@Override
	public FacingCameraMode getFacingCameraMode() {
		return FacingCameraMode.LOOKAT_Y;
	}

	@Override
	protected Layer getLayer() {
		return Layer.TRANSLUCENT;
	}

	@Override
	public int getLightCoords(float partialTick) {
		return FULL_BRIGHT;
	}

	@Override
	public void tick() {
		super.tick();
		float t = Mth.clamp((float) age / Math.max(1, lifetime), 0f, 1f);
		float envelope = Mth.sin(t * Mth.PI);
		// A slow breath with flashing on; a steady band with it off (13: never flicker).
		float breath = Vfx.flashing() ? 0.85f + 0.15f * Mth.sin(age * 0.25f + phase) : 1f;
		alpha = peakAlpha * envelope * breath;
	}
}
