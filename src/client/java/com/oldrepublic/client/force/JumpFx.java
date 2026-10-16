package com.oldrepublic.client.force;

import static com.oldrepublic.client.force.ForceFxUtil.RANDOM;
import static com.oldrepublic.client.force.ForceFxUtil.dustColor;
import static com.oldrepublic.client.force.ForceFxUtil.groundBelow;
import static com.oldrepublic.client.force.ForceFxUtil.jitterScalar;
import static com.oldrepublic.client.force.ForceFxUtil.pitchVariance;
import static com.oldrepublic.client.force.ForceFxUtil.play;
import static com.oldrepublic.client.force.ForceFxUtil.ripple;
import static com.oldrepublic.client.force.ForceFxUtil.spawn;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.config.ClientConfig;
import com.oldrepublic.client.force.particle.ForceParticle;
import com.oldrepublic.client.fx.CameraShake;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.force.ForceEffects;
import com.oldrepublic.force.ForceParticles;
import com.oldrepublic.force.jump.JumpShape;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Force Jump visuals and sounds (spec 8.2): dust gathering at the feet while crouched, a
 * compressed dust burst at take-off, faint trail motes, a landing scaled by the fall and the
 * surface, the Crushing Descent shockwave, and the Soaring Leap glide.
 *
 * <p>The glide is the one piece of movement the client applies itself: a player's own motion is
 * simulated on its client, so the fall-speed cap runs here while the Jump key is held, and the
 * server only charges the energy and ends the glide (its {@code force_jump_glide} stop event).
 */
final class JumpFx {
	/** Longest held gather on the server (60 ticks) plus slack, for the pose watchdog. */
	private static final int GATHER_WATCHDOG = 70;
	private static final int TRAIL_TICKS = 10;
	/** Glide limits; the server's balance has the same defaults and ends the glide authoritatively. */
	private static final double GLIDE_FALL_SPEED = 0.15;
	private static final int GLIDE_MAX_TICKS = 30;
	/** A leap that has not landed after this long is forgotten. */
	private static final int LEAP_TIMEOUT = 200;
	private static final float SLAM_SHAKE = 0.25f;

	/** Jumper id to the client tick its trail ends. */
	private static final Map<Integer, Long> TRAILS = new HashMap<>();

	/** The local player's leap in progress (glide bookkeeping), or null. */
	private static final class OwnLeap {
		final boolean soaring;
		int age;
		int glideTicks;
		boolean gliding;
		boolean glideDone;

		OwnLeap(boolean soaring) {
			this.soaring = soaring;
		}
	}

	private static @Nullable OwnLeap ownLeap;

	/** {@code force_jump_gather}: start or stop. Entity = jumper. */
	static void onGather(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		if (!(entity instanceof LivingEntity living)) {
			return;
		}

		if (payload.variant() == ForceEffects.CHARGE_STOP) {
			PowerAnimations.stop(entity.getId(), PowerAnimations.Kind.JUMP_GATHER);
			return;
		}

		startGather(living, false);
	}

	/** Starts the gather pose and loop: from the event, or as the own prediction ({@code predicted}). */
	static void startGather(LivingEntity entity, boolean predicted) {
		if (predicted) {
			PowerAnimations.startPredicted(entity.getId(), PowerAnimations.Kind.JUMP_GATHER, -1, GATHER_WATCHDOG);
		} else {
			PowerAnimations.start(entity.getId(), PowerAnimations.Kind.JUMP_GATHER, -1, GATHER_WATCHDOG);
		}

		PowerAnimations.loop(entity, PowerAnimations.Kind.JUMP_GATHER, ModSounds.FORCE_JUMP_GATHER, 0.5f, 0.9f);
	}

