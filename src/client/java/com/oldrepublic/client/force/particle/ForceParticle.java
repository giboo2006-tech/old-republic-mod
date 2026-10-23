package com.oldrepublic.client.force.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.Mth;

/**
 * Base for the force feature's particles: translucent, velocity taken exactly as given (no vanilla
 * randomisation), size interpolated over the lifetime, alpha fading out. Configured after spawning
 * through the fluent setters (see {@code PushFx}).
 */
public abstract class ForceParticle extends SingleQuadParticle {
	/** Packed block and sky light at full brightness. */
	private static final int FULL_BRIGHT = 0xF000F0;
	protected final SpriteSet sprites;
	private float startSize = 0.2f;
	private float endSize = 0.2f;
	private float baseAlpha = 1f;
	private boolean animated;
	private boolean fullBright;

	protected ForceParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
		super(level, x, y, z, sprites.first());
		this.sprites = sprites;
		this.xd = dx;
		this.yd = dy;
		this.zd = dz;
		this.gravity = 0f;
		this.friction = 1f;
		this.hasPhysics = false;
		this.lifetime = 10;
	}

	@Override
	public Layer getLayer() {
		return Layer.TRANSLUCENT;
	}

	/** Size grows (or shrinks) linearly from {@code start} to {@code end} over the lifetime. */
	public ForceParticle size(float start, float end) {
		this.startSize = start;
		this.endSize = end;
		return this;
	}

	public ForceParticle alpha(float alpha) {
		this.baseAlpha = alpha;
		setAlpha(alpha);
		return this;
	}

	public ForceParticle life(int ticks) {
		setLifetime(Math.max(1, ticks));
		return this;
	}

	public ForceParticle color(float r, float g, float b) {
		setColor(r, g, b);
		return this;
	}

	/** Velocity multiplier per tick (1 keeps speed). */
	public ForceParticle drag(float drag) {
		this.friction = drag;
		return this;
	}

	public ForceParticle fall(float gravity, boolean collide) {
		this.gravity = gravity;
		this.hasPhysics = collide;
		return this;
	}

	/** Steps through the sprite set over the lifetime instead of keeping one random frame. */
	public ForceParticle animated() {
		this.animated = true;
		setSpriteFromAge(sprites);
		return this;
	}

	/** Ignores world light (lightning, sparks and glints are their own light). */
	public ForceParticle fullBright() {
		this.fullBright = true;
		return this;
	}

	@Override
	public int getLightCoords(float partialTick) {
		return fullBright ? FULL_BRIGHT : super.getLightCoords(partialTick);
	}

	/** Picks one frame of the sprite set at random. */
	public ForceParticle randomFrame() {
		setSprite(sprites.get(random));
		return this;
	}

	@Override
	public float getQuadSize(float partialTick) {
		float t = Mth.clamp((age + partialTick) / (float) lifetime, 0f, 1f);
		return Mth.lerp(t, startSize, endSize);
	}

	@Override
	public void tick() {
		super.tick();

		if (!isAlive()) {
			return;
		}

		float t = Mth.clamp(age / (float) lifetime, 0f, 1f);
		setAlpha(baseAlpha * fade(t));

		if (animated) {
			setSpriteFromAge(sprites);
		}
	}

	/** Alpha multiplier at normalised age {@code t}. Default: quick fade-in, eased fade-out. */
	protected float fade(float t) {
		float in = Math.min(1f, t * 6f);
		float out = 1f - t;
		return in * out * out;
	}
}
