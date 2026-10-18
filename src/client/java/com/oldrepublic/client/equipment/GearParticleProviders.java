package com.oldrepublic.client.equipment;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.equipment.EquipmentParticles;

import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;

/**
 * The gear's particles ({@link EquipmentParticles}, spec MILESTONE_4.md 13.2): {@code steam_vent}
 * and {@code flame_jet}. Both play their sprite frames over their life (a puff swelling and thinning;
 * a flame tongue shrinking into smoke). Effects that need a tint, size or life of their own spawn
 * through {@link #spawn} and adjust the returned particle. W6's hunter flamer and jet hop spawn
 * {@code flame_jet} by id and get the defaults.
 */
public final class GearParticleProviders {
	static void init() {
		ParticleProviderRegistry registry = ParticleProviderRegistry.getInstance();
		registry.register(EquipmentParticles.STEAM_VENT, sprites -> (type, level, x, y, z, dx, dy, dz, random) ->
				new SteamVent(level, x, y, z, dx, dy, dz, sprites));
		registry.register(EquipmentParticles.FLAME_JET, sprites -> (type, level, x, y, z, dx, dy, dz, random) ->
				new FlameJet(level, x, y, z, dx, dy, dz, sprites));
	}

	/**
	 * Spawns one gear particle at {@code pos} with {@code velocity} through the particle engine, or
	 * returns null when the engine refused it (minimal particles, the cap).
	 */
	public static @Nullable GearParticle spawn(SimpleParticleType type, Vec3 pos, Vec3 velocity) {
		Particle particle = Minecraft.getInstance().particleEngine.createParticle(type, pos.x, pos.y, pos.z, velocity.x, velocity.y, velocity.z);
		return particle instanceof GearParticle gear ? gear : null;
	}

	/** Shared base: exact velocity, translucent, frames by age, and fluent adjusters. */
	public abstract static class GearParticle extends SingleQuadParticle {
		protected final SpriteSet sprites;
		protected float startSize;
		protected float startAlpha = 1f;
		protected float growth;

		GearParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
			super(level, x, y, z, dx, dy, dz, sprites.get(level.getRandom()));
			this.sprites = sprites;
			this.xd = dx;
			this.yd = dy;
			this.zd = dz;
			this.hasPhysics = false;
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
		public void tick() {
			super.tick();

			if (!this.removed) {
				setSpriteFromAge(sprites);
			}
		}

		/** Tints it with a 0xRRGGBB colour. */
		public GearParticle tint(int rgb) {
			setColor(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f);
			return this;
		}

		public GearParticle size(float size) {
			this.startSize = size;
			this.quadSize = size;
			return this;
		}

		public GearParticle life(int ticks) {
			this.lifetime = Math.max(1, ticks);
			return this;
		}

		public GearParticle alpha(float alpha) {
			this.startAlpha = alpha;
			setAlpha(alpha);
			return this;
		}

		/** How much it swells over its life (1 doubles its size by the end). */
		public GearParticle growth(float growth) {
			this.growth = growth;
			return this;
		}
	}

	/** A soft white steam puff: out of the vent fast, then slowing, rising, swelling and thinning. */
	private static final class SteamVent extends GearParticle {
		SteamVent(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
			super(level, x, y, z, dx, dy, dz, sprites);
			this.lifetime = 14 + this.random.nextInt(10);
			this.gravity = -0.025f;
			this.friction = 0.8f;
			this.startSize = 0.09f + this.random.nextFloat() * 0.06f;
			this.quadSize = startSize;
			this.growth = 2.4f;
			this.startAlpha = 0.65f;
			this.roll = this.random.nextFloat() * 6.28f;
			this.oRoll = this.roll;
			setColor(0.94f, 0.96f, 0.98f);
			setAlpha(startAlpha);
		}

		@Override
		public void tick() {
			super.tick();
			float f = lifeFraction();
			this.quadSize = startSize * (1f + growth * f);
			setAlpha(startAlpha * (1f - f) * Math.min(1f, 0.4f + f * 6f));
		}
	}

	/** A tongue of flame: bright and full-lit at first, shrinking as it cools into a darker smoke wisp that slows and lifts. */
	private static final class FlameJet extends GearParticle {
		FlameJet(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
			super(level, x, y, z, dx, dy, dz, sprites);
			this.lifetime = 8 + this.random.nextInt(6);
			this.gravity = -0.01f;
			this.friction = 0.78f;
			this.startSize = 0.16f + this.random.nextFloat() * 0.08f;
			this.quadSize = startSize;
			this.growth = 0.6f;
			this.startAlpha = 0.95f;
			setColor(1f, 0.82f, 0.5f);
			setAlpha(startAlpha);
		}

		@Override
		public void tick() {
			super.tick();
			float f = lifeFraction();
			// Hot white-orange cooling to sooty grey.
			float heat = Math.max(0f, 1f - f * 1.6f);
			setColor(0.32f + 0.68f * heat, 0.3f + 0.52f * heat * heat, 0.28f + 0.22f * heat * heat * heat);
			this.quadSize = startSize * (1f + growth * f);
			setAlpha(startAlpha * (1f - f * f));
		}

		@Override
		public int getLightCoords(float partialTick) {
			return lifeFraction() < 0.45f ? LightCoordsUtil.FULL_BRIGHT : super.getLightCoords(partialTick);
		}
	}

	private GearParticleProviders() {
	}
}