	/** {@code force_jump_launch}. Entity = jumper, pos = take-off, dir = velocity, intensity = charge, variant = shape. */
	static void onLaunch(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		Vec3 pos = payload.pos();
		float charge = Mth.clamp(payload.intensity(), 0f, 1f);
		play(level, pos, ModSounds.FORCE_JUMP_LAUNCH, 0.7f + 0.4f * charge, pitchVariance(0.05f) + 0.1f * (1f - charge));

		if (entity != null) {
			PowerAnimations.start(entity.getId(), PowerAnimations.Kind.JUMP_LAUNCH, PowerAnimations.LAUNCH_TICKS, 0);

			if (ClientConfig.get().preset != ClientConfig.Preset.REDUCED) {
				TRAILS.put(entity.getId(), PowerAnimations.ticks() + TRAIL_TICKS);
			}

			if (entity == mc.player) {
				ownLeap = new OwnLeap(JumpShape.byOrdinal(payload.variant()) == JumpShape.SOAR);
			}
		}

		if (!Vfx.inRange(pos)) {
			return;
		}

		BlockPos ground = groundBelow(level, pos.add(0, 0.5, 0));
		float[] rgb = ground != null ? dustColor(level, ground) : new float[] {ForceFxUtil.DEFAULT_DUST_R, ForceFxUtil.DEFAULT_DUST_G, ForceFxUtil.DEFAULT_DUST_B};
		BlockState state = ground != null ? level.getBlockState(ground) : null;
		int count = Vfx.count(Math.round(8 + 10 * charge));

		// Compressed burst: dust shoved outward and low, block bits thrown up.
		for (int i = 0; i < count; i++) {
			double angle = RANDOM.nextDouble() * Math.PI * 2;
			Vec3 out = new Vec3(Math.cos(angle), 0, Math.sin(angle));
			double speed = 0.12 + 0.14 * charge * RANDOM.nextDouble();
			ForceParticle p = spawn(ForceParticles.GATHER_DUST, pos.add(out.scale(0.25)).add(0, 0.08, 0), out.scale(speed).add(0, 0.02, 0));

			if (p != null) {
				p.color(rgb[0], rgb[1], rgb[2]).size(0.1f + 0.04f * charge, 0.08f).alpha(0.6f).life(18 + RANDOM.nextInt(8)).drag(0.88f).fall(0.01f, true);
			}

			if (state != null && !state.isAir() && i % 3 == 0) {
				level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.x + out.x * 0.3, pos.y + 0.05, pos.z + out.z * 0.3,
						out.x * 0.15, 0.2 + 0.15 * charge, out.z * 0.15);
			}
		}

