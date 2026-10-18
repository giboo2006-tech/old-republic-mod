package com.oldrepublic.client.force.speed;

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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.Vec3;

/**
 * Helpers shared by the Milestone 4 power visuals (Force Speed, Force Barrier and Force Choke):
 * ground lookup and dust colour, particle spawning with the fluent {@link ForceParticle} setters,
 * and local sounds at the mod volume. The Milestone 2 power visuals keep their own package-private
 * helper; these follow the same rules.
 */
public final class FxKit {
	public static final float DUST_R = 0.62f;
	public static final float DUST_G = 0.57f;
	public static final float DUST_B = 0.49f;
	public static final RandomSource RANDOM = RandomSource.create();

	/** The middle of the body. */
	public static Vec3 chest(Entity entity) {
		return entity.position().add(0, entity.getBbHeight() * 0.6, 0);
	}

	/** The neck and shoulders: just under the eyes. */
	public static Vec3 neck(Entity entity) {
		return entity.position().add(0, entity.getEyeHeight() * 0.88, 0);
	}

	/** {@code dir} flattened to the horizontal and normalised; +Z when it has no horizontal part. */
	public static Vec3 horizontal(Vec3 dir) {
		Vec3 flat = new Vec3(dir.x, 0, dir.z);
		return flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
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
			return new float[] {DUST_R, DUST_G, DUST_B};
		}

		MapColor color = level.getBlockState(pos).getMapColor(level, pos);

		if (color == MapColor.NONE || color.col == 0) {
			return new float[] {DUST_R, DUST_G, DUST_B};
		}

		float r = (color.col >> 16 & 255) / 255f;
		float g = (color.col >> 8 & 255) / 255f;
		float b = (color.col & 255) / 255f;
		return new float[] {Mth.lerp(0.35f, r, 0.8f), Mth.lerp(0.35f, g, 0.77f), Mth.lerp(0.35f, b, 0.7f)};
	}

	/** Whether the ground at {@code pos} is grass or plants, so leaves can join the dust. */
	public static boolean leafy(ClientLevel level, @Nullable BlockPos pos) {
		if (pos == null) {
			return false;
		}

		MapColor color = level.getBlockState(pos).getMapColor(level, pos);
		return color == MapColor.GRASS || color == MapColor.PLANT;
	}

	/** Spawns one particle of a {@code ForceParticle} type, or returns null when the engine refused it. */
	public static @Nullable ForceParticle spawn(SimpleParticleType type, Vec3 pos, Vec3 velocity) {
		Particle particle = Minecraft.getInstance().particleEngine.createParticle(type, pos.x, pos.y, pos.z, velocity.x, velocity.y, velocity.z);
		return particle instanceof ForceParticle force ? force : null;
	}

	/** A dust mote tinted {@code rgb}. */
	public static void dust(Vec3 pos, Vec3 velocity, float[] rgb, float size, float alpha, int life, float gravity) {
		ForceParticle p = spawn(ForceParticles.GATHER_DUST, pos, velocity);

		if (p != null) {
			p.color(rgb[0], rgb[1], rgb[2]).size(size, size * 1.4f).alpha(alpha).life(life).drag(0.92f).fall(gravity, gravity > 0.2f);
		}
	}

	/** One bent-air ripple (no colour, no glow). */
	public static void ripple(Vec3 pos, Vec3 velocity, float startSize, float endSize, float alpha, int life) {
		ForceParticle p = spawn(ForceParticles.AIR_RIPPLE, pos, velocity);

		if (p != null) {
			p.size(startSize, endSize).alpha(alpha).life(life);
		}
	}

	/** One faint streak of bent air lying along {@code velocity}. */
	public static void streak(Vec3 pos, Vec3 velocity, float size, float alpha, int life) {
		ForceParticle p = spawn(ForceParticles.PUSH_STREAK, pos, velocity);

		if (p != null) {
			p.size(size, size * 1.3f).alpha(alpha).life(life).drag(0.8f);
		}
	}

	/** A positional one-shot at the mod sound volume. */
	public static void play(ClientLevel level, Vec3 pos, SoundEvent sound, float volume, float pitch) {
		level.playLocalSound(pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, volume * ClientConfig.get().modSoundVolume, pitch, false);
	}

	/** Pitch 1 with a small random spread. */
	public static float pitch(float spread) {
		return 1f + (RANDOM.nextFloat() * 2f - 1f) * spread;
	}

	public static Vec3 jitter(double amount) {
		return new Vec3(jitter1(amount), jitter1(amount), jitter1(amount));
	}

	public static double jitter1(double amount) {
		return (RANDOM.nextDouble() * 2 - 1) * amount;
	}

	/** A random horizontal unit vector. */
	public static Vec3 randomFlat() {
		double angle = RANDOM.nextDouble() * Math.PI * 2;
		return new Vec3(Math.cos(angle), 0, Math.sin(angle));
	}

	private FxKit() {
	}
}
