package com.oldrepublic.client.force;

import static com.oldrepublic.client.force.ForceFxUtil.RANDOM;
import static com.oldrepublic.client.force.ForceFxUtil.chest;
import static com.oldrepublic.client.force.ForceFxUtil.jitter;
import static com.oldrepublic.client.force.ForceFxUtil.jitterScalar;
import static com.oldrepublic.client.force.ForceFxUtil.pitchVariance;
import static com.oldrepublic.client.force.ForceFxUtil.play;
import static com.oldrepublic.client.force.ForceFxUtil.spawn;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.force.kinetic.AirStreakParticle;
import com.oldrepublic.client.force.kinetic.DustCloudParticle;
import com.oldrepublic.client.force.kinetic.KineticKit;
import com.oldrepublic.client.force.kinetic.KineticNoise;
import com.oldrepublic.client.force.kinetic.KineticRender;
import com.oldrepublic.client.force.kinetic.KineticTrails;
import com.oldrepublic.client.force.kinetic.PressureFront;
import com.oldrepublic.client.force.particle.ForceParticle;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.force.ForceEffects;
import com.oldrepublic.force.ForceParticles;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Force Pull visuals and sounds (spec 8.1), the reverse of a push. The reach: air in front of the clawed hand is sucked in,
 * a front closing faster and faster on the palm, streaks rushing back along the reach, dust whirled into the gripping palm.
 * The target is torn loose: air slams in behind it, streaks peel off its outline and race ahead of it toward the caster,
 * the ground it stood on is ripped up in dust and grit that is dragged after it, and it ploughs a wake of dust as it is
 * hauled in; on arrival a puff of dust bursts at the caster's feet. Metal sparks on a disarm, short dust trails behind pulled
 * items. No glow outline on the target.
 */
final class PullFx {
	/** Ticks a pulled creature is followed for its wake. */
	private static final int WAKE_TICKS = 16;

	/** {@code force_pull}: the reach. Entity = caster, pos = hand, dir = look. */
	static void onCast(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		Vec3 hand = payload.pos();
		play(level, hand, ModSounds.FORCE_PULL_CAST, 0.9f, pitchVariance(0.05f));

		if (entity != null) {
			if (entity == mc.player && PowerAnimations.isActive(entity.getId(), PowerAnimations.Kind.PULL)) {
				PowerAnimations.confirm(entity.getId(), PowerAnimations.Kind.PULL);
			} else {
				PowerAnimations.start(entity.getId(), PowerAnimations.Kind.PULL, PowerAnimations.PULL_TICKS, 0);
			}
		}

		if (!Vfx.inRange(hand)) {
			return;
		}

		Vec3 dir = payload.dir().lengthSqr() > 1.0e-6 ? payload.dir().normalize() : new Vec3(0, 0, 1);
		float light = KineticKit.light(level, hand);
		// The air ahead of the hand sucked in: a front closing on the palm, gathering speed as it comes.
		KineticRender.add(new PressureFront(hand, dir).spread(50f).radius(2.8f, 0.15f).life(6).burst(0.55f).fadeFrom(0.55f).strength(0.75f)
				.light(light));

		// Streaks rushing back along the reach into the palm.
		for (int i = 0, n = Vfx.count(7); i < n; i++) {
			Vec3 from = hand.add(dir.scale(1.2 + RANDOM.nextDouble() * 2.8)).add(jitter(0.45));
			AirStreakParticle streak = KineticKit.streak(from, hand.subtract(from).scale(0.28), 0.14f, 0.55f, 6);

			if (streak != null) {
				streak.stretch(0.45f).drag(0.97f);
			}
		}

		// Dust whirled into the clawed palm as it grips.
		if (entity instanceof LivingEntity caster) {
			Vec3 palm = KineticKit.hand(caster, 0.8f, 1f);
			BlockPos ground = KineticKit.ground(level, caster.position().add(0, 0.4, 0), 3);
			float[] dust = KineticKit.airDust(KineticKit.dustColor(level, ground));

			for (int i = 0, n = Vfx.count(5); i < n; i++) {
				Vec3 at = palm.add(KineticKit.flat(RANDOM.nextDouble() * Math.PI * 2).scale(0.4 + RANDOM.nextDouble() * 0.3)).add(0, KineticKit.spread(0.25), 0);
				DustCloudParticle mote = KineticKit.dust(at, Vec3.ZERO, dust, 0.07f, 0.4f, 0.55f, 9 + RANDOM.nextInt(3));

				if (mote != null) {
					mote.swirl(palm, -0.45f, 0.2f).hold(0.5f);
				}
			}
		}
	}

	/** {@code force_pull_hit}: launched, arrived, or items. */
	static void onHit(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		switch (payload.variant()) {
			case ForceEffects.PULL_LAUNCHED -> launched(level, payload, entity);
			case ForceEffects.PULL_ARRIVED -> arrived(level, payload, entity);
			case ForceEffects.PULL_ITEMS -> items(level, payload);
			default -> {
			}
		}
	}

