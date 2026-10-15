package com.oldrepublic.client.quest;

import com.oldrepublic.quest.QuestParticles;

import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.Mth;

/**
 * Client particles of the quest feature: {@code force_mote} (meditation) and
 * {@code resonance_shimmer} (the Calling). Both are full-bright, translucent and soft; neither
 * forms shapes on the ground.
 */
public final class QuestParticleProviders {
	private static final int FULL_BRIGHT = 0xF000F0;

	static void init() {
		ParticleProviderRegistry.getInstance().register(QuestParticles.FORCE_MOTE,
				sprites -> (type, level, x, y, z, dx, dy, dz, random) -> new Mote(level, x, y, z, dx, dy, dz, sprites));
		ParticleProviderRegistry.getInstance().register(QuestParticles.RESONANCE_SHIMMER,
				sprites -> (type, level, x, y, z, dx, dy, dz, random) -> new Shimmer(level, x, y, z, dx, dy, dz, sprites));
	}

	/** Base for both: exact velocity (no vanilla jitter), no gravity or collision, soft fade in and out. */
	private abstract static class Soft extends SingleQuadParticle {
		private final float peakAlpha;

		Soft(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites, float peakAlpha) {
			super(level, x, y, z, dx, dy, dz, sprites.get(level.getRandom()));
			this.xd = dx;
			this.yd = dy;
			this.zd = dz;
			this.gravity = 0f;
			this.friction = 1f;
			this.hasPhysics = false;
			this.peakAlpha = peakAlpha;
			this.alpha = 0f;
		}

		@Override
		public void tick() {
			super.tick();
			float t = lifetime <= 0 ? 1f : (float) age / lifetime;
			alpha = peakAlpha * Mth.sin(t * Mth.PI);
		}

		@Override
		protected Layer getLayer() {
			return Layer.TRANSLUCENT;
		}

		@Override
		protected int getLightCoords(float partialTick) {
			return FULL_BRIGHT;
		}
	}

	/** Pale mote that drifts inward and loosely orbits (velocity slowly turns around the vertical axis). */
	private static final class Mote extends Soft {
		private final float swirl;

		Mote(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
			super(level, x, y, z, dx, dy, dz, sprites, 0.55f);
			this.lifetime = 34 + random.nextInt(14);
			this.quadSize = 0.035f + random.nextFloat() * 0.03f;
			this.swirl = (random.nextBoolean() ? 1f : -1f) * (0.03f + random.nextFloat() * 0.03f);
			setColor(0.86f, 0.92f, 1.0f);
		}

		@Override
		public void tick() {
			super.tick();
			double cos = Math.cos(swirl);
			double sin = Math.sin(swirl);
			double nx = xd * cos - zd * sin;
			double nz = xd * sin + zd * cos;
			xd = nx * 0.985;
			zd = nz * 0.985;
			yd *= 0.97;
		}
	}

	/** Faint crystal glint that drifts slowly along its velocity and twinkles. */
	private static final class Shimmer extends Soft {
		Shimmer(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
			super(level, x, y, z, dx, dy, dz, sprites, 0.45f);
			this.lifetime = 40 + random.nextInt(20);
			this.quadSize = 0.05f + random.nextFloat() * 0.04f;
			setColor(0.72f + random.nextFloat() * 0.1f, 0.9f, 1.0f);
		}

		@Override
		public void tick() {
			super.tick();
			// A slow twinkle on top of the fade.
			alpha *= 0.75f + 0.25f * Mth.sin(age * 0.6f);
		}
	}

	private QuestParticleProviders() {
	}
}
