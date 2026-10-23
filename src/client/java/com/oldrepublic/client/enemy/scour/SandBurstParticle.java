package com.oldrepublic.client.enemy.scour;

import com.oldrepublic.enemy.scour.ScourParticles;

import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.Mth;

/**
 * W8's {@code sand_burst} particle (spec MILESTONE_5.md 15.2): a clump of sand thrown up that arcs,
 * falls under gravity and breaks into grains as it goes (four frames by age:
 * {@code textures/particle/sand_burst_<0..3>.png}), in the sand's own colour. It collides with blocks,
 * so it lands on the dune and does not pass through walls. The velocity is used exactly as given.
 */
public final class SandBurstParticle extends SingleQuadParticle {
	private final SpriteSet sprites;
	private final float startSize;

	SandBurstParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
		super(level, x, y, z, dx, dy, dz, sprites.get(level.getRandom()));
		this.sprites = sprites;
		this.xd = dx;
		this.yd = dy;
		this.zd = dz;
		this.gravity = 0.55f;
		this.friction = 0.94f;
		this.hasPhysics = true;
		this.lifetime = 14 + this.random.nextInt(10);
		this.startSize = 0.08f + this.random.nextFloat() * 0.07f;
		this.quadSize = startSize;
		this.roll = this.random.nextFloat() * Mth.TWO_PI;
		this.oRoll = this.roll;
		float shade = 0.88f + this.random.nextFloat() * 0.12f;
		setColor(shade, shade * 0.98f, shade * 0.95f);
		setSpriteFromAge(sprites);
	}

	/** Registers the provider. Called once from {@code FoesM5Client}. */
	public static void register() {
		ParticleProviderRegistry.getInstance().register(ScourParticles.SAND_BURST,
				sprites -> (type, level, x, y, z, dx, dy, dz, random) -> new SandBurstParticle(level, x, y, z, dx, dy, dz, sprites));
	}

	@Override
	public void tick() {
		super.tick();

		if (!this.removed) {
			setSpriteFromAge(sprites);
			float t = Mth.clamp((float) this.age / Math.max(1, this.lifetime), 0f, 1f);
			// The clump breaks up: a little larger and thinner as it falls apart.
			this.quadSize = startSize * (1f + 0.4f * t);
			setAlpha(1f - t * t);

			if (this.onGround) {
				// Landed: it slides a little and settles.
				this.xd *= 0.6;
				this.zd *= 0.6;
			}
		}
	}

	@Override
	protected Layer getLayer() {
		return Layer.TRANSLUCENT;
	}
}
