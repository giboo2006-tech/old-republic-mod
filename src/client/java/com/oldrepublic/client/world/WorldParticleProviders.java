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
 * <li>{@code light_mote}: warm sunlit mote drifting slowly and swaying (ruin light shafts, lumen moss, pedestals).</li>
 * <li>{@code leaf_fall}: a small leaf tumbling down, swaying and turning, settling on the ground.</li>
 * <li>{@code tomb_dust}: grey-brown dust sifting down and spreading.</li>
 * <li>{@code veil_dust}: fine pale dust seeping from mortar lines and falling slowly.</li>
 * <li>{@code vent_grit}: heavy chips and pebbles with gravity and bounce.</li>
 * <li>{@code miasma}: a dull grey-green puff that swells, lingers and thins.</li>
 * <li>{@code conduit_spark}: a short orange-white spark with gravity that cools as it falls.</li>
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
		registry.register(WorldParticles.LIGHT_MOTE, sprites -> (type, level, x, y, z, dx, dy, dz, random) -> new LightMote(level, x, y, z, dx, dy, dz, sprites));
		registry.register(WorldParticles.LEAF_FALL, sprites -> (type, level, x, y, z, dx, dy, dz, random) -> new Leaf(level, x, y, z, dx, dy, dz, sprites));
		registry.register(WorldParticles.TOMB_DUST, sprites -> (type, level, x, y, z, dx, dy, dz, random) -> new Dust(level, x, y, z, dx, dy, dz, sprites, false));
		registry.register(WorldParticles.VEIL_DUST, sprites -> (type, level, x, y, z, dx, dy, dz, random) -> new Dust(level, x, y, z, dx, dy, dz, sprites, true));
		registry.register(WorldParticles.VENT_GRIT, sprites -> (type, level, x, y, z, dx, dy, dz, random) -> new Grit(level, x, y, z, dx, dy, dz, sprites));
		registry.register(WorldParticles.MIASMA, sprites -> (type, level, x, y, z, dx, dy, dz, random) -> new Miasma(level, x, y, z, dx, dy, dz, sprites));
		registry.register(WorldParticles.CONDUIT_SPARK, sprites -> (type, level, x, y, z, dx, dy, dz, random) -> new ConduitSpark(level, x, y, z, dx, dy, dz, sprites));
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

	/** Warm, sunlit mote: slow drift, a gentle sway, a fade in and out. */
	private static final class LightMote extends Base {
		private final float peakAlpha;
		private final float phase;

		LightMote(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
			super(level, x, y, z, dx, dy, dz, sprites, true);
			this.lifetime = 60 + random.nextInt(50);
			this.quadSize = 0.025f + random.nextFloat() * 0.03f;
			this.peakAlpha = 0.45f + random.nextFloat() * 0.3f;
			this.phase = random.nextFloat() * Mth.TWO_PI;
			this.alpha = 0f;
			float warm = random.nextFloat() * 0.08f;
			setColor(1.0f, 0.9f + warm, 0.66f + warm);
		}

		@Override
		public void tick() {
			super.tick();
			float t = progress();
			alpha = peakAlpha * Mth.sin(t * Mth.PI) * (0.85f + 0.15f * Mth.sin(age * 0.3f + phase));
			xd = xd * 0.98 + Mth.sin(age * 0.07f + phase) * 0.0008;
			zd = zd * 0.98 + Mth.cos(age * 0.09f + phase) * 0.0008;
		}
	}

	/** A leaf: falls slowly, swings from side to side and turns; it rests on the ground and fades. */
	private static final class Leaf extends Base {
		private final float phase;
		private final float spin;

		Leaf(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
			super(level, x, y, z, dx, dy, dz, sprites, false);
			this.lifetime = 90 + random.nextInt(50);
			this.quadSize = 0.07f + random.nextFloat() * 0.05f;
			this.gravity = 0.015f;
			this.friction = 0.96f;
			this.hasPhysics = true;
			this.alpha = 1f;
			this.phase = random.nextFloat() * Mth.TWO_PI;
			this.spin = (random.nextFloat() - 0.5f) * 0.12f;
			this.roll = random.nextFloat() * Mth.TWO_PI;
			this.oRoll = this.roll;
			float dry = random.nextFloat();
			setColor(Mth.lerp(dry, 0.55f, 0.72f), Mth.lerp(dry, 0.62f, 0.5f), Mth.lerp(dry, 0.3f, 0.26f));
		}

		@Override
		public void tick() {
			oRoll = roll;
			super.tick();

			if (onGround) {
				alpha = Math.max(0f, alpha - 0.04f);
				return;
			}

			roll += spin;
			xd += Mth.sin(age * 0.15f + phase) * 0.0025;
			zd += Mth.cos(age * 0.12f + phase) * 0.0015;
			alpha = 1f - Math.max(0f, progress() - 0.85f) / 0.15f;
		}
	}

	/** Dust: tomb dust is grey-brown and spreads as it sifts; veil dust is fine, pale and slow. */
	private static final class Dust extends Base {
		private final float startSize;
		private final float peakAlpha;
		private final boolean fine;

		Dust(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites, boolean fine) {
			super(level, x, y, z, dx, dy, dz, sprites, false);
			this.fine = fine;
			this.lifetime = fine ? 30 + random.nextInt(25) : 40 + random.nextInt(35);
			this.startSize = fine ? 0.018f + random.nextFloat() * 0.02f : 0.05f + random.nextFloat() * 0.06f;
			this.quadSize = startSize;
			this.peakAlpha = fine ? 0.75f : 0.5f + random.nextFloat() * 0.2f;
			this.gravity = fine ? 0.01f : 0.004f;
			this.friction = 0.95f;
			this.alpha = 0f;
			float shade = random.nextFloat() * 0.08f;

			if (fine) {
				setColor(0.86f + shade, 0.83f + shade, 0.75f + shade);
			} else {
				setColor(0.52f + shade, 0.48f + shade, 0.43f + shade);
			}
		}

		@Override
		public void tick() {
			super.tick();
			float t = progress();
			quadSize = startSize * (fine ? 1f : 1f + 0.8f * t);
			alpha = peakAlpha * Math.min(1f, t * 6f) * (1f - t);
		}
	}

	/** Grit: small stone chips that fall, bounce and come to rest. */
	private static final class Grit extends Base {
		Grit(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
			super(level, x, y, z, dx, dy, dz, sprites, false);
			this.lifetime = 20 + random.nextInt(25);
			this.quadSize = 0.025f + random.nextFloat() * 0.03f;
			this.gravity = 0.8f;
			this.friction = 0.92f;
			this.hasPhysics = true;
			this.alpha = 1f;
			float shade = random.nextFloat() * 0.12f;
			setColor(0.42f + shade, 0.4f + shade, 0.36f + shade);
		}

		@Override
		public void tick() {
			super.tick();
			alpha = 1f - Math.max(0f, progress() - 0.75f) / 0.25f;
		}
	}

	/** Miasma: a dull grey-green puff that swells, lingers about 30 ticks and thins out. */
	private static final class Miasma extends Base {
		private final float startSize;

		Miasma(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
			super(level, x, y, z, dx, dy, dz, sprites, false);
			this.lifetime = 30 + random.nextInt(12);
			this.startSize = 0.18f + random.nextFloat() * 0.14f;
			this.quadSize = startSize;
			this.friction = 0.88f;
			this.alpha = 0f;
			this.roll = random.nextFloat() * Mth.TWO_PI;
			this.oRoll = this.roll;
			float shade = random.nextFloat() * 0.06f;
			setColor(0.44f + shade, 0.5f + shade, 0.37f + shade);
			setSpriteFromAge(sprites);
		}

		@Override
		public void tick() {
			super.tick();
			setSpriteFromAge(sprites);
			float t = progress();
			quadSize = startSize * (1f + 1.2f * t);
			alpha = 0.62f * Math.min(1f, t * 8f) * (1f - t * t);
		}
	}

	/** A conduit spark: white-hot, falls and bounces, cooling to orange. */
	private static final class ConduitSpark extends Base {
		ConduitSpark(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
			super(level, x, y, z, dx, dy, dz, sprites, true);
			this.lifetime = 6 + random.nextInt(8);
			this.quadSize = 0.02f + random.nextFloat() * 0.018f;
			this.gravity = 0.7f;
			this.friction = 0.9f;
			this.hasPhysics = true;
			this.alpha = 1f;
			setColor(1f, 0.95f, 0.8f);
		}

		@Override
		public void tick() {
			super.tick();
			float t = progress();
			setColor(1f, Mth.lerp(t, 0.95f, 0.5f), Mth.lerp(t, 0.8f, 0.2f));
			alpha = 1f - t * t;
		}
	}

	private WorldParticleProviders() {
	}
}
