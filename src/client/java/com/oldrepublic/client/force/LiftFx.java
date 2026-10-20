package com.oldrepublic.client.force;

import static com.oldrepublic.client.force.ForceFxUtil.RANDOM;
import static com.oldrepublic.client.force.ForceFxUtil.chest;
import static com.oldrepublic.client.force.ForceFxUtil.dustColor;
import static com.oldrepublic.client.force.ForceFxUtil.groundBelow;
import static com.oldrepublic.client.force.ForceFxUtil.handPos;
import static com.oldrepublic.client.force.ForceFxUtil.jitter;
import static com.oldrepublic.client.force.ForceFxUtil.jitterScalar;
import static com.oldrepublic.client.force.ForceFxUtil.pitchVariance;
import static com.oldrepublic.client.force.ForceFxUtil.play;
import static com.oldrepublic.client.force.ForceFxUtil.ripple;
import static com.oldrepublic.client.force.ForceFxUtil.spawn;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.force.particle.ForceParticle;
import com.oldrepublic.client.fx.CameraShake;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.force.ForceEffects;
import com.oldrepublic.force.ForceParticles;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
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
 * <li><b>Start</b> ({@code force_lift} 0): the free-arm pose {@code LIFT} (palm up, trembling), a
 * puff of bent air at the raised palm, {@code force.lift.start}, then the {@code force.lift.loop}
 * attached to the pose, so it stops with it: on the end event, the watchdog (the hold the server
 * sent plus slack), the caster's death or removal, a level change or a disconnect.</li>
 * <li><b>Held target</b> ({@code force_lift_target}): a column of faint bent-air
 * {@code lift_shimmer} wisps between the ground and the target's feet (staggered heights, never a
 * disc), with pebbles and dust falling from its feet; the target flails (the humanoid model, through
 * {@link #flail}). At most 30 live particles at Standard.</li>
 * <li><b>Slam</b> (2): irregular dust puffs of varied height with gaps and the landing block's
 * debris, {@code force.lift.slam}, and a small camera shake close by.</li>
 * <li><b>Hurl</b> (3): a bent-air streak along the throw, {@code force.lift.hurl}.</li>
 * </ul>
 */
final class LiftFx {
	/** Ticks past the hold the server sent before a lost end event drops the pose and loop. */
	private static final int WATCHDOG_SLACK = 30;
	/** The own prediction: the longest hold (Suspend, 100 ticks) plus slack. */
	static final int PREDICT_WATCHDOG = 100 + WATCHDOG_SLACK;
	private static final int PALM_INTERVAL = 6;
	private static final int SHIMMER_INTERVAL = 2;
	private static final int PEBBLE_INTERVAL = 5;
	private static final int DUST_INTERVAL = 3;
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
						palmAir(caster, 3);
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
			return;
		}

		long now = PowerAnimations.ticks();
		LIFTED.put(payload.entityId(), new Held(now, now + Math.round(payload.intensity() * 20f) + WATCHDOG_SLACK));
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
					palmAir(caster, 1);
				}
			}
		}

		Iterator<Map.Entry<Integer, Held>> it = LIFTED.entrySet().iterator();

		while (it.hasNext()) {
			Map.Entry<Integer, Held> entry = it.next();
			Entity target = level.getEntity(entry.getKey());

			if (now > entry.getValue().until() || target == null || target.isRemoved() || !target.isAlive()) {
				it.remove();
				continue;
			}

			if (Vfx.inRange(target.position())) {
				heldTick(level, target, now);
			}
		}
	}

	/** Bent air rising off the raised palm. */
	private static void palmAir(LivingEntity caster, int count) {
		Vec3 palm = handPos(caster, ForceFxUtil.freeArmIsLeft(caster), 0.8f).add(0, 0.15, 0);

		for (int i = 0, n = Vfx.count(count); i < n; i++) {
			ripple(palm.add(jitter(0.06)), new Vec3(0, 0.02, 0).add(caster.getViewVector(1f).scale(0.015)), 0.08f, 0.3f, 0.09f, 8);
		}
	}

	/**
	 * Under a held target: shimmer wisps at staggered heights of the column between the ground and its
	 * feet, pebbles dropping off its feet and a little dust sifting down. About 8 + 4 + 8 live particles.
	 */
	private static void heldTick(ClientLevel level, Entity target, long now) {
		AABB box = target.getBoundingBox();
		Vec3 feet = target.position();
		BlockPos ground = groundBelow(level, feet.subtract(0, 0.5, 0));
		double floor = ground != null ? ground.getY() + 1.0 : feet.y - MAX_COLUMN;
		double column = Math.max(0.3, Math.min(MAX_COLUMN, feet.y - floor));
		double halfX = box.getXsize() * 0.3;
		double halfZ = box.getZsize() * 0.3;

		if (now % SHIMMER_INTERVAL == 0) {
			for (int i = 0, n = Vfx.count(1); i < n; i++) {
				Vec3 at = new Vec3(feet.x + jitterScalar(halfX), feet.y - RANDOM.nextDouble() * column, feet.z + jitterScalar(halfZ));
				spawn(ForceParticles.LIFT_SHIMMER, at, new Vec3(jitterScalar(0.004), 0.02 + RANDOM.nextDouble() * 0.015, jitterScalar(0.004)));
			}
		}

		float[] rgb = ground != null ? dustColor(level, ground)
				: new float[] {ForceFxUtil.DEFAULT_DUST_R, ForceFxUtil.DEFAULT_DUST_G, ForceFxUtil.DEFAULT_DUST_B};

		if (now % PEBBLE_INTERVAL == 0) {
			for (int i = 0, n = Vfx.count(1); i < n; i++) {
				ForceParticle p = spawn(ForceParticles.GATHER_DUST, new Vec3(feet.x + jitterScalar(halfX), feet.y, feet.z + jitterScalar(halfZ)),
						new Vec3(jitterScalar(0.01), -0.02, jitterScalar(0.01)));

				if (p != null) {
					// Small, dark and heavy: a pebble shaken loose.
					p.color(rgb[0] * 0.7f, rgb[1] * 0.7f, rgb[2] * 0.7f).size(0.05f, 0.045f).alpha(0.9f).life(20).drag(0.98f).fall(1f, true);
				}
			}
		}

		if (now % DUST_INTERVAL == 0) {
			for (int i = 0, n = Vfx.count(1); i < n; i++) {
				ForceParticle p = spawn(ForceParticles.GATHER_DUST, new Vec3(feet.x + jitterScalar(halfX * 1.4), feet.y + 0.05, feet.z + jitterScalar(halfZ * 1.4)),
						new Vec3(jitterScalar(0.006), -0.012, jitterScalar(0.006)));

				if (p != null) {
					p.color(rgb[0], rgb[1], rgb[2]).size(0.06f, 0.11f).alpha(0.4f).life(22 + RANDOM.nextInt(6)).drag(0.95f).fall(0.02f, false);
				}
			}
		}
	}

	/** Slam: pos = the target's feet, intensity = damage. Irregular dust puffs and the landing block's debris. */
	private static void slam(Minecraft mc, ClientLevel level, EffectEventPayload payload) {
		Vec3 pos = payload.pos();
		LIFTED.remove(payload.entityId());
		play(level, pos, ModSounds.FORCE_LIFT_SLAM, 0.9f, pitchVariance(0.05f));

		if (mc.player != null && mc.player.position().distanceToSqr(pos) < SHAKE_RANGE * SHAKE_RANGE) {
			CameraShake.add(SLAM_SHAKE);
		}

		if (!Vfx.inRange(pos)) {
			return;
		}

		BlockPos ground = groundBelow(level, pos.add(0, 0.5, 0));
		BlockState state = ground != null ? level.getBlockState(ground) : null;
		float[] rgb = ground != null ? dustColor(level, ground)
				: new float[] {ForceFxUtil.DEFAULT_DUST_R, ForceFxUtil.DEFAULT_DUST_G, ForceFxUtil.DEFAULT_DUST_B};

		// Puffs at random angles with random reach and height: gaps, never a ring.
		for (int i = 0, n = Vfx.count(10); i < n; i++) {
			double angle = RANDOM.nextDouble() * Math.PI * 2;
			Vec3 out = new Vec3(Math.cos(angle), 0, Math.sin(angle));
			double speed = 0.06 + RANDOM.nextDouble() * 0.16;
			ForceParticle p = spawn(ForceParticles.GATHER_DUST, pos.add(out.scale(0.2 + RANDOM.nextDouble() * 0.4)).add(0, 0.05 + RANDOM.nextDouble() * 0.35, 0),
					out.scale(speed).add(0, 0.02 + RANDOM.nextDouble() * 0.06, 0));

			if (p != null) {
				p.color(rgb[0], rgb[1], rgb[2]).size(0.1f + RANDOM.nextFloat() * 0.06f, 0.16f).alpha(0.5f).life(22 + RANDOM.nextInt(12)).drag(0.88f).fall(0.01f, true);
			}
		}

		if (state != null && !state.isAir()) {
			BlockParticleOption debris = new BlockParticleOption(ParticleTypes.BLOCK, state);

			for (int i = 0, n = Vfx.count(8); i < n; i++) {
				double angle = RANDOM.nextDouble() * Math.PI * 2;
				level.addParticle(debris, pos.x + Math.cos(angle) * 0.3, pos.y + 0.1, pos.z + Math.sin(angle) * 0.3, Math.cos(angle) * 0.2,
						0.18 + RANDOM.nextDouble() * 0.15, Math.sin(angle) * 0.2);
			}
		}
	}

	/** Hurl: pos = the target's centre, dir = the throw, intensity = distance. A bent-air streak along it. */
	private static void hurl(ClientLevel level, EffectEventPayload payload) {
		Vec3 pos = payload.pos();
		LIFTED.remove(payload.entityId());
		play(level, pos, ModSounds.FORCE_LIFT_HURL, 0.9f, pitchVariance(0.04f));

		if (!Vfx.inRange(pos) || payload.dir().lengthSqr() < 1.0e-4) {
			return;
		}

		Vec3 dir = payload.dir().normalize();
		double distance = Math.max(2, payload.intensity());
		int life = 10;

		for (int i = 0, n = Vfx.count(8); i < n; i++) {
			Vec3 at = pos.add(dir.scale(-0.4 + RANDOM.nextDouble() * 0.8)).add(jitter(0.25));
			ForceParticle p = spawn(ForceParticles.PUSH_STREAK, at, dir.scale(distance / life * (0.7 + RANDOM.nextDouble() * 0.3)));

			if (p != null) {
				p.alpha(0.3f).size(0.22f, 0.3f).life(life);
			}
		}

		ripple(pos, dir.scale(0.2), 0.3f, 0.8f, 0.14f, 8);
	}

	/** Forgets every held target (level change, disconnect); poses and loops end with PowerAnimations. */
	static void clear() {
		LIFTED.clear();
	}

	private LiftFx() {
	}
}