	private static void launched(ClientLevel level, EffectEventPayload payload, @Nullable Entity target) {
		Vec3 pos = target != null ? chest(target) : payload.pos();
		Vec3 dir = payload.dir().lengthSqr() > 1.0e-6 ? payload.dir().normalize() : Vec3.ZERO;
		float speed = Math.max(0.3f, payload.intensity());
		play(level, pos, ModSounds.FORCE_PULL_DRAG, 0.8f, 0.9f + 0.15f * Math.min(1f, speed / 1.6f));

		if (!Vfx.inRange(pos)) {
			return;
		}

		float light = KineticKit.light(level, pos);

		if (dir.lengthSqr() > 0) {
			// Air slams in behind it and shoves it toward the caster.
			KineticRender.add(new PressureFront(pos.subtract(dir.scale(0.6)), dir).spread(65f).radius(0.3f, 1.9f).life(6).burst(2.4f).strength(0.85f)
					.light(light));
		}

		// Streaks peel off its outline and race ahead of it toward the caster.
		AABB box = target != null ? target.getBoundingBox() : new AABB(pos, pos).inflate(0.4);

		for (int i = 0, n = Vfx.count(10); i < n; i++) {
			Vec3 at = new Vec3(box.minX + RANDOM.nextDouble() * box.getXsize(), box.minY + RANDOM.nextDouble() * box.getYsize(),
					box.minZ + RANDOM.nextDouble() * box.getZsize());
			AirStreakParticle streak = KineticKit.streak(at, dir.scale(speed * (1 + RANDOM.nextDouble() * 0.45)).add(jitter(0.04)), 0.16f, 0.6f,
					7 + RANDOM.nextInt(4));

			if (streak != null) {
				streak.stretch(0.4f).drag(0.9f);
			}
		}

		if (target == null) {
			return;
		}

		// The ground it stood on, ripped up and dragged after it.
		Vec3 feet = target.position();
		BlockPos ground = KineticKit.ground(level, feet.add(0, 0.4, 0), 3);

		if (ground != null) {
			float[] dust = KineticKit.dustColor(level, ground);
			float[] grit = KineticKit.gritColor(dust);
			BlockState floor = level.getBlockState(ground);
			double top = ground.getY() + 1.05;

			for (int i = 0, n = Vfx.count(6); i < n; i++) {
				Vec3 at = new Vec3(feet.x + KineticKit.spread(0.4), top + 0.1, feet.z + KineticKit.spread(0.4));
				Vec3 vel = dir.scale(speed * (0.12 + RANDOM.nextDouble() * 0.15)).add(KineticKit.jitter(0.04)).add(0, 0.05 + RANDOM.nextDouble() * 0.05, 0);
				DustCloudParticle cloud = KineticKit.dust(at, vel, KineticKit.airDust(dust), 0.24f + RANDOM.nextFloat() * 0.12f, 2.6f, 0.52f, 30 + RANDOM.nextInt(16));

				if (cloud != null) {
					cloud.lift(-0.002f);
				}
			}

			for (int i = 0, n = Vfx.count(6); i < n; i++) {
				Vec3 at = new Vec3(feet.x + KineticKit.spread(0.35), top + 0.05, feet.z + KineticKit.spread(0.35));
				Vec3 vel = dir.scale(speed * (0.35 + RANDOM.nextDouble() * 0.35)).add(KineticKit.jitter(0.05)).add(0, 0.18 + RANDOM.nextDouble() * 0.16, 0);
				KineticKit.pebble(at, vel, grit, 0.045f + RANDOM.nextFloat() * 0.045f, 28 + RANDOM.nextInt(12));
			}

			for (int i = 0, n = Vfx.count(4); i < n; i++) {
				Vec3 at = new Vec3(feet.x + KineticKit.spread(0.4), top, feet.z + KineticKit.spread(0.4));
				KineticKit.blockBit(level, floor, at, dir.scale(0.15).add(0, 0.15, 0));
			}
		}

		KineticTrails.wake(target, WAKE_TICKS, Vfx.count(16));
	}

