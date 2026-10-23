package com.oldrepublic.client.enemy;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.enemy.EnemyParticles;

import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;

/**
 * Particle classes and providers for {@link EnemyParticles}. Effects that need a tint, size or
 * life of their own spawn through {@link #spawn} and adjust the returned particle (the equipment
 * flourish uses them too).
 */
public final class EnemyParticleProviders {
	static void init() {
		ParticleProviderRegistry registry = ParticleProviderRegistry.getInstance();
		registry.register(EnemyParticles.MUZZLE_GLOW, sprites -> (type, level, x, y, z, dx, dy, dz, random) ->
				new MuzzleGlow(level, x, y, z, dx, dy, dz, sprites.get(random)));
		registry.register(EnemyParticles.BOLT_IMPACT, sprites -> (type, level, x, y, z, dx, dy, dz, random) ->
				new ImpactSpark(level, x, y, z, dx, dy, dz, sprites.get(random)));
		registry.register(EnemyParticles.BOLT_SCORCH, sprites -> (type, level, x, y, z, dx, dy, dz, random) ->
				new Scorch(level, x, y, z, dx, dy, dz, sprites.get(random)));
		registry.register(EnemyParticles.KOLTO_MIST, sprites -> (type, level, x, y, z, dx, dy, dz, random) ->
				new KoltoMist(level, x, y, z, dx, dy, dz, sprites.get(random)));
		registry.register(EnemyParticles.VENT_STEAM, sprites -> (type, level, x, y, z, dx, dy, dz, random) ->
				new VentSteam(level, x, y, z, dx, dy, dz, sprites.get(random)));
		registry.register(EnemyParticles.GRENADE_SMOKE, sprites -> (type, level, x, y, z, dx, dy, dz, random) ->
				new GrenadeSmoke(level, x, y, z, dx, dy, dz, sprites.get(random)));
		registry.register(EnemyParticles.FLARE_EMBER, sprites -> (type, level, x, y, z, dx, dy, dz, random) ->
				new FlareEmber(level, x, y, z, dx, dy, dz, sprites.get(random)));
	}

	/**
	 * Spawns one of the enemy particles at {@code pos} with {@code velocity} through the particle
	 * engine, or returns null if the engine refused it (minimal particles, budget).
	 */
	public static @Nullable EnemyParticle spawn(SimpleParticleType type, Vec3 pos, Vec3 velocity) {
		Particle particle = Minecraft.getInstance().particleEngine.createParticle(type, pos.x, pos.y, pos.z, velocity.x, velocity.y, velocity.z);
		return particle instanceof EnemyParticle enemy ? enemy : null;
	}

	/** Shared base: exact velocity (vanilla jitters it), translucent layer, and fluent adjusters for effects. */
	public abstract static class EnemyParticle extends SingleQuadParticle {
		protected float startSize;
		protected float startAlpha = 1f;
		/** Full bright regardless of the world light. */
		protected boolean glowing;

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

		@Override
		public int getLightCoords(float partialTick) {
			return glowing ? LightCoordsUtil.FULL_BRIGHT : super.getLightCoords(partialTick);
		}

		/** Tints it with a 0xRRGGBB colour. */
		public EnemyParticle tint(int rgb) {
			setColor(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f);
			return this;
		}

		public EnemyParticle size(float size) {
			this.startSize = size;
			this.quadSize = size;
			return this;
		}

		public EnemyParticle life(int ticks) {
			this.lifetime = Math.max(1, ticks);
			return this;
		}

		public EnemyParticle alpha(float alpha) {
			this.startAlpha = alpha;
			setAlpha(alpha);
			return this;
		}

		public EnemyParticle gravity(float gravity) {
			this.gravity = gravity;
			return this;
		}

