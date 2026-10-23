package com.oldrepublic.client.enemy.hymn;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.boss.cantor.CantorParticles;
import com.oldrepublic.client.config.ClientConfig;
import com.oldrepublic.client.fx.CameraShake;
import com.oldrepublic.enemy.scour.ScourParticles;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Shared client helpers of the Milestone 5 foes and bosses (W8): the particle types they reuse,
 * looked up by id (spec MILESTONE_5.md 15.2; the M4 rule: another feature's type by id, with a
 * vanilla fallback when it is missing), W8's own two types, local sounds at the player's mod
 * volume, the ground's own dust, and a shake close to a blow.
 */
public final class FoeFx {
	public static final RandomSource RANDOM = RandomSource.create();
	private static final BlockParticleOption SAND = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState());

	/** Another feature's particle type by id, or {@code fallback} when it is not registered. */
	public static ParticleOptions byId(String id, ParticleOptions fallback) {
		return BuiltInRegistries.PARTICLE_TYPE.getValue(OldRepublicMod.id(id)) instanceof SimpleParticleType type ? type : fallback;
	}

	/** W6's {@code lightning_spark}: a tiny bright speck. */
	public static ParticleOptions lightningSpark() {
		return byId("lightning_spark", ParticleTypes.ELECTRIC_SPARK);
	}

	/** W6's {@code gather_dust}: a small dust mote. */
	public static ParticleOptions gatherDust() {
		return byId("gather_dust", ParticleTypes.WHITE_ASH);
	}

	/** W6's {@code air_ripple}: a faint shell of bent air. */
	public static ParticleOptions airRipple() {
		return byId("air_ripple", ParticleTypes.POOF);
	}

	/** The world's {@code tomb_dust}: grey-brown dust sifting down. */
	public static ParticleOptions tombDust() {
		return byId("tomb_dust", ParticleTypes.ASH);
	}

	/** The world's {@code shadow_wisp}: a deep violet smoky wisp. */
	public static ParticleOptions shadowWisp() {
		return byId("shadow_wisp", ParticleTypes.SMOKE);
	}

	/** The world's {@code light_mote}: a warm mote drifting slowly. */
	public static ParticleOptions lightMote() {
		return byId("light_mote", ParticleTypes.END_ROD);
	}

	/** The world's {@code vent_grit}: heavy chips with gravity and bounce. */
	public static ParticleOptions ventGrit() {
		return byId("vent_grit", ParticleTypes.CRIT);
	}

	/** The M4 {@code engine_exhaust}: a hot puff from a jet. */
	public static ParticleOptions engineExhaust() {
		return byId("engine_exhaust", ParticleTypes.SMOKE);
	}

	/** The M4 {@code spark_shower}: a short orange spark with gravity. */
	public static ParticleOptions sparkShower() {
		return byId("spark_shower", ParticleTypes.CRIT);
	}

	/** W8's {@code sand_burst} (the sand scour). */
	public static ParticleOptions sandBurst() {
		return ScourParticles.SAND_BURST;
	}

	/** W8's {@code spectral_wisp} (the First Cantor, his choir, and Ulvaine's echo coming apart). */
	public static ParticleOptions spectralWisp() {
		return CantorParticles.SPECTRAL_WISP;
	}

	/** Dust of the block under {@code at}, or sand dust over air. */
	public static ParticleOptions ground(ClientLevel level, Vec3 at) {
		BlockState below = level.getBlockState(BlockPos.containing(at.x, at.y - 0.2, at.z));
		return below.isAir() ? SAND : new BlockParticleOption(ParticleTypes.BLOCK, below);
	}

	public static void add(ClientLevel level, ParticleOptions type, Vec3 at, Vec3 velocity) {
		level.addParticle(type, at.x, at.y, at.z, velocity.x, velocity.y, velocity.z);
	}

	public static void add(ClientLevel level, ParticleOptions type, double x, double y, double z, double dx, double dy, double dz) {
		level.addParticle(type, x, y, z, dx, dy, dz);
	}

	/** A random offset in {@code [-amount, amount]}. */
	public static double jitter(double amount) {
		return (RANDOM.nextDouble() * 2 - 1) * amount;
	}

	/** A random vector with each component in {@code [-amount, amount]}. */
	public static Vec3 jitterVec(double amount) {
		return new Vec3(jitter(amount), jitter(amount), jitter(amount));
	}

	/** The horizontal unit vector of {@code dir}, or {@code fallback} when it has none. */
	public static Vec3 flat(Vec3 dir, Vec3 fallback) {
		Vec3 flat = new Vec3(dir.x, 0, dir.z);
		return flat.lengthSqr() < 1.0e-6 ? fallback : flat.normalize();
	}

	/** The horizontal direction an entity faces (its body), as a unit vector. */
	public static Vec3 facing(Entity entity) {
		float yaw = entity.getYRot() * ((float) Math.PI / 180f);
		return new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
	}

	/** A local hostile sound at the player's mod volume (the server plays the fights' own sounds). */
	public static void play(ClientLevel level, Vec3 pos, SoundEvent sound, float volume, float pitch) {
		level.playLocalSound(pos.x, pos.y, pos.z, sound, SoundSource.HOSTILE, volume * ClientConfig.get().modSoundVolume, pitch, false);
	}

	/** A shake for the local player when within {@code range} blocks of {@code at}. */
	public static void shake(Minecraft mc, Vec3 at, double range, float amount) {
		if (mc.player != null && mc.player.position().distanceToSqr(at) < range * range) {
			CameraShake.add(amount);
		}
	}

	private FoeFx() {
	}
}
