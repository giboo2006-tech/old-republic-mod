package com.oldrepublic.client.saber;

import com.oldrepublic.saber.SaberParticles;

import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * Particles of the saber feature. {@link SaberFx} creates them through the particle engine and
 * then tints them with the blade colour.
 */
final class SaberParticleProviders {
	static void init() {
		ParticleProviderRegistry registry = ParticleProviderRegistry.getInstance();
		registry.register(SaberParticles.SABER_SPARK, sprites -> (type, level, x, y, z, dx, dy, dz, random) ->
				new Spark(level, x, y, z, dx, dy, dz, sprites.get(random)));
		registry.register(SaberParticles.CONTACT_FLASH, sprites -> (type, level, x, y, z, dx, dy, dz, random) ->
				new Flash(level, x, y, z, sprites.get(random)));
	}

	/** Full-bright, translucent, tintable base. */
	abstract static class SaberParticle extends SingleQuadParticle {
		/** Packed block and sky light 15. */
		private static final int FULL_BRIGHT = 0xF000F0;
		protected int rgb = 0xFFFFFF;

		SaberParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, TextureAtlasSprite sprite) {
			super(level, x, y, z, dx, dy, dz, sprite);
			this.xd = dx;
			this.yd = dy;
			this.zd = dz;
		}

		/** Sets the blade colour this particle cools toward. */
		SaberParticle tint(int color) {
			this.rgb = color & 0xFFFFFF;
			return this;
		}

		@Override
		protected Layer getLayer() {
			return Layer.TRANSLUCENT;
		}

		@Override
		public int getLightCoords(float partialTick) {
			return FULL_BRIGHT;
		}

		protected float lifeFraction() {
			return Math.min(1f, (float) this.age / Math.max(1, this.lifetime));
		}

		protected void blend(float towardTint) {
			float r = ((rgb >> 16) & 0xFF) / 255f;
			float g = ((rgb >> 8) & 0xFF) / 255f;
			float b = (rgb & 0xFF) / 255f;
			setColor(1f + (r - 1f) * towardTint, 1f + (g - 1f) * towardTint, 1f + (b - 1f) * towardTint);
		}
	}

	/** Tiny spark: starts hot white, cools to the blade colour, falls and fades. */
	static final class Spark extends SaberParticle {
		private final float startSize;

		Spark(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, TextureAtlasSprite sprite) {
			super(level, x, y, z, dx, dy, dz, sprite);
			this.lifetime = 6 + this.random.nextInt(5);
			this.gravity = 0.4f;
			this.friction = 0.88f;
			this.hasPhysics = true;
			this.startSize = 0.025f + this.random.nextFloat() * 0.025f;
			this.quadSize = startSize;
			blend(0f);
		}

		@Override
		public void tick() {
			super.tick();
			float f = lifeFraction();
			blend(Math.min(1f, f * 1.6f));
			this.quadSize = startSize * (1f - 0.5f * f);
			setAlpha(1f - f * f);
		}
	}

	/**
	 * Contact flash: a single bright quad for one tick, or, with flashing effects disabled, a soft
	 * low-alpha glow that fades over a few ticks.
	 */
	static final class Flash extends SaberParticle {
		private boolean soft;

		Flash(ClientLevel level, double x, double y, double z, TextureAtlasSprite sprite) {
			super(level, x, y, z, 0, 0, 0, sprite);
			this.lifetime = 1;
			this.gravity = 0f;
			this.hasPhysics = false;
			this.quadSize = 0.6f;
			blend(0.35f);
		}

		/** Switches to the non-flashing variant. */
		Flash soft() {
			this.soft = true;
			this.lifetime = 4;
			this.quadSize = 0.4f;
			setAlpha(0.35f);
			return this;
		}

		@Override
		SaberParticle tint(int color) {
			super.tint(color);
			blend(0.35f);
			return this;
		}

		@Override
		public void tick() {
			super.tick();

			if (soft) {
				setAlpha(0.35f * (1f - lifeFraction()));
			}
		}
	}

	private SaberParticleProviders() {
	}
}
