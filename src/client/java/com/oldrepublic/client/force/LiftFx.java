package com.oldrepublic.client.force;

import static com.oldrepublic.client.force.ForceFxUtil.RANDOM;
import static com.oldrepublic.client.force.ForceFxUtil.chest;
import static com.oldrepublic.client.force.ForceFxUtil.pitchVariance;
import static com.oldrepublic.client.force.ForceFxUtil.play;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.force.kinetic.AirStreakParticle;
import com.oldrepublic.client.force.kinetic.DustCloudParticle;
import com.oldrepublic.client.force.kinetic.KineticKit;
import com.oldrepublic.client.force.kinetic.KineticNoise;
import com.oldrepublic.client.force.kinetic.KineticRender;
import com.oldrepublic.client.force.kinetic.KineticTrails;
import com.oldrepublic.client.force.kinetic.PebbleParticle;
import com.oldrepublic.client.force.kinetic.PressureFront;
import com.oldrepublic.client.fx.CameraShake;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.force.ForceEffects;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Telekinetic Lift visuals and sounds (spec 10.2, 13.1, 13.3).
 *
 * <ul>
 * <li><b>Start</b> ({@code force_lift} 0): the free-arm pose {@code LIFT} (palm up, trembling), dust whirled up off the
 * raised palm, {@code force.lift.start}, then the {@code force.lift.loop} attached to the pose, so it stops with it: on the
 * end event, the watchdog (the hold the server sent plus slack), the caster's death or removal, a level change or a
 * disconnect.</li>
 * <li><b>Held target</b> ({@code force_lift_target}): the ground under it is torn up as it leaves (dust pushed out low in
 * patches, grit flung up), and pebbles and clods rise with it and hang trembling round its legs for the hold. Under it an
 * updraft: dust drawn up off the ground spirals up toward its feet and streaks of air rise through the column and on past
 * the body, dark grit caught in the hold circles the body slowly, grit drops from its feet and a little dust sifts down.
 * The target flails (the humanoid model, through {@link #flail}). Never a disc or a glow.</li>
 * <li><b>Let go</b>: the hanging pebbles fall.</li>
 * <li><b>Slam</b> (2): dust bursts out of the impact in patches with gaps, grit and the landing block's bits fly, the air
 * the body drove down bursts out low over the ground in streaks, {@code force.lift.slam}, and a small camera shake close
 * by.</li>
 * <li><b>Hurl</b> (3): air rushes after the throw (a front and streaks along it), the hanging pebbles are flung with it
 * and the creature trails dust, {@code force.lift.hurl}.</li>
 * </ul>
 */
final class LiftFx {
	/** Ticks past the hold the server sent before a lost end event drops the pose and loop. */
	private static final int WATCHDOG_SLACK = 30;
	/** The own prediction: the longest hold (Suspend, 100 ticks) plus slack. */
	static final int PREDICT_WATCHDOG = 100 + WATCHDOG_SLACK;
	private static final int PALM_INTERVAL = 6;
	private static final int UPDRAFT_INTERVAL = 2;
	private static final int PEBBLE_INTERVAL = 3;
	private static final int DUST_INTERVAL = 4;
	/** Pebbles that hang round one lifted creature at most. */
	private static final int MAX_HANGING = 12;
	/** Ticks the flail takes to reach full swing after the lift starts. */
	private static final float FLAIL_IN = 6f;
	/** The shimmer column is at most this tall when no ground is found below. */
	private static final double MAX_COLUMN = 3.0;
	private static final float SLAM_SHAKE = 0.1f;
	private static final double SHAKE_RANGE = 5;

	/** A target held up: when it started and the watchdog end, in client ticks. */
	private record Held(long start, long until) {
	}

	private static final Map<Integer, Held> LIFTED = new HashMap<>();
	/** The pebbles hanging round each lifted creature. */
	private static final Map<Integer, List<PebbleParticle>> HANGING = new HashMap<>();

	/** {@code force_lift}: start, end, slam or hurl. Variant = packed caster id and phase. */
	static void onLift(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity target) {
		int phase = ForceEffects.valueOf(payload.variant());
		Entity casterEntity = level.getEntity(ForceEffects.casterOf(payload.variant()));
		LivingEntity caster = casterEntity instanceof LivingEntity living ? living : null;

		switch (phase) {
			case ForceEffects.LIFT_START -> {
				if (caster != null) {
					start(caster, Math.round(payload.intensity() * 20f) + WATCHDOG_SLACK, false);
					play(level, chest(caster), ModSounds.FORCE_LIFT_START, 0.8f, pitchVariance(0.04f));
					PowerAnimations.loop(caster, PowerAnimations.Kind.LIFT, ModSounds.FORCE_LIFT_LOOP, 0.55f, 1f);

					if (Vfx.inRange(caster.position())) {
						palmAir(level, caster, 3);
					}
				}
			}
			case ForceEffects.LIFT_END -> {
				if (caster != null) {
					PowerAnimations.stop(caster.getId(), PowerAnimations.Kind.LIFT);
				}
			}
			case ForceEffects.LIFT_SLAM -> slam(mc, level, payload);
			case ForceEffects.LIFT_HURL -> hurl(level, payload);
			default -> {
			}
		}
	}

	/** Starts the pose: from the start event, or as the own prediction ({@code predicted}). */
	static void start(LivingEntity caster, int watchdog, boolean predicted) {
		if (predicted) {
			PowerAnimations.startPredicted(caster.getId(), PowerAnimations.Kind.LIFT, -1, watchdog);
		} else {
			PowerAnimations.start(caster.getId(), PowerAnimations.Kind.LIFT, -1, watchdog);
		}
	}

	/** {@code force_lift_target}: intensity = hold seconds while lifted, 0 when let go. Entity = target. */
	static void onTarget(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity target) {
		if (payload.intensity() <= 0f) {
			LIFTED.remove(payload.entityId());
			letGo(payload.entityId(), null);
			return;
		}

		long now = PowerAnimations.ticks();
		int hold = Math.round(payload.intensity() * 20f);
		LIFTED.put(payload.entityId(), new Held(now, now + hold + WATCHDOG_SLACK));

		if (target != null && Vfx.inRange(target.position())) {
			tearUp(level, target, hold);
		}
	}

	/**
	 * The ground under a creature as it is lifted off it: dust pushed out low in patches, grit flung up, and pebbles and
	 * clods that rise with it and hang trembling round its legs for the {@code hold}.
	 */
	private static void tearUp(ClientLevel level, Entity target, int hold) {
		Vec3 feet = target.position();
		BlockPos ground = KineticKit.ground(level, feet.add(0, 0.4, 0), 3);

		if (ground == null) {
			return;
		}

		float[] dust = KineticKit.dustColor(level, ground);
		float[] grit = KineticKit.gritColor(dust);
		double top = ground.getY() + 1.0;
		float seed = RANDOM.nextFloat() * 40f;

		for (int i = 0, n = Vfx.count(8); i < n; i++) {
			double angle = RANDOM.nextDouble() * Math.PI * 2;

			if (KineticNoise.at((float) Math.cos(angle) * 2.2f + seed, (float) Math.sin(angle) * 2.2f, 0.5f) < 0.35f) {
				continue;
			}

			Vec3 out = KineticKit.flat(angle);
			Vec3 at = new Vec3(feet.x + out.x * 0.35, top + 0.12, feet.z + out.z * 0.35);
			DustCloudParticle cloud = KineticKit.dust(at, out.scale(0.08 + RANDOM.nextDouble() * 0.1).add(0, 0.015, 0), KineticKit.airDust(dust), 0.25f, 2.6f,
					0.5f, 30 + RANDOM.nextInt(16));

			if (cloud != null) {
				cloud.hold(0.25f).lift(-0.002f).drag(0.88f);
			}
		}

		List<PebbleParticle> hanging = HANGING.computeIfAbsent(target.getId(), id -> new ArrayList<>());

		for (int i = 0, n = Math.min(MAX_HANGING - hanging.size(), Vfx.count(MAX_HANGING)); i < n; i++) {
			Vec3 out = KineticKit.flat(RANDOM.nextDouble() * Math.PI * 2);
			double reach = 0.3 + RANDOM.nextDouble() * 0.7;
			Vec3 at = new Vec3(feet.x + out.x * reach, top + 0.05, feet.z + out.z * reach);
			PebbleParticle pebble = KineticKit.pebble(at, Vec3.ZERO, grit, 0.07f + RANDOM.nextFloat() * 0.09f, hold + 60);

			if (pebble != null) {
				// They rise with the creature, the nearer ones higher, some up beside its body, and hang there.
				pebble.hover(hold + 10, 0.7 + RANDOM.nextDouble() * 2.2 * (1.3 - reach));
				hanging.add(pebble);
			}
		}

		for (int i = 0, n = Vfx.count(4); i < n; i++) {
			Vec3 out = KineticKit.flat(RANDOM.nextDouble() * Math.PI * 2);
			KineticKit.blockBit(level, level.getBlockState(ground), new Vec3(feet.x + out.x * 0.3, top + 0.05, feet.z + out.z * 0.3),
					out.scale(0.08).add(0, 0.22, 0));
		}
	}

	/** Lets go of the pebbles hanging round a creature: flung along {@code dir}, or just dropped when it is null. */
	private static void letGo(int id, @Nullable Vec3 dir) {
		List<PebbleParticle> hanging = HANGING.remove(id);

		if (hanging == null) {
			return;
		}

		for (PebbleParticle pebble : hanging) {
			if (dir == null) {
				pebble.drop(KineticKit.spread(0.03), -0.05, KineticKit.spread(0.03));
			} else {
				double speed = 0.3 + RANDOM.nextDouble() * 0.3;
				pebble.drop(dir.x * speed + KineticKit.spread(0.06), dir.y * speed + 0.05, dir.z * speed + KineticKit.spread(0.06));
			}
		}
	}

	/** How far {@code entityId} flails: 0 when not lifted, rising to 1 over the first ticks. */
	static float flail(int entityId, float partialTick) {
		if (LIFTED.isEmpty()) {
			return 0f;
		}

		Held held = LIFTED.get(entityId);
		return held == null ? 0f : Mth.clamp((PowerAnimations.ticks() - held.start() + partialTick) / FLAIL_IN, 0f, 1f);
	}

	/** Palm air for every lifting caster, and the column, pebbles and dust under every held target. */
	static void tick(ClientLevel level) {
		long now = PowerAnimations.ticks();

		if (now % PALM_INTERVAL == 0) {
			for (int id : PowerAnimations.active(PowerAnimations.Kind.LIFT)) {
				if (level.getEntity(id) instanceof LivingEntity caster && Vfx.inRange(caster.position())) {
					palmAir(level, caster, 1);
				}
			}
		}

		Iterator<Map.Entry<Integer, Held>> it = LIFTED.entrySet().iterator();

		while (it.hasNext()) {
			Map.Entry<Integer, Held> entry = it.next();
			Entity target = level.getEntity(entry.getKey());

			if (now > entry.getValue().until() || target == null || target.isRemoved() || !target.isAlive()) {
				it.remove();
				letGo(entry.getKey(), null);
				continue;
			}

			if (Vfx.inRange(target.position())) {
				heldTick(level, target, now);
			}
		}
	}

	/** Dust whirled up off the raised palm. */
	private static void palmAir(ClientLevel level, LivingEntity caster, int count) {
		Vec3 palm = KineticKit.hand(caster, 0.8f, 1f).add(0, 0.12, 0);
		BlockPos ground = KineticKit.ground(level, caster.position().add(0, 0.4, 0), 3);
		float[] dust = KineticKit.airDust(KineticKit.dustColor(level, ground));

		for (int i = 0, n = Vfx.count(count * 2); i < n; i++) {
			Vec3 at = palm.add(KineticKit.flat(RANDOM.nextDouble() * Math.PI * 2).scale(0.25 + RANDOM.nextDouble() * 0.2));
			DustCloudParticle mote = KineticKit.dust(at, Vec3.ZERO, dust, 0.05f + RANDOM.nextFloat() * 0.04f, 0.8f, 0.5f, 14 + RANDOM.nextInt(6));

			if (mote != null) {
				// Circles the palm and rises off it.
				mote.swirl(palm.add(0, 0.6, 0), 0.3f, 0.04f).hold(0.4f);
			}
		}
	}

	/**
	 * Under a held creature: an updraft of dust drawn up off the ground and spiralling up toward its feet, streaks of air
	 * rising through the column between the ground and its feet and on past its body, dark grit circling the body, grit
	 * dropping off its feet and a little dust sifting down.
	 */
	private static void heldTick(ClientLevel level, Entity target, long now) {
		AABB box = target.getBoundingBox();
		Vec3 feet = target.position();
		BlockPos ground = KineticKit.ground(level, feet.subtract(0, 0.5, 0), 4);
		double floor = ground != null ? ground.getY() + 1.0 : feet.y - MAX_COLUMN;
		double column = Math.max(0.3, Math.min(MAX_COLUMN, feet.y - floor));
		double halfX = box.getXsize() * 0.45;
		double halfZ = box.getZsize() * 0.45;
		float[] dust = KineticKit.dustColor(level, ground);

		if (now % UPDRAFT_INTERVAL == 0) {
			float[] haze = KineticKit.airDust(dust);
			// The column turns one way for one creature, the other for the next.
			float turn = (target.getId() & 1) == 0 ? 0.12f : -0.12f;
			Vec3 under = new Vec3(feet.x, feet.y - 0.1, feet.z);

			for (int i = 0, n = Vfx.count(2); i < n; i++) {
				Vec3 out = KineticKit.flat(RANDOM.nextDouble() * Math.PI * 2);
				double reach = 0.35 + RANDOM.nextDouble() * 0.6;

				if (ground != null) {
					// Drawn up off the ground and spiralling in and up toward the feet.
					Vec3 at = new Vec3(feet.x + out.x * reach, floor + 0.12, feet.z + out.z * reach);
					DustCloudParticle cloud = KineticKit.dust(at, Vec3.ZERO, haze, 0.26f + RANDOM.nextFloat() * 0.12f, 2f, 0.5f + RANDOM.nextFloat() * 0.12f,
							22 + RANDOM.nextInt(7));

					if (cloud != null) {
						cloud.swirl(under, turn, 0.045f).hold(0.35f);
					}
				}

				// Air rising through the column and on up past the body.
				double up = RANDOM.nextDouble() * (column + target.getBbHeight() * 0.6);
				Vec3 from = new Vec3(feet.x + out.x * (reach * 0.6 + (up > column ? 0.3 : 0)), floor + up, feet.z + out.z * (reach * 0.6 + (up > column ? 0.3 : 0)));
				AirStreakParticle streak = KineticKit.streak(from, new Vec3(0, 0.2 + RANDOM.nextDouble() * 0.1, 0), 0.3f, 0.6f, 7);

				if (streak != null) {
					streak.stretch(1f).drag(0.95f);
				}
			}

			// Dark grit caught in the hold, circling the body slowly at its own height.
			for (int i = 0, n = Vfx.count(1); i < n; i++) {
				Vec3 axis = new Vec3(feet.x, feet.y + target.getBbHeight() * (0.1 + RANDOM.nextDouble() * 0.8), feet.z);
				Vec3 at = axis.add(KineticKit.flat(RANDOM.nextDouble() * Math.PI * 2).scale(0.55 + RANDOM.nextDouble() * 0.35));
				DustCloudParticle mote = KineticKit.dust(at, Vec3.ZERO, KineticKit.gritColor(dust), 0.07f + RANDOM.nextFloat() * 0.05f, 1f, 0.75f,
						20 + RANDOM.nextInt(8));

				if (mote != null) {
					mote.swirl(axis, turn * 0.8f, 0f).hold(0.6f);
				}
			}
		}

		if (now % PEBBLE_INTERVAL == 0) {
			for (int i = 0, n = Vfx.count(1); i < n; i++) {
				// Grit shaken loose from its feet.
				KineticKit.pebble(new Vec3(feet.x + KineticKit.spread(halfX), feet.y, feet.z + KineticKit.spread(halfZ)),
						new Vec3(KineticKit.spread(0.01), -0.02, KineticKit.spread(0.01)), KineticKit.gritColor(dust), 0.05f + RANDOM.nextFloat() * 0.03f, 22);
			}
		}

		if (now % DUST_INTERVAL == 0) {
			for (int i = 0, n = Vfx.count(1); i < n; i++) {
				DustCloudParticle cloud = KineticKit.dust(new Vec3(feet.x + KineticKit.spread(halfX), feet.y + 0.05, feet.z + KineticKit.spread(halfZ)),
						new Vec3(KineticKit.spread(0.006), -0.015, KineticKit.spread(0.006)), dust, 0.1f, 2.4f, 0.42f, 24 + RANDOM.nextInt(6));

				if (cloud != null) {
					cloud.drag(0.97f);
				}
			}
		}
	}

	/**
	 * Slam: pos = the target's feet, intensity = damage. Dust bursting out in patches, grit and the landing block's bits
	 * thrown up, and the air the body drove down bursting out low over the ground in streaks.
	 */
	private static void slam(Minecraft mc, ClientLevel level, EffectEventPayload payload) {
		Vec3 pos = payload.pos();
		LIFTED.remove(payload.entityId());
		letGo(payload.entityId(), null);
		play(level, pos, ModSounds.FORCE_LIFT_SLAM, 0.9f, pitchVariance(0.05f));

		if (mc.player != null && mc.player.position().distanceToSqr(pos) < SHAKE_RANGE * SHAKE_RANGE) {
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
		// Dust bursting out at random angles with random reach and height: gaps, never a ring.
		float seed = RANDOM.nextFloat() * 40f;

		for (int i = 0, n = Vfx.count(16); i < n; i++) {
			double angle = RANDOM.nextDouble() * Math.PI * 2;

			if (KineticNoise.at((float) Math.cos(angle) * 2.2f + seed, (float) Math.sin(angle) * 2.2f, 0.5f) < 0.33f) {
				angle += Math.PI * (0.3 + RANDOM.nextDouble() * 0.4);
			}

			Vec3 out = KineticKit.flat(angle);
			Vec3 at = base.add(out.scale(0.2 + RANDOM.nextDouble() * 0.4)).add(0, 0.1 + RANDOM.nextDouble() * 0.35, 0);
			Vec3 vel = out.scale(0.1 + RANDOM.nextDouble() * 0.22).add(0, 0.02 + RANDOM.nextDouble() * 0.07, 0);
			DustCloudParticle cloud = KineticKit.dust(at, vel, KineticKit.airDust(dust), 0.34f + RANDOM.nextFloat() * 0.24f, 2.8f, 0.65f, 30 + RANDOM.nextInt(20));

			if (cloud != null) {
				cloud.hold(0.2f).lift(-0.002f).drag(0.88f);
			}
		}

		for (int i = 0, n = Vfx.count(8); i < n; i++) {
			Vec3 out = KineticKit.flat(RANDOM.nextDouble() * Math.PI * 2);
			Vec3 at = base.add(out.scale(0.3)).add(0, 0.08, 0);
			Vec3 vel = out.scale(0.12 + RANDOM.nextDouble() * 0.12).add(0, 0.18 + RANDOM.nextDouble() * 0.15, 0);

			if (i % 2 == 0 || state == null) {
				KineticKit.pebble(at, vel, grit, 0.05f + RANDOM.nextFloat() * 0.05f, 30);
			} else {
				KineticKit.blockBit(level, state, at, vel);
			}
		}

		// The air the body drove down bursts out low over the ground.
		for (int i = 0, n = Vfx.count(8); i < n; i++) {
			Vec3 out = KineticKit.flat(RANDOM.nextDouble() * Math.PI * 2);
			AirStreakParticle streak = KineticKit.streak(base.add(out.scale(0.5)).add(0, 0.12 + RANDOM.nextDouble() * 0.35, 0),
					out.scale(0.45 + RANDOM.nextDouble() * 0.25), 0.26f, 0.5f, 6);

			if (streak != null) {
				streak.stretch(0.5f).drag(0.82f);
			}
		}
	}

	/** Hurl: pos = the target's centre, dir = the throw, intensity = distance. Air rushing after it, its pebbles flung along. */
	private static void hurl(ClientLevel level, EffectEventPayload payload) {
		Vec3 pos = payload.pos();
		LIFTED.remove(payload.entityId());
		play(level, pos, ModSounds.FORCE_LIFT_HURL, 0.9f, pitchVariance(0.04f));

		Vec3 dir = payload.dir().lengthSqr() < 1.0e-4 ? null : payload.dir().normalize();
		letGo(payload.entityId(), dir);

		if (!Vfx.inRange(pos) || dir == null) {
			return;
		}

		double distance = Math.max(2, payload.intensity());
		int life = 10;
		KineticRender.add(new PressureFront(pos.subtract(dir.scale(0.5)), dir).spread(55f).radius(0.3f, 2.2f).life(7).burst(2.4f).strength(0.7f)
				.light(KineticKit.light(level, pos)));

		for (int i = 0, n = Vfx.count(8); i < n; i++) {
			Vec3 at = pos.add(dir.scale(-0.4 + RANDOM.nextDouble() * 0.8)).add(KineticKit.jitter(0.3));
			AirStreakParticle streak = KineticKit.streak(at, dir.scale(distance / life * (0.7 + RANDOM.nextDouble() * 0.4)), 0.16f, 0.5f, life);

			if (streak != null) {
				streak.drag(0.9f);
			}
		}

		if (level.getEntity(payload.entityId()) instanceof LivingEntity target) {
			KineticTrails.wake(target, 16, Vfx.count(14));
		}
	}

	/** Forgets every held target (level change, disconnect); poses and loops end with PowerAnimations. */
	static void clear() {
		LIFTED.clear();
		HANGING.clear();
	}

	private LiftFx() {
	}
}
