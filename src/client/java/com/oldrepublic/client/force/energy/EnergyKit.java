package com.oldrepublic.client.force.energy;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.config.ClientConfig;
import com.oldrepublic.client.force.particle.ForceParticle;

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
 * Helpers for the energy and mind powers' effects: where a hand, a chest or a head is between ticks, the ground under a
 * point and the colour of its dust, particle spawning with the fluent {@link ForceParticle} setters, and local sounds at the
 * mod volume.
 */
public final class EnergyKit {
	public static final RandomSource RANDOM = RandomSource.create();
	/** Plain dust, for ground without a map colour. */
	private static final float[] DUST = {0.62f, 0.57f, 0.49f};

	/**
	 * Where the hand of {@code entity} is ({@code left} for the left one), reaching {@code reach} along its look (0 drawn in
	 * at the shoulder, 1 thrust out). For the camera's own player in first person it is a point low at that side of the
	 * view, where the first-person hand is drawn, so light leaving the hand comes up from the bottom of the screen.
	 */
	public static Vec3 hand(LivingEntity entity, boolean left, float reach, float partial) {
		Vec3 look = entity.getViewVector(partial);

		if (firstPerson(entity)) {
			Vec3 side = side(look);
			Vec3 up = side.cross(look).normalize();
			return entity.getEyePosition(partial).add(look.scale(0.6)).add(side.scale(left ? -0.36 : 0.36)).add(up.scale(-0.31));
		}

		float yaw = Mth.rotLerp(partial, entity.yBodyRotO, entity.yBodyRot) * Mth.DEG_TO_RAD;
		Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
		Vec3 shoulder = entity.getPosition(partial).add(0, entity.getEyeHeight() * 0.8, 0).add(right.scale(left ? -0.33 : 0.33));
		return shoulder.add(look.scale(0.15 + 0.55 * reach));
	}

	/** Whether {@code entity} is what the camera sees from, in first person (its body is not drawn). */
	public static boolean firstPerson(Entity entity) {
		Minecraft mc = Minecraft.getInstance();
		return entity == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
	}

	/** The free hand: the main arm when the main hand is empty, otherwise the off arm. */
	public static boolean freeArmIsLeft(LivingEntity entity) {
		HumanoidArm main = entity.getMainArm();
		HumanoidArm arm = entity.getMainHandItem().isEmpty() ? main : main.getOpposite();
		return arm == HumanoidArm.LEFT;
	}

	/** The unit vector to the right of {@code look}, level with the ground (east when looking straight up or down). */
	public static Vec3 side(Vec3 look) {
		Vec3 side = look.cross(new Vec3(0, 1, 0));
		return side.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : side.normalize();
	}

	/** The middle of the body between ticks. */
	public static Vec3 chest(Entity entity, float partial) {
		return entity.getPosition(partial).add(0, entity.getBbHeight() * 0.6, 0);
	}

	/** The middle of the body. */
	public static Vec3 chest(Entity entity) {
		return entity.position().add(0, entity.getBbHeight() * 0.6, 0);
	}

	/** The middle of the head. */
	public static Vec3 head(Entity entity) {
		return entity.position().add(0, entity.getEyeHeight() + 0.05, 0);
	}

	/** First solid block at or up to {@code depth} below {@code pos}, or null. */
	public static @Nullable BlockPos groundBelow(ClientLevel level, Vec3 pos, int depth) {
		BlockPos cursor = BlockPos.containing(pos);

		for (int i = 0; i <= depth; i++) {
			BlockState state = level.getBlockState(cursor);

			if (!state.isAir() && state.isSolid()) {
				return cursor;
			}

			cursor = cursor.below();
		}

		return null;
	}

	/** Airborne dust colour of the block at {@code pos} (lighter and less saturated than its map colour), or plain dust for null. */
	public static float[] dustColor(ClientLevel level, @Nullable BlockPos pos) {
		if (pos == null) {
			return DUST.clone();
		}

		MapColor color = level.getBlockState(pos).getMapColor(level, pos);

		if (color == MapColor.NONE || color.col == 0) {
			return DUST.clone();
		}

		float r = (color.col >> 16 & 255) / 255f;
		float g = (color.col >> 8 & 255) / 255f;
		float b = (color.col & 255) / 255f;
		return new float[] {Mth.lerp(0.35f, r, 0.8f), Mth.lerp(0.35f, g, 0.77f), Mth.lerp(0.35f, b, 0.7f)};
	}

	/** Whether the block at {@code pos} stops a spark or a mote. */
	static boolean solid(ClientLevel level, double x, double y, double z) {
		BlockState state = level.getBlockState(BlockPos.containing(x, y, z));
		return !state.isAir() && state.isSolid();
	}

	/** Spawns one particle of a {@code ForceParticle} type, or returns null when the engine refused it. */
	public static @Nullable ForceParticle spawn(SimpleParticleType type, Vec3 pos, Vec3 velocity) {
		Particle particle = Minecraft.getInstance().particleEngine.createParticle(type, pos.x, pos.y, pos.z, velocity.x, velocity.y, velocity.z);
		return particle instanceof ForceParticle force ? force : null;
	}

	/** A positional one-shot at the mod sound volume. */
	public static void play(ClientLevel level, Vec3 pos, SoundEvent sound, float volume, float pitch) {
		level.playLocalSound(pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, volume * ClientConfig.get().modSoundVolume, pitch, false);
	}

	/** Pitch 1 with a small random spread. */
	public static float pitch(float spread) {
		return 1f + (RANDOM.nextFloat() * 2f - 1f) * spread;
	}

	/** A random offset up to {@code amount} along each axis. */
	public static Vec3 jitter(double amount) {
		return new Vec3(jitter1(amount), jitter1(amount), jitter1(amount));
	}

	public static double jitter1(double amount) {
		return (RANDOM.nextDouble() * 2 - 1) * amount;
	}

	/** A random unit vector. */
	public static Vec3 randomUnit() {
		Vec3 v = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian(), RANDOM.nextGaussian());
		return v.lengthSqr() < 1.0e-6 ? new Vec3(0, 1, 0) : v.normalize();
	}

	/** A random horizontal unit vector. */
	public static Vec3 randomFlat() {
		double angle = RANDOM.nextDouble() * Math.PI * 2;
		return new Vec3(Math.cos(angle), 0, Math.sin(angle));
	}

	/** A random unit vector within {@code spread} radians of {@code axis} (a unit vector). */
	public static Vec3 cone(Vec3 axis, double spread) {
		Vec3 across = randomUnit();
		across = across.subtract(axis.scale(across.dot(axis)));
		across = across.lengthSqr() < 1.0e-6 ? side(axis) : across.normalize();
		double angle = Math.sqrt(RANDOM.nextDouble()) * spread;
		return axis.scale(Math.cos(angle)).add(across.scale(Math.sin(angle))).normalize();
	}

	/** Smooth value noise, -1 to 1. */
	public static float noise(float x) {
		int i = Mth.floor(x);
		float f = x - i;
		float t = f * f * (3f - 2f * f);
		return Mth.lerp(t, hash(i), hash(i + 1));
	}

	/** A fixed pseudo-random value, -1 to 1, for an integer. */
	static float hash(int i) {
		int h = i * 0x27D4EB2D;
		h ^= h >>> 15;
		h *= 0x165667B1;
		h ^= h >>> 13;
		return (h & 0xFFFF) / 32767.5f - 1f;
	}

	private EnergyKit() {
	}
}
