package com.oldrepublic.client.boss.cantor;

import com.oldrepublic.boss.cantor.CantorParticles;

import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.Mth;

/**
 * W8's {@code spectral_wisp} particle (spec MILESTONE_5.md 15.2): a pale streak of sung light that
 * drifts on the given velocity, slows, lifts a little and fades in and out (three frames by age:
 * {@code textures/particle/spectral_wisp_<0..2>.png}). It glows (full-bright) and passes through
 * blocks. Near-white with a cold cast; {@link #spawnTinted} colours one (Ulvaine's echo coming apart).
 */
public final class SpectralWispParticle extends SingleQuadParticle {
	private static final int FULL_BRIGHT = 0xF000F0;

	private final SpriteSet sprites;
	private final float startSize;
	private final float peakAlpha;

	SpectralWispParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
		super(level, x, y, z, dx, dy, dz, sprites.get(level.getRandom()));
		this.sprites = sprites;
		this.xd = dx;
		this.yd = dy;
		this.zd = dz;
		this.gravity = -0.004f;
		this.friction = 0.97f;
		this.hasPhysics = false;
		this.lifetime = 24 + this.random.nextInt(16);
		this.startSize = 0.06f + this.random.nextFloat() * 0.06f;
		this.quadSize = startSize;
		this.roll = this.random.nextFloat() * Mth.TWO_PI;
		this.oRoll = this.roll;
		this.peakAlpha = 0.65f + this.random.nextFloat() * 0.3f;
		float cast = this.random.nextFloat() * 0.08f;
		setColor(0.86f + cast, 0.92f + cast * 0.5f, 1f);
		setAlpha(0f);
		setSpriteFromAge(sprites);
	}

	/** Registers the provider. Called once from {@code FoesM5Client}. */
	public static void register() {
		ParticleProviderRegistry.getInstance().register(CantorParticles.SPECTRAL_WISP,
				sprites -> (type, level, x, y, z, dx, dy, dz, random) -> new SpectralWispParticle(level, x, y, z, dx, dy, dz, sprites));
	}

	/** One wisp in colour {@code rgb} (0xRRGGBB), spawned straight into the particle engine. */
	public static void spawnTinted(double x, double y, double z, double dx, double dy, double dz, int rgb) {
		Particle particle = Minecraft.getInstance().particleEngine.createParticle(CantorParticles.SPECTRAL_WISP, x, y, z, dx, dy, dz);

		if (particle instanceof SpectralWispParticle wisp) {
			wisp.setColor(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f);
		}
	}

	@Override
	public void tick() {
		super.tick();

		if (!this.removed) {
			setSpriteFromAge(sprites);
			float t = Mth.clamp((float) this.age / Math.max(1, this.lifetime), 0f, 1f);
			// Fades in over the first fifth, holds, and thins away; it stretches a little as it goes.
			float fade = t < 0.2f ? t / 0.2f : 1f - Mth.clamp((t - 0.55f) / 0.45f, 0f, 1f);
			setAlpha(peakAlpha * fade);
			this.quadSize = startSize * (1f + 0.5f * t);
		}
	}

	@Override
	protected int getLightCoords(float partialTick) {
		return FULL_BRIGHT;
	}

	@Override
	protected Layer getLayer() {
		return Layer.TRANSLUCENT;
	}
}
