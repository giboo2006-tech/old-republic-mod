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
import com.oldrepublic.client.force.kinetic.PressureFront;
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
 * Force Jump visuals and sounds (spec 8.2). Crouched and gathering: dust and grit creep in along the ground to the feet, and
 * every few ticks the air underfoot draws in. The take-off is a compressed burst: air blasted down and out of the ground
 * throws dust out low in patches, flings grit and the ground's own bits, and streaks of air rush down past the legs; for a
 * moment streaks of air trail the leap. The landing throws up dust and chips by the fall and the surface (sand bursts,
 * stone chips, snow powder), and Crushing Descent hits like a blast: a front of pressure runs out over the ground from the
 * point of impact, tearing dust and grit off it as it passes. The Soaring Leap glide is the one piece of movement here.
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
		KineticRender.add(new PressureFront(base.add(0, 0.1, 0), new Vec3(0, 1, 0)).spread(88f).radius(0.3f, 1.6f + 1.4f * charge).life(7).burst(2.6f)
				.strength(0.45f + 0.4f * charge).light(KineticKit.light(level, pos)));

		// Air blasted down and out of the ground: dust thrown out low in patches, with gaps.
		burst(level, base, dust, grit, state, Vfx.count(Math.round(8 + 10 * charge)), Vfx.count(Math.round(3 + 6 * charge)), 0.1 + 0.16 * charge, 1f);

		// Streaks of air rushing down past the legs.
		for (int i = 0, n = Vfx.count(Math.round(3 + 5 * charge)); i < n; i++) {
			Vec3 out = KineticKit.flat(RANDOM.nextDouble() * Math.PI * 2);
			Vec3 at = base.add(out.scale(0.25 + RANDOM.nextDouble() * 0.3)).add(0, 0.6 + RANDOM.nextDouble() * 0.8, 0);
			AirStreakParticle streak = KineticKit.streak(at, out.scale(0.15).add(0, -0.45 - RANDOM.nextDouble() * 0.3, 0), 0.14f, 0.5f, 6);

			if (streak != null) {
				streak.drag(0.8f);
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
			case ForceEffects.SURFACE_SAND -> 14;
			case ForceEffects.SURFACE_SNOW -> 16;
			case ForceEffects.SURFACE_STONE -> 4;
			default -> 8;
		};
		int chips = surface == ForceEffects.SURFACE_STONE ? 10 : 4;
		double rise = surface == ForceEffects.SURFACE_SAND ? 0.1 : surface == ForceEffects.SURFACE_SNOW ? 0.05 : 0.03;

		if (surface == ForceEffects.SURFACE_SNOW) {
			dust = new float[] {0.95f, 0.96f, 0.98f};
		}

		float[] grit = surface == ForceEffects.SURFACE_SNOW ? new float[] {0.86f, 0.88f, 0.92f} : KineticKit.gritColor(dust);
		int n = Vfx.count(Math.round(clouds * strength) + 2);
		int m = Vfx.count(Math.round(chips * strength) + 1);
		burst(level, base, dust, grit, state, n, m, 0.06 + 0.12 * strength, (float) rise / 0.03f * 0.5f);

		if (heavy) {
			KineticRender.add(new PressureFront(base.add(0, 0.08, 0), new Vec3(0, 1, 0)).spread(88f).radius(0.3f, 1.2f + 1.2f * strength).life(6).burst(2.4f)
					.strength(0.35f + 0.3f * strength).light(KineticKit.light(level, pos)));
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
		float light = KineticKit.light(level, pos);
		// The blast: a front of pressure running out over the ground, low and wide, and a fainter one close behind.
		PressureFront front = new PressureFront(base.add(0, 0.15, 0), new Vec3(0, 1, 0)).spread(88f).radius(0.4f, 4.2f).life(10).burst(2.4f).strength(1f)
				.light(light);
		KineticRender.add(front);
		KineticRender.later(2, l -> KineticRender.add(new PressureFront(base.add(0, 0.15, 0), new Vec3(0, 1, 0)).spread(80f).radius(0.3f, 2.8f).life(9)
				.burst(2f).strength(0.45f).light(light).thickness(0.04f)));

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
			float r = Math.min(3.6f, front.radiusAt(tick[0]));

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

	/** Anticipation: dust and grit creep in along the ground to the feet, and every few ticks the air underfoot draws in. */
	private static void gatherTick(ClientLevel level, LivingEntity entity, float age) {
		Vec3 feet = entity.position();

		for (int i = 0, n = Vfx.count(2); i < n; i++) {
			double angle = RANDOM.nextDouble() * Math.PI * 2;
			double distance = 1.0 + RANDOM.nextDouble() * 1.3;
			Vec3 around = feet.add(Math.cos(angle) * distance, 0.5, Math.sin(angle) * distance);
			BlockPos ground = KineticKit.ground(level, around, 3);

			if (ground == null) {
				continue;
			}

			Vec3 start = new Vec3(around.x, ground.getY() + 1.08, around.z);
			Vec3 to = new Vec3(feet.x, ground.getY() + 1.08, feet.z);
			float[] dust = KineticKit.dustColor(level, ground);
			DustCloudParticle cloud = KineticKit.dust(start, to.subtract(start).scale(1 / 14.0), dust, 0.18f, 0.6f, 0.45f, 14);

			if (cloud != null) {
				cloud.hold(0.5f).drag(1f);
			}

			if (i == 0 && ((int) age) % 4 == 0) {
				KineticKit.pebble(start, to.subtract(start).scale(1 / 12.0).add(0, 0.12, 0), KineticKit.gritColor(dust), 0.04f + RANDOM.nextFloat() * 0.03f, 14);
			}
		}

		if (((int) age) % 6 == 0) {
			// The air underfoot drawn in: low streaks closing on the feet.
			for (int i = 0, n = Vfx.count(3); i < n; i++) {
				Vec3 out = KineticKit.flat(RANDOM.nextDouble() * Math.PI * 2);
				Vec3 from = feet.add(out.scale(1.1 + RANDOM.nextDouble() * 0.6)).add(0, 0.12 + RANDOM.nextDouble() * 0.25, 0);
				AirStreakParticle streak = KineticKit.streak(from, out.scale(-0.2), 0.12f, 0.4f, 6);

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

		for (int i = 0; i < clouds; i++) {
			double angle = RANDOM.nextDouble() * Math.PI * 2;

			if (KineticNoise.at((float) Math.cos(angle) * 2.2f + seed, (float) Math.sin(angle) * 2.2f, 0.5f) < 0.33f) {
				// A gap: the dust bursts out in patches, never an even ring.
				angle += Math.PI * (0.25 + RANDOM.nextDouble() * 0.5);
			}

			Vec3 out = KineticKit.flat(angle);
			Vec3 at = base.add(out.scale(0.2 + RANDOM.nextDouble() * 0.35)).add(0, 0.12 + RANDOM.nextDouble() * 0.2, 0);
			Vec3 vel = out.scale(speed * (0.6 + RANDOM.nextDouble() * 0.8)).add(0, (0.02 + RANDOM.nextDouble() * 0.05) * rise, 0);
			DustCloudParticle cloud = KineticKit.dust(at, vel, dust, 0.22f + RANDOM.nextFloat() * 0.14f, 2.7f, 0.55f, 28 + RANDOM.nextInt(18));

			if (cloud != null) {
				cloud.hold(0.2f).lift(-0.002f).drag(0.87f);
			}
		}

		for (int i = 0; i < chips; i++) {
			Vec3 out = KineticKit.flat(RANDOM.nextDouble() * Math.PI * 2);
			Vec3 at = base.add(out.scale(0.25)).add(0, 0.06, 0);
			Vec3 vel = out.scale(speed * (0.6 + RANDOM.nextDouble())).add(0, 0.16 + RANDOM.nextDouble() * 0.16, 0);

			if (i % 2 == 0 || state == null) {
				KineticKit.pebble(at, vel, grit, 0.045f + RANDOM.nextFloat() * 0.045f, 26 + RANDOM.nextInt(12));
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
			AirStreakParticle streak = KineticKit.streak(behind.add(KineticKit.jitter(0.3)), motion.scale(0.6), 0.16f, 0.45f, 6);

			if (streak != null) {
				streak.drag(0.8f);
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
