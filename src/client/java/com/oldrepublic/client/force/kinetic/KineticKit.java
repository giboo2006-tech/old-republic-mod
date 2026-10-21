package com.oldrepublic.client.force.kinetic;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.force.particle.ForceParticle;
import com.oldrepublic.client.force.speed.FxKit;
import com.oldrepublic.force.KineticParticles;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Helpers of the kinetic powers' effects: the free hand, the world light at a point, and spawning the kinetic particles
 * (dust clouds, pebbles, streaks of bent air, the ground's own block bits) with the ground's colour. Callers spend their
 * counts through {@code Vfx.count} and check {@code Vfx.inRange} first.
 */
public final class KineticKit {
	public static final RandomSource RANDOM = RandomSource.create();

	/** The free hand: the main arm when the main hand is empty, otherwise the off arm. */
	public static boolean freeArmIsLeft(LivingEntity entity) {
		HumanoidArm main = entity.getMainArm();
		HumanoidArm arm = entity.getMainHandItem().isEmpty() ? main : main.getOpposite();
		return arm == HumanoidArm.LEFT;
	}

	/**
	 * Where the free hand is, {@code extend} 0 drawn back to 1 thrust out along the look; the body at {@code partialTick}
	 * between the last tick and this one.
	 */
	public static Vec3 hand(LivingEntity entity, float extend, float partialTick) {
		boolean left = freeArmIsLeft(entity);
		double yaw = Math.toRadians(entity.yBodyRot);
		Vec3 forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
		Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
		Vec3 feet = entity.getPosition(partialTick);
		Vec3 shoulder = feet.add(0, entity.getEyeHeight() * 0.8, 0).add(right.scale(left ? -0.36 : 0.36));
		Vec3 look = entity.getViewVector(partialTick);
		return shoulder.add(look.scale(0.25 + 0.5 * extend)).add(forward.scale(-0.15 * (1 - extend)));
	}

	/** World light at {@code pos}, 0 (dark) to 1 (full day or a torch beside it), with night's sky darkening. */
	public static float light(ClientLevel level, Vec3 pos) {
		return level.getMaxLocalRawBrightness(BlockPos.containing(pos)) / 15f;
	}

	/** The first solid block at or up to {@code depth} below {@code pos}, or null. */
	public static @Nullable BlockPos ground(ClientLevel level, Vec3 pos, int depth) {
		return FxKit.groundBelow(level, pos, depth);
	}

	/** Airborne dust colour of the block at {@code pos} (plain dust for null). */
	public static float[] dustColor(ClientLevel level, @Nullable BlockPos pos) {
		return FxKit.dustColor(level, pos);
	}

	/** The colour of a cloud of dust in the air: the ground's dust colour, paler (fine dust catches the light). */
	public static float[] airDust(float[] dust) {
		return new float[] {dust[0] + (0.88f - dust[0]) * 0.3f, dust[1] + (0.85f - dust[1]) * 0.3f, dust[2] + (0.8f - dust[2]) * 0.3f};
	}

	/** The colour of thrown grit: the dust colour, darker. */
	public static float[] gritColor(float[] dust) {
		return new float[] {dust[0] * 0.62f, dust[1] * 0.6f, dust[2] * 0.58f};
	}

	/** A cloud of dust tinted {@code rgb}, growing from {@code size} to {@code size * grow}; null when refused. */
	public static @Nullable DustCloudParticle dust(Vec3 pos, Vec3 velocity, float[] rgb, float size, float grow, float alpha, int life) {
		if (spawn(KineticParticles.KINETIC_DUST, pos, velocity) instanceof DustCloudParticle cloud) {
			cloud.color(rgb[0], rgb[1], rgb[2]).size(size, size * grow).alpha(alpha).life(life);
			return cloud;
		}

		return null;
	}

	/** A pebble tinted {@code rgb} thrown with {@code velocity}; null when refused. */
	public static @Nullable PebbleParticle pebble(Vec3 pos, Vec3 velocity, float[] rgb, float size, int life) {
		if (spawn(KineticParticles.KINETIC_PEBBLE, pos, velocity) instanceof PebbleParticle pebble) {
			float shade = 0.85f + RANDOM.nextFloat() * 0.3f;
			pebble.color(Math.min(1f, rgb[0] * shade), Math.min(1f, rgb[1] * shade), Math.min(1f, rgb[2] * shade)).size(size, size).life(life);
			return pebble;
		}

		return null;
	}

	/** A streak of bent air flying with {@code velocity}; null when refused. */
	public static @Nullable AirStreakParticle streak(Vec3 pos, Vec3 velocity, float size, float alpha, int life) {
		if (spawn(KineticParticles.KINETIC_STREAK, pos, velocity) instanceof AirStreakParticle streak) {
			streak.size(size, size).alpha(alpha).life(life);
			return streak;
		}

		return null;
	}

	/** One of the vanilla block bits of {@code state} (its real texture), thrown with {@code velocity}. */
	public static void blockBit(ClientLevel level, BlockState state, Vec3 pos, Vec3 velocity) {
		if (!state.isAir()) {
			level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.x, pos.y, pos.z, velocity.x, velocity.y, velocity.z);
		}
	}

	/** Spawns one particle of a {@code ForceParticle} type, or returns null when the engine refused it. */
	public static @Nullable ForceParticle spawn(SimpleParticleType type, Vec3 pos, Vec3 velocity) {
		Particle particle = Minecraft.getInstance().particleEngine.createParticle(type, pos.x, pos.y, pos.z, velocity.x, velocity.y, velocity.z);
		return particle instanceof ForceParticle force ? force : null;
	}

	/** A random direction on the horizontal circle. */
	public static Vec3 flat(double angle) {
		return new Vec3(Math.cos(angle), 0, Math.sin(angle));
	}

	public static double spread(double amount) {
		return (RANDOM.nextDouble() * 2 - 1) * amount;
	}

	public static Vec3 jitter(double amount) {
		return new Vec3(spread(amount), spread(amount), spread(amount));
	}

	/** {@code dir} with its vertical part removed, normalised; +Z when it has none. */
	public static Vec3 horizontal(Vec3 dir) {
		return FxKit.horizontal(dir);
	}

	private KineticKit() {
	}
}
