package com.oldrepublic.client.enemy;

import com.oldrepublic.enemy.EnemyParticles;

import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.LightCoordsUtil;

/** Particle classes and providers for {@link EnemyParticles}. */
final class EnemyParticleProviders {
	static void init() {
		ParticleProviderRegistry registry = ParticleProviderRegistry.getInstance();
		registry.register(EnemyParticles.MUZZLE_GLOW, sprites -> (type, level, x, y, z, dx, dy, dz, random) ->
				new MuzzleGlow(level, x, y, z, dx, dy, dz, sprites.get(random)));
		registry.register(EnemyParticles.BOLT_IMPACT, sprites -> (type, level, x, y, z, dx, dy, dz, random) ->
				new ImpactSpark(level, x, y, z, dx, dy, dz, sprites.get(random)));
		registry.register(EnemyParticles.BOLT_SCORCH, sprites -> (type, level, x, y, z, dx, dy, dz, random) ->
				new Scorch(level, x, y, z, dx, dy, dz, sprites.get(random)));
	}

	/** Shared base: exact velocity (vanilla jitters it), translucent layer. */
	private abstract static class EnemyParticle extends SingleQuadParticle {
		protected float startSize;
		protected float startAlpha = 1f;

		EnemyParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, TextureAtlasSprite sprite) {
			super(level, x, y, z, dx, dy, dz, sprite);
			this.xd = dx;
			this.yd = dy;
			this.zd = dz;
		}

		@Override
		protected Layer getLayer() {
			return Layer.TRANSLUCENT;
		}

		/** Fraction of life used, 0 to 1. */
		protected float lifeFraction() {
			return Math.min(1f, (float) this.age / Math.max(1, this.lifetime));
		}
	}

	/** Small hot flare that shrinks and fades: muzzle tell and shot flash. */
	private static final class MuzzleGlow extends EnemyParticle {
		MuzzleGlow(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, TextureAtlasSprite sprite) {
			super(level, x, y, z, dx, dy, dz, sprite);
			this.lifetime = 6 + this.random.nextInt(4);
			this.gravity = 0f;
			this.friction = 0.75f;
			this.hasPhysics = false;
			this.startSize = 0.09f + this.random.nextFloat() * 0.05f;
			this.quadSize = startSize;
			setColor(1f, 0.38f + this.random.nextFloat() * 0.12f, 0.2f);
		}

		@Override
		public void tick() {
			super.tick();
			float f = lifeFraction();
			this.quadSize = startSize * (1f - 0.6f * f);
			setAlpha(startAlpha * (1f - f));
		}

		@Override
		public int getLightCoords(float partialTick) {
			return LightCoordsUtil.FULL_BRIGHT;
		}
	}

	/** Fast spark streak with gravity, thrown off the impact surface. */
	private static final class ImpactSpark extends EnemyParticle {
		ImpactSpark(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, TextureAtlasSprite sprite) {
			super(level, x, y, z, dx, dy, dz, sprite);
			this.lifetime = 4 + this.random.nextInt(5);
			this.gravity = 0.6f;
			this.friction = 0.9f;
			this.hasPhysics = true;
			this.startSize = 0.035f + this.random.nextFloat() * 0.025f;
			this.quadSize = startSize;
			setColor(1f, 0.62f + this.random.nextFloat() * 0.25f, 0.35f);
		}

		@Override
		public void tick() {
			super.tick();
			float f = lifeFraction();
			// Cools from yellow-white to deep red as it dies.
			setColor(1f, Math.max(0.2f, 0.75f - 0.55f * f), Math.max(0.1f, 0.4f - 0.3f * f));
			setAlpha(1f - f * f);
		}

		@Override
		public int getLightCoords(float partialTick) {
			return LightCoordsUtil.FULL_BRIGHT;
		}
	}

	/** Slow dark wisp rising off a scorched block; lit by the world, not full-bright. */
	private static final class Scorch extends EnemyParticle {
		Scorch(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, TextureAtlasSprite sprite) {
			super(level, x, y, z, dx, dy, dz, sprite);
			this.lifetime = 30 + this.random.nextInt(16);
			this.gravity = -0.01f;
			this.friction = 0.92f;
			this.hasPhysics = false;
			this.startSize = 0.08f + this.random.nextFloat() * 0.05f;
			this.quadSize = startSize;
			this.startAlpha = 0.55f;
			setColor(0.16f, 0.13f, 0.11f);
			setAlpha(startAlpha);
		}

		@Override
		public void tick() {
			super.tick();
			float f = lifeFraction();
			this.quadSize = startSize * (1f + 1.2f * f);
			setAlpha(startAlpha * (1f - f));
		}
	}

	private EnemyParticleProviders() {
	}
}
