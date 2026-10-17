package com.oldrepublic.client.holocron;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.Mth;

/**
 * {@code holocron_mote}: a slow, soft speck of seam light or alcove dust (spec 13.2). Velocity is
 * taken exactly as given, there is no gravity or collision, the size moves linearly between two
 * values and the alpha rises and falls over the lifetime, so motes never pop. Configured after
 * spawning through the fluent setters ({@link HolocronFx#mote}): tint, size, life, drag, and
 * whether it glows (seam light) or takes the world light (dust).
 */
public final class HolocronMoteParticle extends SingleQuadParticle {
	/** Packed block and sky light at full brightness. */
	private static final int FULL_BRIGHT = 0xF000F0;

	private float startSize = 0.05f;
	private float endSize = 0.03f;
	private float peakAlpha = 0.6f;
	private boolean glowing = true;

	HolocronMoteParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
		super(level, x, y, z, sprites.get(level.getRandom()));
		this.xd = dx;
		this.yd = dy;
		this.zd = dz;
		this.gravity = 0f;
		this.friction = 1f;
		this.hasPhysics = false;
		this.lifetime = 30;
		this.alpha = 0f;
	}

	/** Tints the mote with a 0xRRGGBB colour. */
	public HolocronMoteParticle tint(int rgb) {
		setColor(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f);
		return this;
	}

	/** Size grows or shrinks linearly from {@code start} to {@code end} over the lifetime. */
	public HolocronMoteParticle size(float start, float end) {
		this.startSize = start;
		this.endSize = end;
		return this;
	}

	/** The alpha at the middle of the lifetime. */
	public HolocronMoteParticle alpha(float peak) {
		this.peakAlpha = peak;
		return this;
	}

	public HolocronMoteParticle life(int ticks) {
		setLifetime(Math.max(1, ticks));
		return this;
	}

	/** Velocity multiplier per tick (1 keeps speed). */
	public HolocronMoteParticle drag(float drag) {
		this.friction = drag;
		return this;
	}

	/** Takes the light of where it floats, like real dust, instead of glowing. */
	public HolocronMoteParticle dust() {
		this.glowing = false;
		return this;
	}

	/** Falls slowly, as settling grit does: {@code gravity} is a fraction of normal particle gravity (0.04 blocks per tick²). */
	public HolocronMoteParticle fall(float gravity) {
		this.gravity = gravity;
		return this;
	}

	@Override
	protected Layer getLayer() {
		return Layer.TRANSLUCENT;
	}

	@Override
	public int getLightCoords(float partialTick) {
		return glowing ? FULL_BRIGHT : super.getLightCoords(partialTick);
	}

	@Override
	public float getQuadSize(float partialTick) {
		float t = Mth.clamp((age + partialTick) / Math.max(1, lifetime), 0f, 1f);
		return Mth.lerp(t, startSize, endSize);
	}

	@Override
	public void tick() {
		super.tick();
		float t = Mth.clamp((float) age / Math.max(1, lifetime), 0f, 1f);
		alpha = peakAlpha * Mth.sin(t * Mth.PI);
	}
}
