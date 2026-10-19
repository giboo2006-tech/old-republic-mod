package com.oldrepublic.client.saber;

import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.saber.SaberParticles;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * The saber spark and contact flash particles for other client features (the Engineering Bench's
 * welding arm and the stun grenade): the same tinted, full-bright sparks the saber clash uses.
 * Callers check {@link Vfx#inRange} and scale counts with {@link Vfx#count} themselves.
 */
public final class SaberSparks {
	private static final RandomSource RANDOM = RandomSource.create();

	/** {@code count} sparks in a cone of {@code spread} around {@code dir}, cooling toward {@code color}. */
	public static void sparks(Vec3 pos, Vec3 dir, double spread, int count, int color, double speed) {
		Minecraft mc = Minecraft.getInstance();
		Vec3 axis = dir.lengthSqr() < 1.0E-4 ? new Vec3(0, 1, 0) : dir.normalize();

		for (int i = 0; i < count; i++) {
			Vec3 r = new Vec3(RANDOM.nextDouble() * 2 - 1, RANDOM.nextDouble() * 2 - 1, RANDOM.nextDouble() * 2 - 1);
			Vec3 v = axis.add(r.scale(spread)).normalize().scale(speed * (0.6 + RANDOM.nextDouble() * 0.8));
			Particle particle = mc.particleEngine.createParticle(SaberParticles.SABER_SPARK, pos.x, pos.y, pos.z, v.x, v.y, v.z);

			if (particle instanceof SaberParticleProviders.SaberParticle spark) {
				spark.tint(color);
			}
		}
	}

	/** A one-tick flash of {@code color}, or a soft steady glow when flashing effects are off. */
	public static void flash(Vec3 pos, int color) {
		Particle particle = Minecraft.getInstance().particleEngine.createParticle(SaberParticles.CONTACT_FLASH, pos.x, pos.y, pos.z, 0, 0, 0);

		if (particle instanceof SaberParticleProviders.Flash flash) {
			flash.tint(color);

			if (!Vfx.flashing()) {
				flash.soft();
			}
		}
	}

	/**
	 * A soft glow of {@code color} that fades over a few ticks and never flashes; spawned every few
	 * ticks it reads as steady light (a Legendary craft's glow, MILESTONE_5.md 15.3).
	 */
	public static void glow(Vec3 pos, int color) {
		Particle particle = Minecraft.getInstance().particleEngine.createParticle(SaberParticles.CONTACT_FLASH, pos.x, pos.y, pos.z, 0, 0, 0);

		if (particle instanceof SaberParticleProviders.Flash flash) {
			flash.tint(color);
			flash.soft();
		}
	}

	private SaberSparks() {
	}
}
