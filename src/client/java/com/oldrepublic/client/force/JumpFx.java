package com.oldrepublic.client.force;

import static com.oldrepublic.client.force.ForceFxUtil.RANDOM;
import static com.oldrepublic.client.force.ForceFxUtil.pitchVariance;
import static com.oldrepublic.client.force.ForceFxUtil.play;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.config.ClientConfig;
import com.oldrepublic.client.force.kinetic.AirStreakParticle;
import com.oldrepublic.client.force.kinetic.DustCloudParticle;
import com.oldrepublic.client.force.kinetic.KineticKit;
import com.oldrepublic.client.force.kinetic.KineticNoise;
import com.oldrepublic.client.force.kinetic.KineticRender;
import com.oldrepublic.client.fx.CameraShake;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.force.ForceEffects;
import com.oldrepublic.force.jump.JumpShape;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Force Jump visuals and sounds (spec 8.2). Crouched and gathering: dust is drawn in along the ground from all round and
 * spirals into the feet, grit hops on the ground as if it trembled, and every few ticks the air underfoot draws in. The
 * take-off is a compressed burst: air blasted down and out of the ground throws dust out low in patches, flings grit and the
 * ground's own bits, dust is sucked up after the body in a column, streaks of air rush down past the legs and burst out
 * low over the ground; for a moment streaks of air trail the leap. The landing throws up dust and chips by the fall and the
 * surface (sand bursts, stone chips, snow powder), a heavy one bursting air out low over the ground too, and Crushing
 * Descent hits like a blast: it runs out low over the ground from the point of impact and shows only in the dust and grit it
 * tears off as it passes. The Soaring Leap glide is the one piece of movement here.
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

		BlockPos ground = KineticKit.ground(level, pos.add(0, 0.5, 0), 3);
		float[] dust = KineticKit.dustColor(level, ground);
		float[] grit = KineticKit.gritColor(dust);
		BlockState state = ground != null ? level.getBlockState(ground) : null;
		double top = ground != null ? ground.getY() + 1.0 : pos.y;
		Vec3 base = new Vec3(pos.x, top, pos.z);
		// Air blasted down and out of the ground: dust thrown out low in patches, with gaps.
		burst(level, base, dust, grit, state, Vfx.count(Math.round(14 + 14 * charge)), Vfx.count(Math.round(6 + 8 * charge)), 0.14 + 0.2 * charge, 1.6f);
		float[] haze = KineticKit.airDust(dust);

		// Dust sucked up off the ground after the leaping body, a column that slows and spreads under it.
		for (int i = 0, n = Vfx.count(Math.round(7 + 5 * charge)); i < n; i++) {
			Vec3 at = base.add(KineticKit.spread(0.3), 0.15 + RANDOM.nextDouble() * 0.2, KineticKit.spread(0.3));
			Vec3 vel = new Vec3(KineticKit.spread(0.03), (0.12 + RANDOM.nextDouble() * 0.22) * (0.6 + 0.6 * charge), KineticKit.spread(0.03));
			DustCloudParticle cloud = KineticKit.dust(at, vel, haze, 0.28f + RANDOM.nextFloat() * 0.12f, 2.6f, 0.62f, 20 + RANDOM.nextInt(10));

			if (cloud != null) {
				cloud.hold(0.25f).drag(0.85f);
			}
		}

		// Streaks of air rushing down past the legs, and the blast running out low over the ground.
		for (int i = 0, n = Vfx.count(Math.round(4 + 6 * charge)); i < n; i++) {
			Vec3 out = KineticKit.flat(RANDOM.nextDouble() * Math.PI * 2);
			Vec3 at = base.add(out.scale(0.25 + RANDOM.nextDouble() * 0.3)).add(0, 0.6 + RANDOM.nextDouble() * 0.8, 0);
			AirStreakParticle streak = KineticKit.streak(at, out.scale(0.15).add(0, -0.45 - RANDOM.nextDouble() * 0.3, 0), 0.32f, 0.65f, 6);

			if (streak != null) {
				streak.drag(0.8f);
			}
		}

		groundStreaks(base, Vfx.count(Math.round(6 + 6 * charge)), 0.55 + 0.3 * charge);
	}

	/** Streaks of air bursting out low over the ground from {@code base}, {@code speed} blocks a tick: the blast's hard edge. */
	private static void groundStreaks(Vec3 base, int count, double speed) {
		for (int i = 0; i < count; i++) {
			Vec3 out = KineticKit.flat(RANDOM.nextDouble() * Math.PI * 2);
			Vec3 at = base.add(out.scale(0.4 + RANDOM.nextDouble() * 0.3)).add(0, 0.1 + RANDOM.nextDouble() * 0.3, 0);
			AirStreakParticle streak = KineticKit.streak(at, out.scale(speed * (0.8 + RANDOM.nextDouble() * 0.4)).add(0, 0.02, 0), 0.34f, 0.65f, 6);

			if (streak != null) {
				streak.stretch(0.6f).drag(0.82f);
			}
		}
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

		BlockPos ground = KineticKit.ground(level, pos.add(0, 0.5, 0), 3);
		BlockState state = ground != null ? level.getBlockState(ground) : null;
		float[] dust = KineticKit.dustColor(level, ground);
		int surface = payload.variant();
		double top = ground != null ? ground.getY() + 1.0 : pos.y;
		Vec3 base = new Vec3(pos.x, top, pos.z);

		// Per surface: sand bursts high and thick, snow is a wide powder, stone throws chips and little dust, soft ground puffs.
		int clouds = switch (surface) {
			case ForceEffects.SURFACE_SAND -> 20;
			case ForceEffects.SURFACE_SNOW -> 22;
			case ForceEffects.SURFACE_STONE -> 7;
			default -> 14;
		};
		int chips = surface == ForceEffects.SURFACE_STONE ? 12 : 6;
		double rise = surface == ForceEffects.SURFACE_SAND ? 0.1 : surface == ForceEffects.SURFACE_SNOW ? 0.05 : 0.03;

		if (surface == ForceEffects.SURFACE_SNOW) {
			dust = new float[] {0.95f, 0.96f, 0.98f};
		}

		float[] grit = surface == ForceEffects.SURFACE_SNOW ? new float[] {0.86f, 0.88f, 0.92f} : KineticKit.gritColor(dust);
		int n = Vfx.count(Math.round(clouds * strength) + 3);
		int m = Vfx.count(Math.round(chips * strength) + 2);
		burst(level, base, dust, grit, state, n, m, (heavy ? 0.12 : 0.07) + 0.14 * strength, (float) rise / 0.03f * 0.5f);

		if (heavy) {
			// The air the body drove down bursts out low over the ground.
			groundStreaks(base, Vfx.count(Math.round(4 + 6 * strength)), 0.4 + 0.3 * strength);
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

		BlockPos ground = KineticKit.ground(level, pos.add(0, 0.5, 0), 3);
		BlockState state = ground != null ? level.getBlockState(ground) : null;
		float[] dust = KineticKit.dustColor(level, ground);
		float[] grit = KineticKit.gritColor(dust);
		double top = ground != null ? ground.getY() + 1.0 : pos.y;
		Vec3 base = new Vec3(pos.x, top, pos.z);
		// The blast runs out over the ground, low and wide: it shows only in what it tears up as it passes.
		// The impact itself: a thick burst of dust and grit at the centre.
		burst(level, base, dust, grit, state, Vfx.count(10), Vfx.count(10), 0.22, 1.4f);

		// The ground torn up as the front passes: patches of dust rolling outward, grit and bits thrown ahead.
		int[] clouds = {Vfx.count(18)};
		int[] pebbles = {Vfx.count(10)};
		float[] last = {0.8f};
		int[] tick = {0};
		float seed = RANDOM.nextFloat() * 40f;
		KineticRender.run(l -> {
			tick[0]++;
			float r = Math.min(3.6f, 0.4f + 3.8f * (1f - (float) Math.pow(1f - Math.min(1f, tick[0] / 10f), 2.4f)));

			for (int i = 0, k = Math.min(clouds[0], 4); i < k; i++) {
				double a = RANDOM.nextDouble() * Math.PI * 2;
				double d = last[0] + RANDOM.nextDouble() * Math.max(0.1, r - last[0]);

				if (KineticNoise.at((float) Math.cos(a) * 2.5f + seed, (float) Math.sin(a) * 2.5f, (float) d * 0.7f) < 0.36f) {
					continue;
				}

				Vec3 out = KineticKit.flat(a);
				BlockPos under = KineticKit.ground(l, base.add(out.scale(d)).add(0, 1.2, 0), 3);

				if (under == null) {
					continue;
				}

				Vec3 at = new Vec3(base.x + out.x * d, under.getY() + 1.12, base.z + out.z * d);
				clouds[0]--;
				DustCloudParticle cloud = KineticKit.dust(at, out.scale(0.14 + RANDOM.nextDouble() * 0.14).add(0, 0.03 + RANDOM.nextDouble() * 0.05, 0),
						KineticKit.dustColor(l, under), 0.24f + RANDOM.nextFloat() * 0.16f, 2.8f, 0.55f, 34 + RANDOM.nextInt(20));

				if (cloud != null) {
					cloud.hold(0.2f).lift(-0.002f).drag(0.88f);
				}

				if (pebbles[0] > 0 && RANDOM.nextBoolean()) {
					pebbles[0]--;
					KineticKit.pebble(at, out.scale(0.14 + RANDOM.nextDouble() * 0.14).add(0, 0.2 + RANDOM.nextDouble() * 0.15, 0),
							KineticKit.gritColor(KineticKit.dustColor(l, under)), 0.05f + RANDOM.nextFloat() * 0.04f, 30);
				}
			}

			last[0] = r;
			return tick[0] < 9 && clouds[0] > 0;
		});

		for (int i = 0, n = Vfx.count(10); i < n; i++) {
			Vec3 out = KineticKit.flat(RANDOM.nextDouble() * Math.PI * 2);
			AirStreakParticle streak = KineticKit.streak(base.add(out.scale(0.6)).add(0, 0.2 + RANDOM.nextDouble() * 0.4, 0), out.scale(0.7 + RANDOM.nextDouble() * 0.3),
					0.18f, 0.55f, 7);

			if (streak != null) {
				streak.stretch(0.5f).drag(0.8f);
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

	/**
	 * Anticipation: dust is drawn in along the ground from all round and spirals into the feet, grit hops on the ground as
	 * if it trembled, more as the gather builds, and every few ticks the air underfoot draws in.
	 */
	private static void gatherTick(ClientLevel level, LivingEntity entity, float age) {
		Vec3 feet = entity.position();
		BlockPos under = KineticKit.ground(level, feet.add(0, 0.4, 0), 3);

		if (under == null) {
			return;
		}

		Vec3 base = new Vec3(feet.x, under.getY() + 1.0, feet.z);
		// The gather builds over a second and a half.
		float build = Mth.clamp(age / 30f, 0f, 1f);
		float turn = (entity.getId() & 1) == 0 ? 0.1f : -0.1f;

		for (int i = 0, n = Vfx.count(4); i < n; i++) {
			double angle = RANDOM.nextDouble() * Math.PI * 2;
			double distance = 1.2 + RANDOM.nextDouble() * 1.4;
			Vec3 around = feet.add(Math.cos(angle) * distance, 0.5, Math.sin(angle) * distance);
			BlockPos ground = KineticKit.ground(level, around, 3);

			if (ground == null) {
				continue;
			}

			Vec3 start = new Vec3(around.x, ground.getY() + 1.12, around.z);
			DustCloudParticle cloud = KineticKit.dust(start, Vec3.ZERO, KineticKit.airDust(KineticKit.dustColor(level, ground)), 0.3f + RANDOM.nextFloat() * 0.15f,
					1.4f, 0.5f + 0.2f * build, 16 + RANDOM.nextInt(6));

			if (cloud != null) {
				// Spiralling in toward the feet, low over the ground.
				cloud.swirl(base.add(0, 0.12, 0), turn, 0.09f).hold(0.5f);
			}
		}

		// Grit hopping on the ground round the feet, higher as the gather builds.
		float[] grit = KineticKit.gritColor(KineticKit.dustColor(level, under));

		for (int i = 0, n = Vfx.count(2); i < n; i++) {
			Vec3 out = KineticKit.flat(RANDOM.nextDouble() * Math.PI * 2);
			Vec3 at = base.add(out.scale(0.25 + RANDOM.nextDouble() * 0.6)).add(0, 0.04, 0);
			KineticKit.pebble(at, new Vec3(KineticKit.spread(0.01), 0.05 + RANDOM.nextDouble() * 0.06 * (0.5 + build), KineticKit.spread(0.01)), grit,
					0.045f + RANDOM.nextFloat() * 0.035f, 10 + RANDOM.nextInt(4));
		}

		if (((int) age) % 6 == 0) {
			// The air underfoot drawn in: low streaks closing on the feet.
			for (int i = 0, n = Vfx.count(3); i < n; i++) {
				Vec3 out = KineticKit.flat(RANDOM.nextDouble() * Math.PI * 2);
				Vec3 from = feet.add(out.scale(1.1 + RANDOM.nextDouble() * 0.6)).add(0, 0.12 + RANDOM.nextDouble() * 0.25, 0);
				AirStreakParticle streak = KineticKit.streak(from, out.scale(-0.22), 0.26f, 0.6f, 6);

				if (streak != null) {
					streak.drag(0.9f);
				}
			}
		}
	}

	/**
	 * Dust thrown out low from {@code base} in patches with gaps ({@code clouds} puffs, {@code speed} their outward speed),
	 * and {@code chips} pebbles and bits of the ground flung up; {@code rise} scales how high the dust is thrown.
	 */
	private static void burst(ClientLevel level, Vec3 base, float[] dust, float[] grit, @Nullable BlockState state, int clouds, int chips, double speed,
			float rise) {
		float seed = RANDOM.nextFloat() * 40f;
		// Dust in the air is paler than the ground it came off.
		float[] haze = KineticKit.airDust(dust);

		for (int i = 0; i < clouds; i++) {
			double angle = RANDOM.nextDouble() * Math.PI * 2;

			if (KineticNoise.at((float) Math.cos(angle) * 2.2f + seed, (float) Math.sin(angle) * 2.2f, 0.5f) < 0.33f) {
				// A gap: the dust bursts out in patches, never an even ring.
				angle += Math.PI * (0.25 + RANDOM.nextDouble() * 0.5);
			}

			Vec3 out = KineticKit.flat(angle);
			Vec3 at = base.add(out.scale(0.2 + RANDOM.nextDouble() * 0.35)).add(0, 0.12 + RANDOM.nextDouble() * 0.2, 0);
			Vec3 vel = out.scale(speed * (0.6 + RANDOM.nextDouble() * 0.8)).add(0, (0.02 + RANDOM.nextDouble() * 0.05) * rise, 0);
			DustCloudParticle cloud = KineticKit.dust(at, vel, haze, 0.3f + RANDOM.nextFloat() * 0.18f, 2.8f, 0.68f + RANDOM.nextFloat() * 0.1f,
					28 + RANDOM.nextInt(18));

			if (cloud != null) {
				cloud.hold(0.2f).lift(-0.002f).drag(0.87f);
			}
		}

		for (int i = 0; i < chips; i++) {
			Vec3 out = KineticKit.flat(RANDOM.nextDouble() * Math.PI * 2);
			Vec3 at = base.add(out.scale(0.25)).add(0, 0.06, 0);
			Vec3 vel = out.scale(speed * (0.6 + RANDOM.nextDouble())).add(0, 0.16 + RANDOM.nextDouble() * 0.16, 0);

			if (i % 2 == 0 || state == null) {
				KineticKit.pebble(at, vel, grit, 0.05f + RANDOM.nextFloat() * 0.06f, 26 + RANDOM.nextInt(12));
			} else {
				KineticKit.blockBit(level, state, at, vel);
			}
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

			// Streaks of air torn off the body along the leap.
			Vec3 motion = entity.getDeltaMovement();
			Vec3 behind = entity.position().add(0, entity.getBbHeight() * 0.45, 0).subtract(motion.scale(0.6));

			for (int i = 0, n = Vfx.count(2); i < n; i++) {
				Vec3 at = behind.add(KineticKit.spread(0.45), KineticKit.spread(0.7), KineticKit.spread(0.45));
				AirStreakParticle streak = KineticKit.streak(at, motion.scale(0.5 + RANDOM.nextDouble() * 0.2), 0.22f, 0.5f, 6);

				if (streak != null) {
					streak.stretch(0.8f).drag(0.8f);
				}
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