		ripple(pos.add(0, 0.15, 0), Vec3.ZERO, 0.3f, 1.2f + 0.8f * charge, 0.12f, 6);
	}

	/** {@code force_jump_glide}: start or stop. Entity = jumper. */
	static void onGlide(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		if (!(entity instanceof LivingEntity living)) {
			return;
		}

		if (payload.variant() == ForceEffects.CHARGE_STOP) {
			PowerAnimations.stop(entity.getId(), PowerAnimations.Kind.GLIDE);

			if (entity == mc.player && ownLeap != null) {
				ownLeap.glideDone = true;
				ownLeap.gliding = false;
			}

			return;
		}

		PowerAnimations.start(entity.getId(), PowerAnimations.Kind.GLIDE, -1, GLIDE_MAX_TICKS + 20);
		PowerAnimations.loop(living, PowerAnimations.Kind.GLIDE, ModSounds.FORCE_JUMP_GLIDE, 0.45f, 1f);
	}

	/** {@code force_jump_land}. Entity = jumper, pos = landing point, intensity = blocks fallen, variant = surface. */
	static void onLand(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		Vec3 pos = payload.pos();
		float fallen = Math.max(0f, payload.intensity());
		float strength = Mth.clamp(fallen / 8f, 0.15f, 1f);
		boolean heavy = fallen >= 4f;
		play(level, pos, ModSounds.FORCE_JUMP_LAND, 0.5f + 0.5f * strength, heavy ? 0.85f : 1.1f);

		if (entity != null) {
			PowerAnimations.stop(entity.getId(), PowerAnimations.Kind.GLIDE);
			TRAILS.remove(entity.getId());

			if (entity == mc.player) {
				ownLeap = null;
			}
		}

		if (!Vfx.inRange(pos)) {
			return;
		}

		BlockPos ground = groundBelow(level, pos.add(0, 0.5, 0));
		BlockState state = ground != null ? level.getBlockState(ground) : null;
		float[] rgb = ground != null ? dustColor(level, ground) : new float[] {ForceFxUtil.DEFAULT_DUST_R, ForceFxUtil.DEFAULT_DUST_G, ForceFxUtil.DEFAULT_DUST_B};
		int surface = payload.variant();

		// Per surface: sand bursts, stone chips, snow powder, soft ground a dust ring.
		int dust = switch (surface) {
			case ForceEffects.SURFACE_SAND -> 16;
			case ForceEffects.SURFACE_SNOW -> 18;
			case ForceEffects.SURFACE_STONE -> 5;
			default -> 9;
		};
		int chips = surface == ForceEffects.SURFACE_STONE ? 12 : 5;
		double rise = surface == ForceEffects.SURFACE_SAND ? 0.12 : surface == ForceEffects.SURFACE_SNOW ? 0.05 : 0.03;

		if (surface == ForceEffects.SURFACE_SNOW) {
			rgb = new float[] {0.95f, 0.96f, 0.98f};
		}

		for (int i = 0; i < Vfx.count(Math.round(dust * strength) + 2); i++) {
			double angle = RANDOM.nextDouble() * Math.PI * 2;
			Vec3 out = new Vec3(Math.cos(angle), 0, Math.sin(angle));
			ForceParticle p = spawn(ForceParticles.GATHER_DUST, pos.add(out.scale(0.3)).add(0, 0.1, 0),
					out.scale(0.08 + 0.12 * strength * RANDOM.nextDouble()).add(0, rise * (0.5 + RANDOM.nextDouble()), 0));

			if (p != null) {
				p.color(rgb[0], rgb[1], rgb[2]).size(0.1f, 0.09f).alpha(0.6f).life(20 + RANDOM.nextInt(10)).drag(0.9f).fall(0.015f, true);
			}
		}

		if (state != null && !state.isAir()) {
			BlockParticleOption debris = new BlockParticleOption(ParticleTypes.BLOCK, state);

			for (int i = 0; i < Vfx.count(Math.round(chips * strength) + 1); i++) {
				double angle = RANDOM.nextDouble() * Math.PI * 2;
				double speed = surface == ForceEffects.SURFACE_STONE ? 0.25 : 0.15;
				level.addParticle(debris, pos.x + jitterScalar(0.4), pos.y + 0.05, pos.z + jitterScalar(0.4),
						Math.cos(angle) * speed * strength, 0.15 + 0.15 * strength, Math.sin(angle) * speed * strength);
			}
		}
	}

	/** {@code force_jump_slam}: Crushing Descent. Entity = jumper, pos = centre, intensity = damage. */
	static void onSlam(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		Vec3 pos = payload.pos();
		play(level, pos, ModSounds.FORCE_JUMP_SLAM, 1f, pitchVariance(0.05f));

		if (entity == mc.player) {
			CameraShake.add(SLAM_SHAKE);
		}

		if (!Vfx.inRange(pos)) {
			return;
		}

		BlockPos ground = groundBelow(level, pos.add(0, 0.5, 0));
		BlockState state = ground != null ? level.getBlockState(ground) : null;

		// A ground-level shell of bent air running outward, and debris sprayed radially.
		int ring = Vfx.count(14);

		for (int i = 0; i < ring; i++) {
			double angle = Math.PI * 2 * i / ring;
			Vec3 out = new Vec3(Math.cos(angle), 0, Math.sin(angle));
			ripple(pos.add(out.scale(0.4)).add(0, 0.2, 0), out.scale(0.3), 0.3f, 0.9f, 0.16f, 10);

			if (state != null && !state.isAir()) {
				level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.x + out.x * 0.5, pos.y + 0.1, pos.z + out.z * 0.5,
						out.x * 0.35, 0.25 + RANDOM.nextDouble() * 0.15, out.z * 0.35);
			}
		}

		float[] rgb = ground != null ? dustColor(level, ground) : new float[] {ForceFxUtil.DEFAULT_DUST_R, ForceFxUtil.DEFAULT_DUST_G, ForceFxUtil.DEFAULT_DUST_B};

		for (int i = 0; i < Vfx.count(12); i++) {
			double angle = RANDOM.nextDouble() * Math.PI * 2;
			Vec3 out = new Vec3(Math.cos(angle), 0, Math.sin(angle));
			ForceParticle p = spawn(ForceParticles.GATHER_DUST, pos.add(out.scale(0.6)).add(0, 0.15, 0), out.scale(0.25).add(0, 0.05, 0));

			if (p != null) {
				p.color(rgb[0], rgb[1], rgb[2]).size(0.14f, 0.12f).alpha(0.55f).life(26 + RANDOM.nextInt(10)).drag(0.88f).fall(0.01f, true);
			}
		}
	}

	/** Gather dust, trails and the own glide. Called every client tick. */
	static void tick(Minecraft mc, ClientLevel level) {
		long now = PowerAnimations.ticks();
		tickTrails(level, now);
		tickOwnLeap(mc);

		if (now % 2 == 0) {
			for (int id : PowerAnimations.active(PowerAnimations.Kind.JUMP_GATHER)) {
				if (level.getEntity(id) instanceof LivingEntity living && Vfx.inRange(living.position())) {
					gatherTick(level, living, PowerAnimations.age(id, PowerAnimations.Kind.JUMP_GATHER, 0f));
				}
			}
		}
	}

	/** Anticipation: ground dust drawn in around the feet, a faint low shimmer. */
	private static void gatherTick(ClientLevel level, LivingEntity entity, float age) {
		for (int i = 0; i < Vfx.count(2); i++) {
			double angle = RANDOM.nextDouble() * Math.PI * 2;
			double distance = 1.0 + RANDOM.nextDouble() * 1.2;
			Vec3 around = entity.position().add(Math.cos(angle) * distance, 0.5, Math.sin(angle) * distance);
			BlockPos ground = groundBelow(level, around);

			if (ground == null) {
				continue;
			}

			Vec3 start = new Vec3(around.x, ground.getY() + 1.05, around.z);
			Vec3 feet = entity.position().add(0, 0.1, 0);
			ForceParticle p = spawn(ForceParticles.GATHER_DUST, start, feet.subtract(start).scale(1 / 12.0));

			if (p != null) {
				float[] rgb = dustColor(level, ground);
				p.color(rgb[0], rgb[1], rgb[2]).size(0.06f, 0.04f).alpha(0.7f).life(12);
			}

			if (i == 0 && ((int) age) % 6 == 0) {
				level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(ground)), start.x, start.y, start.z, 0, 0.08, 0);
			}
		}

		if (((int) age) % 6 == 0) {
			ripple(entity.position().add(0, 0.2, 0), Vec3.ZERO, 0.6f, 1.1f, 0.07f, 8);
		}
	}

	private static void tickTrails(ClientLevel level, long now) {
		if (TRAILS.isEmpty()) {
			return;
		}

		Iterator<Map.Entry<Integer, Long>> it = TRAILS.entrySet().iterator();

		while (it.hasNext()) {
			Map.Entry<Integer, Long> entry = it.next();
			Entity entity = level.getEntity(entry.getKey());

			if (now > entry.getValue() || entity == null || entity.isRemoved()) {
				it.remove();
				continue;
			}

			if (!Vfx.inRange(entity.position())) {
				continue;
			}

			Vec3 behind = entity.position().add(0, entity.getBbHeight() * 0.4, 0).subtract(entity.getDeltaMovement().scale(0.6));
			ForceParticle p = spawn(ForceParticles.JUMP_TRAIL, behind.add(jitterScalar(0.2), jitterScalar(0.3), jitterScalar(0.2)), Vec3.ZERO);

			if (p != null) {
				p.color(0.86f, 0.84f, 0.8f).size(0.05f, 0.02f).alpha(0.45f).life(10);
			}
		}
	}

	/** Soaring Leap: caps the own fall speed while Jump is held past the apex, once per leap. */
	private static void tickOwnLeap(Minecraft mc) {
		OwnLeap leap = ownLeap;
		LocalPlayer player = mc.player;

		if (leap == null) {
			return;
		}

		if (player == null || !player.isAlive() || ++leap.age > LEAP_TIMEOUT || leap.age > 2 && player.onGround()) {
			ownLeap = null;
			return;
		}

		if (!leap.soaring || leap.glideDone || mc.isPaused()) {
			return;
		}

		Vec3 motion = player.getDeltaMovement();
		boolean holding = mc.options.keyJump.isDown() && mc.gui.screen() == null;

		if (holding && motion.y < 0) {
			leap.gliding = true;

			if (++leap.glideTicks > GLIDE_MAX_TICKS) {
				leap.glideDone = true;
				return;
			}

			player.setDeltaMovement(motion.x, Math.max(motion.y, -GLIDE_FALL_SPEED), motion.z);
		} else if (leap.gliding) {
			// Letting go ends the glide for this leap, as on the server.
			leap.glideDone = true;
		}
	}

	static void clear() {
		TRAILS.clear();
		ownLeap = null;
	}

	private JumpFx() {
	}
}
