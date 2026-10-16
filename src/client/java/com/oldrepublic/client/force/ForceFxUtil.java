package com.oldrepublic.client.force;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.config.ClientConfig;
import com.oldrepublic.client.force.particle.ForceParticle;
import com.oldrepublic.force.ForceParticles;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.Vec3;

/**
 * Helpers shared by the Force power visuals: hand and body positions, ground lookup and dust
 * colour, particle spawning with the fluent {@link ForceParticle} setters, and local sounds at the
 * mod volume.
 */
final class ForceFxUtil {
	static final float DEFAULT_DUST_R = 0.62f;
	static final float DEFAULT_DUST_G = 0.57f;
	static final float DEFAULT_DUST_B = 0.49f;
	static final RandomSource RANDOM = RandomSource.create();

	/** Approximate world position of a hand; {@code extend} 0 = drawn back, 1 = thrust out. */
	static Vec3 handPos(LivingEntity entity, boolean leftArm, float extend) {
		double yaw = Math.toRadians(entity.yBodyRot);
		Vec3 forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
		Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
		Vec3 shoulder = entity.position().add(0, entity.getEyeHeight() * 0.8, 0).add(right.scale(leftArm ? -0.38 : 0.38));
		Vec3 look = entity.getViewVector(1f);
		return shoulder.add(look.scale(0.2 + 0.5 * extend)).add(forward.scale(-0.15 * (1 - extend)));
	}

	/** The free hand: the main arm when the main hand is empty, otherwise the off arm. */
	static boolean freeArmIsLeft(LivingEntity entity) {
		HumanoidArm main = entity.getMainArm();
		HumanoidArm arm = entity.getMainHandItem().isEmpty() ? main : main.getOpposite();
		return arm == HumanoidArm.LEFT;
	}

	/** The middle of the body. */
	static Vec3 chest(Entity entity) {
		return entity.position().add(0, entity.getBbHeight() * 0.6, 0);
	}

	static Vec3 horizontal(Vec3 dir) {
		Vec3 flat = new Vec3(dir.x, 0, dir.z);
		return flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
	}

	/** First solid block at or up to 3 below {@code pos}, or null. */
	static @Nullable BlockPos groundBelow(ClientLevel level, Vec3 pos) {
		BlockPos cursor = BlockPos.containing(pos);

		for (int i = 0; i < 4; i++) {
			BlockState state = level.getBlockState(cursor);

			if (!state.isAir() && state.isSolid()) {
				return cursor;
			}

			cursor = cursor.below();
		}

		return null;
	}

	/** Airborne dust colour of a block: lighter and less saturated than its map colour. */
	static float[] dustColor(ClientLevel level, BlockPos pos) {
		MapColor color = level.getBlockState(pos).getMapColor(level, pos);

		if (color == MapColor.NONE || color.col == 0) {
			return new float[] {DEFAULT_DUST_R, DEFAULT_DUST_G, DEFAULT_DUST_B};
		}

		float r = (color.col >> 16 & 255) / 255f;
		float g = (color.col >> 8 & 255) / 255f;
		float b = (color.col & 255) / 255f;
		return new float[] {Mth.lerp(0.35f, r, 0.8f), Mth.lerp(0.35f, g, 0.77f), Mth.lerp(0.35f, b, 0.7f)};
	}

	/** One bent-air ripple. */
	static void ripple(Vec3 pos, Vec3 velocity, float startSize, float endSize, float alpha, int life) {
		ForceParticle p = spawn(ForceParticles.AIR_RIPPLE, pos, velocity);

		if (p != null) {
			p.size(startSize, endSize).alpha(alpha).life(life);
		}
	}

	/** Spawns one of the force feature's particles, or returns null if the engine refused it. */
	static @Nullable ForceParticle spawn(SimpleParticleType type, Vec3 pos, Vec3 velocity) {
		Particle particle = Minecraft.getInstance().particleEngine.createParticle(type, pos.x, pos.y, pos.z, velocity.x, velocity.y, velocity.z);
		return particle instanceof ForceParticle force ? force : null;
	}

	/** A positional one-shot at the mod sound volume. */
	static void play(ClientLevel level, Vec3 pos, SoundEvent sound, float volume, float pitch) {
		level.playLocalSound(pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, volume * ClientConfig.get().modSoundVolume, pitch, false);
	}

	/** Pitch 1 with a small random spread. */
	static float pitchVariance(float spread) {
		return 1f + (RANDOM.nextFloat() * 2f - 1f) * spread;
	}

	static Vec3 jitter(double amount) {
		return new Vec3(jitterScalar(amount), jitterScalar(amount), jitterScalar(amount));
	}

	static double jitterScalar(double amount) {
		return (RANDOM.nextDouble() * 2 - 1) * amount;
	}

	private ForceFxUtil() {
	}
}