		public EnemyParticle bright() {
			this.glowing = true;
			return this;
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
			this.startSize = 0.14f + this.random.nextFloat() * 0.08f;
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

	/** A soft teal-green puff that swells, drifts and thins out; faintly self-lit so it reads in shade. */
	private static final class KoltoMist extends EnemyParticle {
		KoltoMist(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, TextureAtlasSprite sprite) {
			super(level, x, y, z, dx, dy, dz, sprite);
			this.lifetime = 18 + this.random.nextInt(10);
			this.gravity = -0.004f;
			this.friction = 0.9f;
			this.hasPhysics = false;
			this.startSize = 0.09f + this.random.nextFloat() * 0.06f;
			this.quadSize = startSize;
			this.startAlpha = 0.7f;
			setColor(0.42f, 0.95f, 0.82f);
			setAlpha(startAlpha);
		}

		@Override
		public void tick() {
			super.tick();
			float f = lifeFraction();
			this.quadSize = startSize * (1f + 0.9f * f);
			setAlpha(startAlpha * (1f - f) * Math.min(1f, f * 6f + 0.2f));
		}

		@Override
		public int getLightCoords(float partialTick) {
			return LightCoordsUtil.FULL_BRIGHT;
		}
	}

	/** A white steam puff: fast out of the vent, then slowing, rising and spreading. */
	private static final class VentSteam extends EnemyParticle {
		VentSteam(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, TextureAtlasSprite sprite) {
			super(level, x, y, z, dx, dy, dz, sprite);
			this.lifetime = 12 + this.random.nextInt(10);
			this.gravity = -0.02f;
			this.friction = 0.82f;
			this.hasPhysics = false;
			this.startSize = 0.07f + this.random.nextFloat() * 0.05f;
			this.quadSize = startSize;
			this.startAlpha = 0.6f;
			setColor(0.93f, 0.95f, 0.97f);
			setAlpha(startAlpha);
		}

		@Override
		public void tick() {
			super.tick();
			float f = lifeFraction();
			this.quadSize = startSize * (1f + 2.2f * f);
			setAlpha(startAlpha * (1f - f));
		}
	}

	/** Grey-brown smoke: billows, rises slowly and lingers; lit by the world. */
	private static final class GrenadeSmoke extends EnemyParticle {
		GrenadeSmoke(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, TextureAtlasSprite sprite) {
			super(level, x, y, z, dx, dy, dz, sprite);
			this.lifetime = 28 + this.random.nextInt(22);
			this.gravity = -0.006f;
			this.friction = 0.86f;
			this.hasPhysics = true;
			this.startSize = 0.18f + this.random.nextFloat() * 0.14f;
			this.quadSize = startSize;
			this.startAlpha = 0.7f;
			float grey = 0.3f + this.random.nextFloat() * 0.12f;
			setColor(grey * 1.05f, grey, grey * 0.92f);
			setAlpha(startAlpha);
		}

		@Override
		public void tick() {
			super.tick();
			float f = lifeFraction();
			this.quadSize = startSize * (1f + 1.4f * f);
			setAlpha(startAlpha * (1f - f * f));
		}
	}

	/** A bright ember: falls slowly, cools from white-hot to deep red and winks out. */
	private static final class FlareEmber extends EnemyParticle {
		FlareEmber(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, TextureAtlasSprite sprite) {
			super(level, x, y, z, dx, dy, dz, sprite);
			this.lifetime = 14 + this.random.nextInt(12);
			this.gravity = 0.08f;
			this.friction = 0.94f;
			this.hasPhysics = true;
			this.startSize = 0.05f + this.random.nextFloat() * 0.04f;
			this.quadSize = startSize;
			setColor(1f, 0.55f, 0.25f);
		}

		@Override
		public void tick() {
			super.tick();
			float f = lifeFraction();
			this.quadSize = startSize * (1f - 0.5f * f);
			setAlpha(startAlpha * (1f - f * f));
		}

		@Override
		public int getLightCoords(float partialTick) {
			return LightCoordsUtil.FULL_BRIGHT;
		}
	}

	private EnemyParticleProviders() {
	}
}
