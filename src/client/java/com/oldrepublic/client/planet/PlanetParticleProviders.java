package com.oldrepublic.client.planet;

import com.oldrepublic.planet.PlanetParticles;

import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.Mth;

/**
 * Client particles of the planets (spec MILESTONE_4.md 13.2). Velocities are used exactly as given,
 * so the effect code controls the motion, as in M3's {@code WorldParticleProviders}.
 * <ul>
 * <li>{@code ash_flake}: a grey flake that drifts down on the wind its velocity gives it, swaying and
 * tumbling, lit by the world (never full-bright); it settles and fades on the ground.</li>
 * <li>{@code aurelite_glint}: a short warm-gold twinkle that grows and shrinks (full-bright: it is the
 * crystal's own light).</li>
 * <li>{@code duskglass_shard}: a black glass sliver with a dull red edge that tumbles slowly and
 * falls; world-lit, so it reads as debris, not light.</li>
 * </ul>
 */
final class PlanetParticleProviders {
	private static final int FULL_BRIGHT = 0xF000F0;

	static void init() {
		ParticleProviderRegistry registry = ParticleProviderRegistry.getInstance();
		registry.register(PlanetParticles.ASH_FLAKE, sprites -> (type, level, x, y, z, dx, dy, dz, random) -> new AshFlake(level, x, y, z, dx, dy, dz, sprites));
		registry.register(PlanetParticles.AURELITE_GLINT, sprites -> (type, level, x, y, z, dx, dy, dz, random) -> new Glint(level, x, y, z, dx, dy, dz, sprites));
		registry.register(PlanetParticles.DUSKGLASS_SHARD, sprites -> (type, level, x, y, z, dx, dy, dz, random) -> new Shard(level, x, y, z, dx, dy, dz, sprites));
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

	/**
	 * An ash flake: carried by the wind it was given (the starting velocity), swaying and tumbling,
	 * falling slowly under light gravity; it rests on the ground and fades.
	 */
	private static final class AshFlake extends Base {
		private static final double MAX_FALL = -0.07;
		private final double windX;
		private final double windZ;
		private final float phase;
		private final float spin;
		private final float peakAlpha;

		AshFlake(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
			super(level, x, y, z, dx, dy, dz, sprites, false);
			this.lifetime = 90 + random.nextInt(60);
			this.quadSize = 0.035f + random.nextFloat() * 0.035f;
			this.gravity = 0.006f;
			this.hasPhysics = true;
			this.windX = dx;
			this.windZ = dz;
			this.phase = random.nextFloat() * Mth.TWO_PI;
			this.spin = (random.nextFloat() - 0.5f) * 0.16f;
			this.peakAlpha = 0.7f + random.nextFloat() * 0.25f;
			this.alpha = 0f;
			this.roll = random.nextFloat() * Mth.TWO_PI;
			this.oRoll = this.roll;
			float shade = random.nextFloat() * 0.18f;
			setColor(0.62f + shade, 0.6f + shade, 0.58f + shade);
		}

		@Override
		public void tick() {
			oRoll = roll;
			super.tick();

			if (onGround) {
				alpha = Math.max(0f, alpha - 0.03f);
				return;
			}

			roll += spin;
			xd = windX + Mth.sin(age * 0.09f + phase) * 0.012;
			zd = windZ + Mth.cos(age * 0.07f + phase) * 0.01;
			yd = Math.max(yd, MAX_FALL);
			float t = progress();
			alpha = peakAlpha * Math.min(1f, t * 8f) * (1f - Math.max(0f, t - 0.85f) / 0.15f);
		}
	}

	/** A warm-gold twinkle on aurelite. */
	private static final class Glint extends Base {
		private final float maxSize;

		Glint(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
			super(level, x, y, z, dx, dy, dz, sprites, true);
			this.lifetime = 10 + random.nextInt(8);
			this.maxSize = 0.07f + random.nextFloat() * 0.07f;
			this.quadSize = 0f;
			this.alpha = 0.9f;
			this.roll = random.nextFloat() * 0.4f;
			this.oRoll = this.roll;
			setColor(1f, 0.84f + random.nextFloat() * 0.08f, 0.5f);
			setSpriteFromAge(sprites);
		}

		@Override
		public void tick() {
			super.tick();
			setSpriteFromAge(sprites);
			quadSize = maxSize * Mth.sin(progress() * Mth.PI);
		}
	}

	/** A duskglass sliver: tumbles slowly, falls with light gravity, rests and fades. */
	private static final class Shard extends Base {
		private final float spin;

		Shard(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
			super(level, x, y, z, dx, dy, dz, sprites, false);
			this.lifetime = 30 + random.nextInt(30);
			this.quadSize = 0.03f + random.nextFloat() * 0.03f;
			this.gravity = 0.08f;
			this.friction = 0.94f;
			this.hasPhysics = true;
			this.alpha = 1f;
			this.spin = (random.nextFloat() - 0.5f) * 0.3f;
			this.roll = random.nextFloat() * Mth.TWO_PI;
			this.oRoll = this.roll;
		}

		@Override
		public void tick() {
			oRoll = roll;
			super.tick();

			if (!onGround) {
				roll += spin;
			}

			alpha = 1f - Math.max(0f, progress() - 0.7f) / 0.3f;
		}
	}

	private PlanetParticleProviders() {
	}
}