	private static void arrived(ClientLevel level, EffectEventPayload payload, @Nullable Entity target) {
		Vec3 feet = payload.pos();
		play(level, feet, ModSounds.FORCE_PULL_CATCH, 0.8f, pitchVariance(0.06f));

		if (!Vfx.inRange(feet)) {
			return;
		}

		Vec3 landing = target != null ? target.position() : feet;
		BlockPos ground = KineticKit.ground(level, landing.add(0, 0.5, 0), 3);
		float[] dust = KineticKit.dustColor(level, ground);
		double top = ground != null ? ground.getY() + 1.08 : landing.y + 0.08;
		// The way it came: the dust it ploughed up runs on past it as it stops.
		Vec3 moving = target != null ? target.getDeltaMovement() : Vec3.ZERO;
		Vec3 along = moving.x * moving.x + moving.z * moving.z > 1.0e-4 ? KineticKit.horizontal(moving) : Vec3.ZERO;
		float seed = RANDOM.nextFloat() * 40f;

		// A puff of dust bursting from where it lands: puffs at random angles with gaps, never a ring.
		for (int i = 0, n = Vfx.count(14); i < n; i++) {
			double angle = RANDOM.nextDouble() * Math.PI * 2;

			if (KineticNoise.at((float) Math.cos(angle) * 2f + seed, (float) Math.sin(angle) * 2f, seed) < 0.33f) {
				continue;
			}

			Vec3 out = KineticKit.flat(angle);
			Vec3 at = new Vec3(landing.x + out.x * 0.3, top + 0.05 + RANDOM.nextDouble() * 0.25, landing.z + out.z * 0.3);
			Vec3 vel = out.scale(0.08 + RANDOM.nextDouble() * 0.12).add(along.scale(0.06)).add(0, 0.02 + RANDOM.nextDouble() * 0.03, 0);
			DustCloudParticle cloud = KineticKit.dust(at, vel, KineticKit.airDust(dust), 0.3f + RANDOM.nextFloat() * 0.15f, 2.6f, 0.6f, 26 + RANDOM.nextInt(14));

			if (cloud != null) {
				cloud.hold(0.2f).lift(-0.002f).drag(0.88f);
			}
		}

		// Grit skidding on past its feet.
		for (int i = 0, n = Vfx.count(4); i < n; i++) {
			Vec3 out = KineticKit.flat(RANDOM.nextDouble() * Math.PI * 2);
			Vec3 vel = out.scale(0.05 + RANDOM.nextDouble() * 0.06).add(along.scale(0.1)).add(0, 0.1 + RANDOM.nextDouble() * 0.08, 0);
			KineticKit.pebble(new Vec3(landing.x, top + 0.04, landing.z), vel, KineticKit.gritColor(dust), 0.04f + RANDOM.nextFloat() * 0.04f, 22 + RANDOM.nextInt(8));
		}
	}

	private static void items(ClientLevel level, EffectEventPayload payload) {
		Vec3 point = payload.pos();
		Vec3 toHand = payload.dir();
		play(level, point, ModSounds.FORCE_PULL_DRAG, 0.5f, 1.15f);

		if (!Vfx.inRange(point)) {
			return;
		}

		int items = Math.max(1, Math.round(payload.intensity()));
		Vec3 dir = toHand.lengthSqr() > 1.0e-6 ? toHand.normalize() : Vec3.ZERO;

		for (int i = 0, n = Vfx.count(Math.min(12, 3 * items)); i < n; i++) {
			Vec3 at = point.add(jitterScalar(0.8), 0.15 + RANDOM.nextDouble() * 0.2, jitterScalar(0.8));
			KineticKit.streak(at, dir.scale(0.5 + RANDOM.nextDouble() * 0.3), 0.12f, 0.45f, 6);
		}

		BlockPos ground = KineticKit.ground(level, point.add(0, 0.5, 0), 3);

		if (ground != null) {
			float[] dust = KineticKit.dustColor(level, ground);

			for (int i = 0, n = Vfx.count(4); i < n; i++) {
				Vec3 at = point.add(jitterScalar(0.6), 0.12, jitterScalar(0.6));
				KineticKit.dust(at, dir.scale(0.1).add(0, 0.03, 0), dust, 0.15f, 2.4f, 0.45f, 20 + RANDOM.nextInt(8));
			}
		}
	}

	/** {@code force_pull_disarm}: the weapon wrenched out of line. Entity = target, pos = weapon hand. */
	static void onDisarm(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity target) {
		Vec3 pos = payload.pos();
		play(level, pos, ModSounds.FORCE_PULL_DISARM, 0.9f, pitchVariance(0.05f));

		if (!Vfx.inRange(pos)) {
			return;
		}

		for (int i = 0, n = Vfx.count(6); i < n; i++) {
			Vec3 vel = new Vec3(jitterScalar(0.12), 0.06 + RANDOM.nextDouble() * 0.1, jitterScalar(0.12));
			ForceParticle p = spawn(ForceParticles.LIGHTNING_SPARK, pos.add(jitter(0.08)), vel);

			if (p != null) {
				// Hot metal, not lightning: warm white to orange, falling.
				p.color(1f, 0.78f + RANDOM.nextFloat() * 0.15f, 0.45f).size(0.03f, 0.01f).life(8 + RANDOM.nextInt(5)).fall(0.6f, true).drag(0.94f);
			}
		}

		for (int i = 0, n = Vfx.count(3); i < n; i++) {
			KineticKit.streak(pos.add(jitter(0.15)), jitter(0.25), 0.1f, 0.45f, 5);
		}
	}

	/** Nothing to do each tick: a dragged creature's wake runs with the kinetic shapes ({@link KineticTrails}). */
	static void tick(ClientLevel level) {
	}

	/** Nothing to forget: the wakes end with the kinetic shapes on a level change or disconnect. */
	static void clear() {
	}

	private PullFx() {
	}
}
