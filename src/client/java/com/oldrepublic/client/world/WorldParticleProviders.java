package com.oldrepublic.client.world;

import com.oldrepublic.world.WorldParticles;

import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.Mth;

/**
 * Client particles of the world feature. Velocities are used exactly as given (no vanilla
 * jitter), so effect code fully controls motion.
 * <ul>
 * <li>{@code crystal_mote}: pale, full-bright mote drifting slowly up and swaying.</li>
 * <li>{@code crystal_glint}: short star twinkle that grows and shrinks.</li>
 * <li>{@code crystal_spark}: bright violet-white spark with gravity and bounce.</li>
 * <li>{@code shadow_wisp}: deep violet, full-bright smoky wisp that swells and fades.</li>
 * </ul>
 */
final class WorldParticleProviders {
	private static final int FULL_BRIGHT = 0xF000F0;

	static void init() {
		ParticleProviderRegistry registry = ParticleProviderRegistry.getInstance();
		registry.register(WorldParticles.CRYSTAL_MOTE, sprites -> (type, level, x, y, z, dx, dy, dz, random) -> new Mote(level, x, y, z, dx, dy, dz, sprites));
		registry.register(WorldParticles.CRYSTAL_GLINT, sprites -> (type, level, x, y, z, dx, dy, dz, random) -> new Glint(level, x, y, z, dx, dy, dz, sprites));
		registry.register(WorldParticles.CRYSTAL_SPARK, sprites -> (type, level, x, y, z, dx, dy, dz, random) -> new Spark(level, x, y, z, dx, dy, dz, sprites));
		registry.register(WorldParticles.SHADOW_WISP, sprites -> (type, level, x, y, z, dx, dy, dz, random) -> new Wisp(level, x, y, z, dx, dy, dz, sprites));
	}

	private abstract static class Base extends SingleQuadParticle {
		protected final SpriteSet sprites;
		private final boolean fullBright;

		Base(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites, boolean fullBright) {
			super(level, x, y, z, dx, dy, dz, sprites.get(level.getRandom()));
			this.sprites = sprites;
			this.fullBright = fullBright;
			this.xd = dx;
			this.yd = dy;
			this.zd = dz;
			this.gravity = 0f;
			this.friction = 1f;
			this.hasPhysics = false;
		}

		@Override
		protected Layer getLayer() {
			return Layer.TRANSLUCENT;
		}

		@Override
		protected int getLightCoords(float partialTick) {
			return fullBright ? FULL_BRIGHT : super.getLightCoords(partialTick);
		}

		protected float progress() {
			return lifetime <= 0 ? 1f : Mth.clamp((float) age / lifetime, 0f, 1f);
		}
	}

	private static final class Mote extends Base {
		private final float peakAlpha;
		private final float phase;

		Mote(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
			super(level, x, y, z, dx, dy, dz, sprites, true);
			this.lifetime = 50 + random.nextInt(30);
			this.quadSize = 0.035f + random.nextFloat() * 0.035f;
			this.peakAlpha = 0.5f + random.nextFloat() * 0.25f;
			this.phase = random.nextFloat() * Mth.TWO_PI;
			this.alpha = 0f;
			float tint = random.nextFloat() * 0.15f;
			setColor(0.72f + tint, 0.9f + tint * 0.5f, 1.0f);
		}

		@Override
		public void tick() {
			super.tick();
			float t = progress();
			alpha = peakAlpha * Mth.sin(t * Mth.PI) * (0.8f + 0.2f * Mth.sin(age * 0.4f + phase));
			xd = xd * 0.97 + Mth.sin(age * 0.11f + phase) * 0.0012;
			zd = zd * 0.97 + Mth.cos(age * 0.13f + phase) * 0.0012;
		}
	}

	private static final class Glint extends Base {
		private final float maxSize;

		Glint(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
			super(level, x, y, z, dx, dy, dz, sprites, true);
			this.lifetime = 10 + random.nextInt(8);
			this.maxSize = 0.09f + random.nextFloat() * 0.08f;
			this.quadSize = 0f;
			this.alpha = 0.95f;
			this.roll = random.nextFloat() * 0.4f;
			this.oRoll = this.roll;
			setColor(0.9f, 0.97f, 1.0f);
			setSpriteFromAge(sprites);
		}

		@Override
		public void tick() {
			super.tick();
			setSpriteFromAge(sprites);
			quadSize = maxSize * Mth.sin(progress() * Mth.PI);
		}
	}

	private static final class Spark extends Base {
		Spark(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
			super(level, x, y, z, dx, dy, dz, sprites, true);
			this.lifetime = 8 + random.nextInt(10);
			this.quadSize = 0.03f + random.nextFloat() * 0.03f;
			this.gravity = 0.6f;
			this.friction = 0.9f;
			this.hasPhysics = true;
			this.alpha = 1f;
			setColor(0.95f, 0.8f + random.nextFloat() * 0.2f, 1.0f);
		}

		@Override
		public void tick() {
			super.tick();
			float t = progress();
			// Cools from white-violet towards magenta as it dies.
			setColor(0.95f, Mth.lerp(t, 0.9f, 0.45f), 1.0f);
			alpha = 1f - t * t;
		}
	}

	private static final class Wisp extends Base {
		private final float startSize;

		Wisp(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
			// Full-bright deep violet: a dark-on-dark wisp would vanish in an unlit cave.
			super(level, x, y, z, dx, dy, dz, sprites, true);
			this.lifetime = 24 + random.nextInt(16);
			this.startSize = 0.12f + random.nextFloat() * 0.1f;
			this.quadSize = startSize;
			this.alpha = 0f;
			this.friction = 0.94f;
			float shade = random.nextFloat() * 0.08f;
			setColor(0.36f + shade, 0.24f + shade, 0.55f + shade);
			setSpriteFromAge(sprites);
		}

		@Override
		public void tick() {
			super.tick();
			setSpriteFromAge(sprites);
			float t = progress();
			quadSize = startSize * (1f + 1.4f * t);
			alpha = 0.75f * Math.min(1f, t * 5f) * (1f - t);
		}
	}

	private WorldParticleProviders() {
	}
}
