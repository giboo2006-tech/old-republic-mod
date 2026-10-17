package com.oldrepublic.client.boss.sentinel;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.enemy.construct.SentinelParticles;

import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.phys.Vec3;

/**
 * The {@code lumen_spark} particle (spec 13.2): a full-bright speck of a sentinel's lumen, painted
 * white and tinted by {@link SentinelFx} (pale blue for a Watcher, red for a Tomb sentinel,
 * amber-white at the lance muzzle). It starts hot white and cools to its tint as it fades. With
 * {@link Spark#steady() steady} it holds its brightness instead of flickering (flashing effects off).
 */
final class LumenSparkProvider {
	static void init() {
		ParticleProviderRegistry.getInstance().register(SentinelParticles.LUMEN_SPARK, sprites -> (type, level, x, y, z, dx, dy, dz, random) ->
				new Spark(level, x, y, z, dx, dy, dz, sprites.get(random)));
	}

	/**
	 * Creates a spark through the particle engine (so it counts toward the vanilla particle limit)
	 * and tints it; null if the engine refused it.
	 */
	static @Nullable Spark spawn(Vec3 pos, Vec3 velocity, int rgb) {
		Particle particle = Minecraft.getInstance().particleEngine.createParticle(SentinelParticles.LUMEN_SPARK,
				pos.x, pos.y, pos.z, velocity.x, velocity.y, velocity.z);

		if (particle instanceof Spark spark) {
			spark.tint(rgb);
			return spark;
		}

		return null;
	}

	/** One lumen speck: full bright, translucent, cooling from white to its tint. */
	static final class Spark extends SingleQuadParticle {
		/** Packed block and sky light 15. */
		private static final int FULL_BRIGHT = 0xF000F0;
		private int rgb = 0xFFFFFF;
		private float startSize;
		private float startAlpha = 1f;
		private boolean steady;
		private boolean flicker;

		Spark(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, TextureAtlasSprite sprite) {
			super(level, x, y, z, dx, dy, dz, sprite);
			this.xd = dx;
			this.yd = dy;
			this.zd = dz;
			this.lifetime = 10 + this.random.nextInt(6);
			this.gravity = 0f;
			this.friction = 0.9f;
			this.hasPhysics = false;
			this.startSize = 0.04f + this.random.nextFloat() * 0.03f;
			this.quadSize = startSize;
			blend(0f);
		}

		/** Sets the colour the spark cools toward. */
		Spark tint(int color) {
			this.rgb = color & 0xFFFFFF;
			blend(0.2f);
			return this;
		}

		/** Lifetime in ticks. */
		Spark life(int ticks) {
			this.lifetime = Math.max(1, ticks);
			return this;
		}

		/** Starting size of the quad. */
		Spark size(float size) {
			this.startSize = size;
			this.quadSize = size;
			return this;
		}

		/** Starting opacity. */
		Spark alpha(float alpha) {
			this.startAlpha = alpha;
			setAlpha(alpha);
			return this;
		}

		/** Falls a little, like a spark shed from guttering stone. */
		Spark falling() {
			this.gravity = 0.25f;
			this.hasPhysics = true;
			return this;
		}

		/** Sputters while it fades (only used with flashing effects on). */
		Spark flicker() {
			this.flicker = true;
			return this;
		}

		/** Holds a steady glow: no flicker, a slow even fade (flashing effects off, the exposed core). */
		Spark steady() {
			this.steady = true;
			this.flicker = false;
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

		@Override
		public void tick() {
			super.tick();
			float f = Math.min(1f, (float) this.age / Math.max(1, this.lifetime));
			blend(Math.min(1f, 0.2f + f * 1.4f));

			if (steady) {
				this.quadSize = startSize;
				setAlpha(startAlpha * (1f - f));
				return;
			}

			this.quadSize = startSize * (1f - 0.5f * f);
			float fade = startAlpha * (1f - f * f);
			setAlpha(flicker && this.random.nextInt(3) == 0 ? fade * 0.35f : fade);
		}

		/** Mixes from white ({@code 0}) to the tint ({@code 1}). */
		private void blend(float towardTint) {
			float r = ((rgb >> 16) & 0xFF) / 255f;
			float g = ((rgb >> 8) & 0xFF) / 255f;
			float b = (rgb & 0xFF) / 255f;
			setColor(1f + (r - 1f) * towardTint, 1f + (g - 1f) * towardTint, 1f + (b - 1f) * towardTint);
		}
	}

	private LumenSparkProvider() {
	}
}
