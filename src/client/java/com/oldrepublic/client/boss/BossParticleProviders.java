package com.oldrepublic.client.boss;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.boss.BossParticles;

import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Particles of the Collector fight (spec 13.2), drawn from B's frames ({@code textures/particle/<id>_<n>.png}).
 * Velocities are used exactly as given, so effect code controls the motion.
 * <ul>
 * <li>{@code ward_haze}: a sparse heat-haze speck that rises off him and wavers; near-white, tinted.</li>
 * <li>{@code ward_current}: a full-bright crystal mote with a short tail, wavering along its path from
 * a singing pylon to him; tinted pale crystal (amber for the guttering blade).</li>
 * <li>{@code surge_dust}: a sandy dust puff that billows, slows and settles; four frames by age, natural
 * colour; collides with blocks.</li>
 * <li>{@code lash_seam}: grit and dust thrown up along the Lash seam; falls back, natural colour.</li>
 * <li>{@code echo_smoke}: slow grey smoke of the echo; four frames by age, tinted.</li>
 * </ul>
 * Effects that need their own tint, size or life spawn through {@link #spawn} and adjust the result.
 */
final class BossParticleProviders {
	/** Packed block and sky light 15. */
	private static final int FULL_BRIGHT = 0xF000F0;

	static void init() {
		ParticleProviderRegistry registry = ParticleProviderRegistry.getInstance();
		registry.register(BossParticles.WARD_HAZE, sprites -> (type, level, x, y, z, dx, dy, dz, random) -> new WardHaze(level, x, y, z, dx, dy, dz, sprites));
		registry.register(BossParticles.WARD_CURRENT, sprites -> (type, level, x, y, z, dx, dy, dz, random) -> new WardCurrent(level, x, y, z, dx, dy, dz, sprites));
		registry.register(BossParticles.SURGE_DUST, sprites -> (type, level, x, y, z, dx, dy, dz, random) -> new SurgeDust(level, x, y, z, dx, dy, dz, sprites));
		registry.register(BossParticles.LASH_SEAM, sprites -> (type, level, x, y, z, dx, dy, dz, random) -> new LashSeam(level, x, y, z, dx, dy, dz, sprites));
		registry.register(BossParticles.ECHO_SMOKE, sprites -> (type, level, x, y, z, dx, dy, dz, random) -> new EchoSmoke(level, x, y, z, dx, dy, dz, sprites));
	}

	/**
	 * Creates one of the boss particles through the particle engine (so it counts toward the vanilla
	 * particle limit), or null if the engine refused it (minimal particles).
	 */
	static @Nullable BossParticle spawn(SimpleParticleType type, Vec3 pos, Vec3 velocity) {
		Particle particle = Minecraft.getInstance().particleEngine.createParticle(type, pos.x, pos.y, pos.z, velocity.x, velocity.y, velocity.z);
		return particle instanceof BossParticle boss ? boss : null;
	}

	/** Shared base: exact velocity, translucent layer, optional frames by age, and fluent adjusters. */
	abstract static class BossParticle extends SingleQuadParticle {
		protected final SpriteSet sprites;
		private final boolean framesByAge;
		protected float startSize;
		protected float startAlpha = 1f;
		protected boolean glowing;
		/** No wavering or flicker (flashing effects off, or a steady glow). */
		protected boolean steady;

		BossParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites, boolean framesByAge) {
			super(level, x, y, z, dx, dy, dz, sprites.get(level.getRandom()));
			this.sprites = sprites;
			this.framesByAge = framesByAge;
			this.xd = dx;
			this.yd = dy;
			this.zd = dz;
			this.gravity = 0f;
			this.friction = 0.9f;
			this.hasPhysics = false;

			if (framesByAge) {
				setSpriteFromAge(sprites);
			}
		}

		@Override
		public void tick() {
			super.tick();

			if (framesByAge && !this.removed) {
				setSpriteFromAge(sprites);
			}
		}

		@Override
		protected Layer getLayer() {
			return Layer.TRANSLUCENT;
		}

		@Override
		protected int getLightCoords(float partialTick) {
			return glowing ? FULL_BRIGHT : super.getLightCoords(partialTick);
		}

		/** Fraction of life used, 0 to 1. */
		protected float progress() {
			return Mth.clamp((float) this.age / Math.max(1, this.lifetime), 0f, 1f);
		}

		/** Tints it with a 0xRRGGBB colour. */
		BossParticle tint(int rgb) {
			setColor(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f);
			return this;
		}

		BossParticle size(float size) {
			this.startSize = size;
			this.quadSize = size;
			return this;
		}

		BossParticle life(int ticks) {
			this.lifetime = Math.max(1, ticks);
			return this;
		}

		BossParticle alpha(float alpha) {
			this.startAlpha = alpha;
			setAlpha(alpha);
			return this;
		}

		BossParticle gravity(float gravity) {
			this.gravity = gravity;
			return this;
		}

		BossParticle drag(float friction) {
			this.friction = friction;
			return this;
		}

		/** Collides with blocks (clipped by walls and floors). */
		BossParticle solid() {
			this.hasPhysics = true;
			return this;
		}

		BossParticle bright() {
			this.glowing = true;
			return this;
		}

		BossParticle steady() {
			this.steady = true;
			return this;
		}
	}

	/** A heat-haze speck: fades in and out, rises slowly and wavers. */
	private static final class WardHaze extends BossParticle {
		private final float phase;

		WardHaze(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
			super(level, x, y, z, dx, dy, dz, sprites, false);
			this.lifetime = 16 + this.random.nextInt(9);
			this.startSize = 0.04f + this.random.nextFloat() * 0.03f;
			this.quadSize = startSize;
			this.startAlpha = 0.4f;
			this.gravity = -0.004f;
			this.friction = 0.94f;
			this.phase = this.random.nextFloat() * Mth.TWO_PI;
			setColor(1f, 0.96f, 0.88f);
			setAlpha(0f);
		}

		@Override
		public void tick() {
			super.tick();
			float t = progress();
			setAlpha(startAlpha * Mth.sin(t * Mth.PI));
			this.quadSize = startSize * (1f + 0.5f * t);

			if (!steady) {
				this.xd += Mth.sin(this.age * 0.5f + phase) * 0.0015;
				this.zd += Mth.cos(this.age * 0.45f + phase) * 0.0015;
			}
		}
	}

	/** A crystal mote streaming along its path with a slight waver; full bright. */
	private static final class WardCurrent extends BossParticle {
		private final float phase;

		WardCurrent(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
			super(level, x, y, z, dx, dy, dz, sprites, false);
			this.lifetime = 14 + this.random.nextInt(7);
			this.startSize = 0.035f + this.random.nextFloat() * 0.02f;
			this.quadSize = startSize;
			this.startAlpha = 0.85f;
			this.friction = 1f;
			this.glowing = true;
			this.phase = this.random.nextFloat() * Mth.TWO_PI;
			setColor(0.81f, 0.94f, 1f);
			setAlpha(startAlpha);
		}

		@Override
		public void tick() {
			super.tick();
			float t = progress();
			setAlpha(startAlpha * Math.min(1f, (1f - t) * 4f));

			if (!steady) {
				// A thin, wavering current, never a straight beam.
				this.yd += Mth.sin(this.age * 0.7f + phase) * 0.004;
				this.xd += Mth.cos(this.age * 0.55f + phase) * 0.002;
			}
		}
	}

	/** A sandy dust puff: billows outward, slows, swells and thins; collides with blocks. */
	private static final class SurgeDust extends BossParticle {
		SurgeDust(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
			super(level, x, y, z, dx, dy, dz, sprites, true);
			this.lifetime = 16 + this.random.nextInt(9);
			this.startSize = 0.22f + this.random.nextFloat() * 0.18f;
			this.quadSize = startSize;
			this.startAlpha = 0.75f;
			this.friction = 0.88f;
			this.gravity = -0.002f;
			this.hasPhysics = true;
			this.roll = this.random.nextFloat() * Mth.TWO_PI;
			this.oRoll = this.roll;
			float shade = 0.9f + this.random.nextFloat() * 0.1f;
			setColor(shade, shade, shade);
			setAlpha(startAlpha);
		}

		@Override
		public void tick() {
			super.tick();
			float t = progress();
			this.quadSize = startSize * (1f + 0.6f * t);
			setAlpha(startAlpha * (1f - t * t));
		}
	}

	/** Grit and dust kicked up along the seam; arcs and falls back. */
	private static final class LashSeam extends BossParticle {
		LashSeam(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
			super(level, x, y, z, dx, dy, dz, sprites, false);
			this.lifetime = 10 + this.random.nextInt(7);
			this.startSize = 0.06f + this.random.nextFloat() * 0.04f;
			this.quadSize = startSize;
			this.startAlpha = 0.9f;
			this.gravity = 0.35f;
			this.friction = 0.9f;
			this.hasPhysics = true;
			setAlpha(startAlpha);
		}

		@Override
		public void tick() {
			super.tick();
			float t = progress();
			setAlpha(startAlpha * (1f - t * t));
		}
	}

	/** Slow grey smoke: swells, drifts and thins. */
	private static final class EchoSmoke extends BossParticle {
		EchoSmoke(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
			super(level, x, y, z, dx, dy, dz, sprites, true);
			this.lifetime = 30 + this.random.nextInt(17);
			this.startSize = 0.15f + this.random.nextFloat() * 0.13f;
			this.quadSize = startSize;
			this.startAlpha = 0.55f;
			this.friction = 0.92f;
			this.gravity = -0.003f;
			this.roll = this.random.nextFloat() * Mth.TWO_PI;
			this.oRoll = this.roll;
			setColor(0.72f, 0.74f, 0.76f);
			setAlpha(0f);
		}

		@Override
		public void tick() {
			super.tick();
			float t = progress();
			this.quadSize = startSize * (1f + 0.8f * t);
			setAlpha(startAlpha * Math.min(1f, t * 5f) * (1f - t));
		}
	}

	private BossParticleProviders() {
	}
}
