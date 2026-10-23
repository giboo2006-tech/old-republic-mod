package com.oldrepublic.client.space;

import com.oldrepublic.space.SpaceParticles;

import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;

/**
 * Space's own particle (spec MILESTONE_5.md 15.2): {@code hull_debris}, a jagged fragment of plating
 * thrown off a struck or destroyed ship. There is no air and no ground in an orbit: it keeps its
 * velocity (a slight drag only so a burst stays readable), never falls, tumbles as it goes, glows
 * hot for its first moments and burns out to dark metal before it fades. Velocities are used
 * exactly as given.
 */
final class SpaceParticleProviders {
	static void init() {
		ParticleProviderRegistry.getInstance().register(SpaceParticles.HULL_DEBRIS,
				sprites -> (type, level, x, y, z, dx, dy, dz, random) -> new HullDebris(level, x, y, z, dx, dy, dz, sprites));
	}

	/** A tumbling plate fragment: hot orange at first, cooling to dark durasteel, then fading. */
	private static final class HullDebris extends SingleQuadParticle {
		/** Share of its life spent glowing hot. */
		private static final float HOT = 0.25f;
		private final float spin;
		private final float shade;

		HullDebris(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
			super(level, x, y, z, dx, dy, dz, sprites.get(level.getRandom()));
			this.xd = dx;
			this.yd = dy;
			this.zd = dz;
			this.gravity = 0f;
			this.friction = 0.97f;
			this.hasPhysics = true;
			this.lifetime = 30 + random.nextInt(24);
			this.quadSize = 0.06f + random.nextFloat() * 0.1f;
			this.spin = (random.nextFloat() - 0.5f) * 0.6f;
			this.roll = random.nextFloat() * Mth.TWO_PI;
			this.oRoll = this.roll;
			this.shade = random.nextFloat() * 0.1f;
			this.alpha = 1f;
			colour();
		}

		@Override
		public void tick() {
			super.tick();
			oRoll = roll;
			roll += spin;
			colour();
			float t = progress();
			alpha = t < 0.8f ? 1f : 1f - (t - 0.8f) / 0.2f;
		}

		/** Hot orange cooling to the plating's grey. */
		private void colour() {
			float heat = 1f - Mth.clamp(progress() / HOT, 0f, 1f);
			float grey = 0.36f + shade;
			setColor(Mth.lerp(heat, grey, 1f), Mth.lerp(heat, grey, 0.55f), Mth.lerp(heat, grey + 0.02f, 0.2f));
		}

		private float progress() {
			return lifetime <= 0 ? 1f : Mth.clamp((float) age / lifetime, 0f, 1f);
		}

		@Override
		protected Layer getLayer() {
			return Layer.TRANSLUCENT;
		}

		@Override
		protected int getLightCoords(float partialTick) {
			// Hot fragments are their own light; cooled ones take the light around them.
			return progress() < HOT ? LightCoordsUtil.FULL_BRIGHT : super.getLightCoords(partialTick);
		}
	}

	private SpaceParticleProviders() {
	}
}
