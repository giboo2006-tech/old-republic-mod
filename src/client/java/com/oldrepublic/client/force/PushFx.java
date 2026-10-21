package com.oldrepublic.client.force;

import static com.oldrepublic.client.force.ForceFxUtil.*;

import java.util.ArrayList;
import java.util.HashMap;
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
import com.oldrepublic.client.force.particle.ForceParticle;
import com.oldrepublic.client.fx.CameraShake;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.client.hud.HudClient;
import com.oldrepublic.force.ForceEffects;
import com.oldrepublic.force.ForceParticles;
import com.oldrepublic.force.PushBranch;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Builds every Force Push visual and sound on the client from effect events (see {@code docs/VFX_SFX_SPEC.md}, "Force
 * Push"): bent air, dust and debris, never glyphs or glows. While the push charges, dust is drawn into a vortex at the palm
 * and creeps in along the ground, streaks of air are sucked in, and pebbles lift off the ground and hang trembling. The
 * release is a shockwave: a front of pressure tears out of the hand and slows as it spends itself, streaks of air rush
 * along the cone, and the ground under the front is scoured, dust rolling outward and grit thrown ahead, while the pebbles
 * held up by the charge are flung along the push. A target is struck by its own small front, dust is blasted off it and
 * grit kicked from under its feet, and it skids away trailing dust. All particle counts go through {@link Vfx#count} and
 * positions through {@link Vfx#inRange}.
 */
final class PushFx {
	/** Pebbles one charging caster may hold up at once. */
	private static final int MAX_HELD_PEBBLES = 10;
	/** Ticks a pebble lifted by the charge may hang before it falls by itself. */
	private static final int PEBBLE_HOVER = 40;
	private static final Map<Integer, List<PebbleParticle>> HELD = new HashMap<>();

	// ---------------------------------------------------------------- effect event handlers

	static void onCharge(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		if (entity == null) {
			return;
		}

		boolean self = entity == mc.player;

		if (payload.variant() == ForceEffects.CHARGE_STOP) {
			PushAnimations.stopCharge(entity.getId());
			dropHeld(entity.getId(), null);
		} else if (self && PushAnimations.isCharging(entity.getId())) {
			PushAnimations.confirm(entity.getId());
		} else {
			PushAnimations.startCharge(entity.getId());
		}
	}

	static void onRelease(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		float charge = Mth.clamp(payload.intensity(), 0f, 1f);
		PushBranch branch = ForceEffects.releaseBranch(payload.variant());
		float range = ForceEffects.releaseRange(payload.variant());
		float halfAngle = ForceEffects.releaseHalfAngle(payload.variant());
		Vec3 eye = payload.pos();
		Vec3 dir = payload.dir().lengthSqr() > 1.0e-6 ? payload.dir().normalize() : new Vec3(0, 0, 1);

		if (entity != null) {
			PushAnimations.release(entity.getId());
		}

		play(level, eye, ModSounds.FORCE_PUSH_RELEASE, 0.85f + 0.3f * charge, 1f + (RANDOM.nextFloat() * 2f - 1f) * 0.06f);

		if (entity != null && entity == mc.player) {
			CameraShake.add(0.12f + 0.12f * charge);
		}

		if (!Vfx.inRange(eye)) {
			if (entity != null) {
				dropHeld(entity.getId(), null);
			}

			return;
		}

		LivingEntity caster = entity instanceof LivingEntity living ? living : null;
		Vec3 hand = caster != null ? KineticKit.hand(caster, 1f, 1f) : eye.add(dir.scale(0.5));
		Vec3 feet = caster != null ? caster.position() : eye.subtract(0, 1.6, 0);
		float light = KineticKit.light(level, hand);
		boolean pulse = branch == PushBranch.DEFLECTING_PULSE;

		if (pulse) {
			Vec3 centre = caster != null ? caster.position().add(0, caster.getBbHeight() * 0.55, 0) : eye.subtract(0, 0.6, 0);
			spherePulse(level, centre, feet, range, charge, light);
		} else {
			conePulse(level, hand, feet, dir, range, halfAngle, branch, charge, light);
		}

		if (entity != null) {
			dropHeld(entity.getId(), pulse ? null : dir);
		}

		recoil(level, feet, dir, pulse, charge);
		shakeFoliage(level, feet, dir, range, pulse ? 180f : halfAngle);
	}

	static void onImpact(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity target) {
		Vec3 pos = payload.pos();
		Vec3 dir = payload.dir().lengthSqr() > 1.0e-6 ? payload.dir().normalize() : new Vec3(0, 0, 1);
		boolean projectile = payload.variant() == ForceEffects.IMPACT_PROJECTILE;

		if (!projectile && payload.variant() < ForceEffects.IMPACT_SOUND_LIMIT) {
			play(level, pos, ModSounds.FORCE_PUSH_IMPACT, 0.8f, 0.92f + RANDOM.nextFloat() * 0.16f);
		}

		if (!Vfx.inRange(pos)) {
			return;
		}

		float light = KineticKit.light(level, pos);
		// The pressure striking the body: a small front bursting from its near side and opening along the push.
		KineticRender.add(new PressureFront(pos.subtract(dir.scale(0.25)), dir).spread(75f).radius(0.25f, projectile ? 1f : 1.7f).life(projectile ? 5 : 7)
				.burst(2.4f).strength(projectile ? 0.55f : 0.95f).light(light));

		if (projectile) {
			for (int i = 0, n = Vfx.count(4); i < n; i++) {
				KineticKit.streak(pos.add(KineticKit.jitter(0.2)), dir.scale(0.6 + RANDOM.nextDouble() * 0.3).add(KineticKit.jitter(0.08)), 0.18f, 0.5f, 6);
			}

			return;
		}

		Vec3 feet = target != null ? target.position() : pos.subtract(0, 0.9, 0);
		BlockPos ground = KineticKit.ground(level, feet.add(0, 0.4, 0), 3);
		float[] dust = KineticKit.dustColor(level, ground);
		float[] grit = KineticKit.gritColor(dust);
		Vec3 flat = KineticKit.horizontal(dir);

		// Dust blasted off the struck side and carried along the push.
		for (int i = 0, n = Vfx.count(9); i < n; i++) {
			Vec3 at = pos.add(KineticKit.jitter(0.3)).add(0, KineticKit.spread(0.4), 0);
			Vec3 vel = dir.scale(0.22 + RANDOM.nextDouble() * 0.3).add(KineticKit.jitter(0.06)).add(0, 0.03, 0);
			DustCloudParticle cloud = KineticKit.dust(at, vel, KineticKit.airDust(dust), 0.4f + RANDOM.nextFloat() * 0.2f, 2.8f, 0.62f, 28 + RANDOM.nextInt(16));

			if (cloud != null) {
				cloud.lift(0.006f);
			}
		}

		if (ground != null) {
			BlockState floor = level.getBlockState(ground);
			double top = ground.getY() + 1.02;

			// Grit and clods kicked out from under its feet.
			for (int i = 0, n = Vfx.count(6); i < n; i++) {
				Vec3 at = new Vec3(feet.x + KineticKit.spread(0.35), top + 0.05, feet.z + KineticKit.spread(0.35));
				Vec3 vel = flat.scale(0.12 + RANDOM.nextDouble() * 0.2).add(KineticKit.jitter(0.05)).add(0, 0.16 + RANDOM.nextDouble() * 0.16, 0);
				KineticKit.pebble(at, vel, grit, 0.045f + RANDOM.nextFloat() * 0.045f, 30 + RANDOM.nextInt(12));
			}

			for (int i = 0, n = Vfx.count(5); i < n; i++) {
				Vec3 at = new Vec3(feet.x + KineticKit.spread(0.4), top + 0.05, feet.z + KineticKit.spread(0.4));
				KineticKit.blockBit(level, floor, at, flat.scale(0.12 + RANDOM.nextDouble() * 0.1).add(0, 0.14, 0));
			}

			// A low roll of dust where it was standing, settling slowly (the aftermath).
			for (int i = 0, n = Vfx.count(3); i < n; i++) {
				Vec3 at = new Vec3(feet.x + KineticKit.spread(0.4), top + 0.15, feet.z + KineticKit.spread(0.4));
				DustCloudParticle cloud = KineticKit.dust(at, flat.scale(0.08 + RANDOM.nextDouble() * 0.06).add(0, 0.02, 0), KineticKit.airDust(dust), 0.5f, 2.6f,
						0.5f, 45 + RANDOM.nextInt(20));

				if (cloud != null) {
					cloud.hold(0.15f).lift(-0.002f);
				}
			}
		}

		if (target != null) {
			KineticTrails.wake(target, 18, Vfx.count(14));
		}
	}

	static void onSlam(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity target) {
		Vec3 pos = payload.pos();
		Vec3 normal = payload.dir().lengthSqr() > 1.0e-6 ? payload.dir().normalize() : new Vec3(0, 1, 0);
		play(level, pos, ModSounds.FORCE_PUSH_SLAM, 1f, 0.95f + RANDOM.nextFloat() * 0.1f);

		if (!Vfx.inRange(pos)) {
			return;
		}

		BlockPos wallPos = BlockPos.containing(pos.subtract(normal.scale(0.35)));
		BlockState wall = level.getBlockState(wallPos);

		if (wall.isAir() && target != null) {
			wall = target.getBlockStateOn();
			BlockPos under = KineticKit.ground(level, target.position().add(0, 0.4, 0), 3);
			wallPos = under != null ? under : wallPos;
		}

		float[] dust = wall.isAir() ? KineticKit.dustColor(level, null) : KineticKit.dustColor(level, wallPos);
		float[] grit = KineticKit.gritColor(dust);
		float[] haze = KineticKit.airDust(dust);

		// The air crushed against the wall bursts out along its face: dust thrown out from the point in patches with gaps.
		Vec3 side = normal.cross(new Vec3(0, 1, 0));
		side = side.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : side.normalize();
		Vec3 up = side.cross(normal).normalize();
		float seed = RANDOM.nextFloat() * 40f;

		for (int i = 0, n = Vfx.count(12); i < n; i++) {
			double a = RANDOM.nextDouble() * Math.PI * 2;

			if (KineticNoise.at((float) Math.cos(a) * 2f + seed, (float) Math.sin(a) * 2f, seed) < 0.35f) {
				continue;
			}

			Vec3 along = side.scale(Math.cos(a)).add(up.scale(Math.sin(a)));
			Vec3 at = pos.add(along.scale(0.2 + RANDOM.nextDouble() * 0.3)).add(normal.scale(0.15));
			Vec3 vel = along.scale(0.12 + RANDOM.nextDouble() * 0.14).add(normal.scale(0.03 + RANDOM.nextDouble() * 0.05));
			DustCloudParticle cloud = KineticKit.dust(at, vel, haze, 0.3f + RANDOM.nextFloat() * 0.18f, 2.8f, 0.6f, 30 + RANDOM.nextInt(16));

			if (cloud != null) {
				cloud.hold(0.2f).drag(0.87f);
			}
		}

		// A thick cloud of the wall's dust, rolling out and sinking.
		for (int i = 0, n = Vfx.count(10); i < n; i++) {
			Vec3 at = pos.add(KineticKit.jitter(0.45));
			Vec3 vel = normal.scale(0.12 + RANDOM.nextDouble() * 0.3).add(KineticKit.jitter(0.12)).add(0, 0.03, 0);
			DustCloudParticle cloud = KineticKit.dust(at, vel, haze, 0.45f + RANDOM.nextFloat() * 0.25f, 2.8f, 0.68f, 40 + RANDOM.nextInt(24));

			if (cloud != null) {
				cloud.lift(RANDOM.nextBoolean() ? 0.004f : -0.004f);
			}
		}

		// Chips of the wall thrown back out.
		for (int i = 0, n = Vfx.count(12); i < n; i++) {
			Vec3 vel = normal.scale(0.15 + RANDOM.nextDouble() * 0.25).add(KineticKit.jitter(0.14)).add(0, 0.12 + RANDOM.nextDouble() * 0.15, 0);
			KineticKit.pebble(pos.add(KineticKit.jitter(0.3)), vel, grit, 0.05f + RANDOM.nextFloat() * 0.06f, 30 + RANDOM.nextInt(15));
		}

		if (!wall.isAir()) {
			for (int i = 0, n = Vfx.count(14); i < n; i++) {
				Vec3 vel = normal.scale(0.1 + RANDOM.nextDouble() * 0.15).add(KineticKit.jitter(0.08)).add(0, 0.1, 0);
				KineticKit.blockBit(level, wall, pos.add(KineticKit.jitter(0.35)), vel);
			}
		}

		for (int i = 0, n = Vfx.count(6); i < n; i++) {
			KineticKit.streak(pos.add(KineticKit.jitter(0.3)), normal.scale(0.5 + RANDOM.nextDouble() * 0.4).add(KineticKit.jitter(0.25)), 0.2f, 0.5f, 6);
		}

		if (mc.player != null) {
			double distance = mc.player.position().distanceTo(pos);

			if (distance < 10) {
				CameraShake.add(0.14f * (float) (1 - distance / 10));
			}
		}
	}

	/** Foundation event {@code oldrepublic:energy_fail}: the hand flickers and a couple of dull motes fall. */
	static void onEnergyFail(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		Vec3 hand = entity instanceof LivingEntity living ? handPos(living, ForceFxUtil.freeArmIsLeft(living), 0.5f) : payload.pos();
		play(level, hand, ModSounds.FORCE_FAIL, 0.7f, 1f);

		if (entity == mc.player) {
			HudClient.onEnergyFail();
			// Our predicted charge or channel was refused: drop the prediction now.
			PushAnimations.stopCharge(entity.getId());
			PowerAnimations.stopPredicted(entity.getId());
			dropHeld(entity.getId(), null);
		}

		if (!Vfx.inRange(hand)) {
			return;
		}

		if (Vfx.flashing()) {
			for (int i = 0; i < 3; i++) {
				ripple(hand.add(jitter(0.05)), Vec3.ZERO, 0.1f, 0.18f, 0.28f, 2 + i);
			}
		} else {
			ripple(hand, Vec3.ZERO, 0.12f, 0.16f, 0.14f, 8);
		}

		for (int i = 0, spawned = Vfx.count(2); i < spawned; i++) {
			ForceParticle p = spawn(ForceParticles.FAIL_MOTE, hand.add(jitter(0.08)), new Vec3(jitterScalar(0.02), 0.02, jitterScalar(0.02)));

			if (p != null) {
				p.color(0.42f, 0.41f, 0.39f).size(0.06f, 0.05f).alpha(0.8f).life(16).fall(0.5f, true);
			}
		}
	}

	static void onLearned(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		if (!(entity instanceof LivingEntity living) || !Vfx.inRange(living.position())) {
			return;
		}

		Vec3 hand = handPos(living, ForceFxUtil.freeArmIsLeft(living), 0.6f);
		ripple(hand, Vec3.ZERO, 0.2f, 0.7f, 0.18f, 10);

		for (int i = 0, spawned = Vfx.count(6); i < spawned; i++) {
			Vec3 from = hand.add(jitter(1.2));
			ForceParticle p = spawn(ForceParticles.GATHER_DUST, from, hand.subtract(from).scale(1 / 12.0));

			if (p != null) {
				p.color(DEFAULT_DUST_R, DEFAULT_DUST_G, DEFAULT_DUST_B).life(12);
			}
		}
	}

	// ---------------------------------------------------------------- charge (every client tick)

	/**
	 * Anticipation while charging: dust is drawn into a vortex at the palm, dust creeps in along the ground toward the
	 * caster, streaks of air are sucked in, and pebbles lift off the ground round the caster and hang there trembling, more
	 * and higher as the charge grows.
	 */
	static void chargeTick(ClientLevel level, LivingEntity caster, float charge, long age) {
		if (!Vfx.inRange(caster.position())) {
			return;
		}

		Vec3 hand = KineticKit.hand(caster, 0f, 1f);
		BlockPos underfoot = KineticKit.ground(level, caster.position().add(0, 0.4, 0), 3);
		float[] dust = KineticKit.dustColor(level, underfoot);

		// Dust drawn into a vortex at the palm.
		for (int i = 0, n = Vfx.count(2); i < n; i++) {
			Vec3 out = KineticKit.flat(RANDOM.nextDouble() * Math.PI * 2);
			Vec3 at = hand.add(out.scale(0.45 + RANDOM.nextDouble() * 0.4)).add(0, KineticKit.spread(0.3), 0);
			DustCloudParticle mote = KineticKit.dust(at, Vec3.ZERO, KineticKit.airDust(dust), 0.09f + RANDOM.nextFloat() * 0.06f, 0.6f,
					0.5f + 0.35f * charge, 12 + RANDOM.nextInt(4));

			if (mote != null) {
				mote.swirl(hand, 0.32f + 0.2f * charge, 0.16f).hold(0.6f);
			}
		}

		// Dust lifted off the ground ahead and drawn in toward the gathering hand, more as the charge grows.
		Vec3 forward = KineticKit.horizontal(caster.getViewVector(1f));

		for (int i = 0, n = Vfx.count(age % 2 == 0 ? 2 : 1); i < n; i++) {
			double angle = Math.atan2(forward.z, forward.x) + KineticKit.spread(0.8);
			double distance = 1.4 + RANDOM.nextDouble() * 2.4;
			Vec3 around = caster.position().add(Math.cos(angle) * distance, 0.8, Math.sin(angle) * distance);
			BlockPos ground = KineticKit.ground(level, around, 3);

			if (ground != null) {
				Vec3 start = new Vec3(around.x, ground.getY() + 1.1, around.z);
				Vec3 toHand = hand.subtract(start).normalize().scale(0.05 + 0.06 * charge).add(0, 0.015, 0);
				DustCloudParticle wisp = KineticKit.dust(start, toHand, KineticKit.airDust(KineticKit.dustColor(level, ground)), 0.16f + RANDOM.nextFloat() * 0.1f,
						2.4f, 0.32f + 0.3f * charge, 18 + RANDOM.nextInt(8));

				if (wisp != null) {
					wisp.hold(0.45f).drag(0.98f);
				}
			}
		}

		// Air sucked in toward the palm.
		if (age % 2 == 0) {
			for (int i = 0, n = Vfx.count(2); i < n; i++) {
				Vec3 from = hand.add(KineticKit.jitter(1f).normalize().scale(1.2 + RANDOM.nextDouble() * 0.8));
				AirStreakParticle streak = KineticKit.streak(from, hand.subtract(from).scale(0.16), 0.1f + 0.06f * charge, 0.25f + 0.25f * charge, 6);

				if (streak != null) {
					streak.drag(0.92f);
				}
			}
		}

		// Dust creeping in along the ground toward the caster's feet.
		if (age % 2 == 1) {
			for (int i = 0, n = Vfx.count(1); i < n; i++) {
				Vec3 out = KineticKit.flat(RANDOM.nextDouble() * Math.PI * 2);
				double distance = 1.6 + RANDOM.nextDouble() * 1.6;
				BlockPos ground = KineticKit.ground(level, caster.position().add(out.scale(distance)).add(0, 0.8, 0), 3);

				if (ground != null) {
					Vec3 start = new Vec3(caster.getX() + out.x * distance, ground.getY() + 1.12, caster.getZ() + out.z * distance);
					DustCloudParticle cloud = KineticKit.dust(start, out.scale(-0.07 - 0.05 * charge), KineticKit.dustColor(level, ground), 0.2f, 2.2f, 0.35f,
							22);

					if (cloud != null) {
						cloud.hold(0.4f).drag(0.97f);
					}
				}
			}
		}

		// Now and then a pebble lifts off the ground ahead and hangs, trembling, until the push throws it.
		if (age % 2 == 1) {
			List<PebbleParticle> held = HELD.computeIfAbsent(caster.getId(), id -> new ArrayList<>());
			held.removeIf(p -> !p.hovering());

			if (held.size() < MAX_HELD_PEBBLES) {
				double angle = Math.atan2(forward.z, forward.x) + KineticKit.spread(1.2);
				double distance = 1.1 + RANDOM.nextDouble() * 1.9;
				Vec3 around = caster.position().add(Math.cos(angle) * distance, 0.8, Math.sin(angle) * distance);
				BlockPos ground = KineticKit.ground(level, around, 3);

				if (ground != null) {
					Vec3 start = new Vec3(around.x, ground.getY() + 1.05, around.z);
					float[] grit = KineticKit.gritColor(KineticKit.dustColor(level, ground));
					PebbleParticle pebble = KineticKit.pebble(start, Vec3.ZERO, grit, 0.07f + RANDOM.nextFloat() * 0.08f, PEBBLE_HOVER + 40);

					if (pebble != null) {
						pebble.hover(PEBBLE_HOVER, 0.35 + RANDOM.nextDouble() * (0.5 + 0.9 * charge));
						held.add(pebble);
					}

					// The ground it left puffs a little.
					DustCloudParticle puff = KineticKit.dust(start.add(0, 0.05, 0), new Vec3(0, 0.025, 0),
							KineticKit.airDust(KineticKit.dustColor(level, ground)), 0.16f, 2.6f, 0.45f, 20);

					if (puff != null) {
						puff.hold(0.2f);
					}
				}
			}
		}
	}

	// ---------------------------------------------------------------- shapes

	/** The cone: a front of pressure from the hand, streaks of air along it, the ground scoured under it. */
	private static void conePulse(ClientLevel level, Vec3 hand, Vec3 feet, Vec3 dir, float range, float halfAngle, PushBranch branch, float charge,
			float light) {
		boolean focused = branch == PushBranch.FOCUSED_BLAST;
		boolean wide = branch == PushBranch.REPULSE_WAVE;
		float strength = 0.7f + 0.45f * charge;
		// Lower than it is wide, so it runs along the ground at what it strikes rather than climbing the sky.
		float flatten = wide ? 0.45f : focused ? 1f : 0.75f;
		int life = focused ? 7 : 9;
		// The narrow blast keeps its fragments close to one front, so from the side it reads as one curved wall of air.
		PressureFront front = new PressureFront(hand, dir).spread(Math.min(80f, halfAngle * 1.2f + 6f)).flatten(flatten).radius(0.25f, range + 1.4f)
				.life(life).burst(focused ? 2.8f : 2.4f).wobble(focused ? 0.03f : 0.08f).strength(strength).light(light);
		KineticRender.add(front);
		// A weaker second front close behind the first, as the air rebounds.
		KineticRender.later(2, l -> KineticRender.add(new PressureFront(hand, dir).spread(Math.min(80f, halfAngle * 1.1f + 4f)).flatten(flatten)
				.radius(0.2f, range * 0.8f).life(life).burst(2.2f).strength(strength * 0.45f).light(light).thickness(0.04f)));

		Vec3 u = dir.cross(new Vec3(0, 1, 0));
		u = u.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : u.normalize();
		Vec3 v = u.cross(dir).normalize();
		double tan = Math.tan(Math.toRadians(Math.min(halfAngle, 75f)));

		// Air rushing along the cone, fastest and longest down its middle.
		for (int i = 0, n = Vfx.count(focused ? 22 : 16); i < n; i++) {
			double a = RANDOM.nextDouble() * Math.PI * 2;
			double out = Math.sqrt(RANDOM.nextDouble()) * tan;
			Vec3 ray = dir.add(u.scale(Math.cos(a) * out)).add(v.scale(Math.sin(a) * out * flatten)).normalize();
			double speed = (focused ? 1.7 : 1.3) * (0.75 + RANDOM.nextDouble() * 0.5) * (1 - 0.35 * out / Math.max(0.05, tan));
			Vec3 at = hand.add(ray.scale(0.5 + RANDOM.nextDouble() * 0.9));
			AirStreakParticle streak = KineticKit.streak(at, ray.scale(speed), focused ? 0.2f : 0.16f, focused ? 0.6f : 0.5f, 7 + RANDOM.nextInt(4));

			if (streak != null) {
				streak.stretch(0.5f).drag(0.8f);
			}
		}

		// A puff of dust and air thrown out of the palm itself.
		for (int i = 0, n = Vfx.count(3); i < n; i++) {
			DustCloudParticle cloud = KineticKit.dust(hand.add(dir.scale(0.9)).add(KineticKit.jitter(0.12)), dir.scale(0.35 + RANDOM.nextDouble() * 0.25)
					.add(KineticKit.jitter(0.05)), KineticKit.airDust(KineticKit.dustColor(level, null)), 0.15f, 3.2f, 0.3f, 14 + RANDOM.nextInt(6));

			if (cloud != null) {
				cloud.drag(0.8f);
			}
		}

		scour(level, front, hand, feet, dir, range, wide ? halfAngle + 10f : halfAngle + 8f, false, charge);
	}

	/** Deflecting Pulse: a whole sphere of pressure bursting from the body, and the ground scoured all round. */
	private static void spherePulse(ClientLevel level, Vec3 centre, Vec3 feet, float radius, float charge, float light) {
		// Round the body, not over it: a band of the front, so it never reads as a ball or a dome.
		PressureFront front = new PressureFront(centre, new Vec3(0, 1, 0)).band(40f, 140f).radius(0.5f, radius + 1.2f).life(9).burst(2.3f)
				.strength(0.75f + 0.4f * charge).light(light);
		KineticRender.add(front);

		for (int i = 0, n = Vfx.count(16); i < n; i++) {
			Vec3 ray = KineticKit.flat(RANDOM.nextDouble() * Math.PI * 2).add(0, KineticKit.spread(0.5), 0).normalize();
			AirStreakParticle streak = KineticKit.streak(centre.add(ray.scale(0.5)), ray.scale(0.8 + RANDOM.nextDouble() * 0.4), 0.16f, 0.55f, 8);

			if (streak != null) {
				streak.stretch(0.5f).drag(0.8f);
			}
		}

		scour(level, front, centre, feet, new Vec3(0, 0, 1), radius, 180f, true, charge);
	}

	/**
	 * As the front passes over the ground it tears dust and grit off it: irregular puffs with gaps, rolling outward and
	 * settling over a second or two, pebbles and the ground's own bits thrown ahead. Spread over the ticks the front takes to
	 * cross {@code range}, each tick covering the band of ground the front crossed.
	 */
	private static void scour(ClientLevel level, PressureFront front, Vec3 origin, Vec3 feet, Vec3 dir, float range, float halfAngle, boolean whole,
			float charge) {
		Vec3 flat = KineticKit.horizontal(dir);
		// Aimed down, the cone ploughs into the ground; aimed up it barely touches it.
		float reach = whole ? 1f : Mth.clamp(1f - (float) dir.y * 1.6f, 0.25f, 1.6f);
		int[] clouds = {Vfx.count(Math.round((whole ? 30 : 24) * reach))};
		int[] pebbles = {Vfx.count(Math.round((whole ? 14 : 12) * reach))};
		int[] bits = {Vfx.count(Math.round(8 * reach))};
		float start = whole ? 0.8f : 1.1f;
		float end = range + 0.6f;
		float[] last = {start};
		int[] tick = {0};
		float gapSeed = RANDOM.nextFloat() * 40f;
		float angleRange = (float) Math.toRadians(Math.min(halfAngle, 180f));
		double baseAngle = Math.atan2(flat.z, flat.x);

		KineticRender.run(l -> {
			tick[0]++;
			float r = Math.min(end, front.radiusAt(tick[0]));
			float share = Mth.clamp((r - last[0]) / (end - start), 0f, 1f);

			if (share > 0f) {
				int nClouds = Math.min(clouds[0], Math.max(1, Math.round(share * clouds[0] * 1.6f)));
				int nPebbles = Math.min(pebbles[0], Math.round(share * pebbles[0] * 1.6f + 0.4f));
				int nBits = Math.min(bits[0], Math.round(share * bits[0] * 1.6f + 0.3f));
				clouds[0] -= nClouds;
				pebbles[0] -= nPebbles;
				bits[0] -= nBits;

				for (int i = 0; i < nClouds + nPebbles + nBits; i++) {
					double d = last[0] + RANDOM.nextDouble() * (r - last[0]);
					double a = baseAngle + KineticKit.spread(angleRange);
					// Gaps: the ground is torn in patches, never an even ring.
					float gap = KineticNoise.at((float) Math.cos(a) * 2.5f + gapSeed, (float) Math.sin(a) * 2.5f,
							(float) d * 0.6f);

					if (gap < 0.38f) {
						continue;
					}

					Vec3 out = KineticKit.flat(a);
					Vec3 above = feet.add(out.scale(d)).add(0, 1.3, 0);
					BlockPos ground = KineticKit.ground(l, above, 4);

					if (ground == null) {
						continue;
					}

					float[] dust = KineticKit.dustColor(l, ground);
					Vec3 at = new Vec3(above.x, ground.getY() + 1.05, above.z);
					float fall = 1f - (float) (d / (range + 1));

					if (i < nClouds) {
						// Thrown out after the front, fastest near it, so the torn-up dust rolls out behind it as a wall.
						double lead = Mth.clamp((d - last[0]) / Math.max(0.1, r - last[0]), 0, 1);
						Vec3 vel = out.scale(0.12 + (0.12 + 0.3 * lead) * (0.5 + fall)).add(0, 0.03 + RANDOM.nextDouble() * 0.07, 0);
						DustCloudParticle cloud = KineticKit.dust(at.add(0, 0.15, 0), vel, KineticKit.airDust(dust), 0.34f + RANDOM.nextFloat() * 0.26f,
								3f + RANDOM.nextFloat() * 0.6f, 0.56f + 0.14f * fall, 32 + RANDOM.nextInt(28));

						if (cloud != null) {
							cloud.hold(0.2f).lift(RANDOM.nextFloat() < 0.3f ? 0.006f : -0.002f).drag(0.88f);
						}
					} else if (i < nClouds + nPebbles) {
						Vec3 vel = out.scale(0.12 + RANDOM.nextDouble() * 0.18).add(0, 0.14 + RANDOM.nextDouble() * 0.2 * (0.6 + charge * 0.4), 0);
						KineticKit.pebble(at.add(0, 0.06, 0), vel, KineticKit.gritColor(dust), 0.04f + RANDOM.nextFloat() * 0.05f, 28 + RANDOM.nextInt(14));
					} else {
						KineticKit.blockBit(l, l.getBlockState(ground), at, out.scale(0.12).add(0, 0.12, 0));
					}
				}
			}

			last[0] = r;
			return r < end - 0.05f && tick[0] < 14;
		});
	}

	/** The kick of the release at the caster's own feet: a few low puffs thrown back and aside, more for a pulse. */
	private static void recoil(ClientLevel level, Vec3 feet, Vec3 dir, boolean pulse, float charge) {
		BlockPos ground = KineticKit.ground(level, feet.add(0, 0.4, 0), 3);

		if (ground == null) {
			return;
		}

		float[] dust = KineticKit.dustColor(level, ground);
		Vec3 back = KineticKit.horizontal(dir).scale(-1);
		double top = ground.getY() + 1.1;

		for (int i = 0, n = Vfx.count(pulse ? 6 : 4); i < n; i++) {
			Vec3 out = pulse ? KineticKit.flat(RANDOM.nextDouble() * Math.PI * 2) : back.yRot((float) KineticKit.spread(1.9)).normalize();
			Vec3 at = new Vec3(feet.x + out.x * 0.35, top, feet.z + out.z * 0.35);
			DustCloudParticle cloud = KineticKit.dust(at, out.scale(0.1 + 0.08 * charge + RANDOM.nextDouble() * 0.06).add(0, 0.02, 0), KineticKit.airDust(dust),
					0.25f, 2.8f, 0.45f, 24 + RANDOM.nextInt(12));

			if (cloud != null) {
				cloud.lift(-0.002f).drag(0.86f);
			}
		}
	}

	/** Struck leaves, grass and flowers inside the shape shed a few particles. */
	private static void shakeFoliage(ClientLevel level, Vec3 feet, Vec3 dir, float range, float halfAngle) {
		Vec3 flat = horizontal(dir);

		for (int i = 0, spawned = Vfx.count(8); i < spawned; i++) {
			double distance = 0.8 + RANDOM.nextDouble() * Math.max(0.5, range - 0.8);
			float yaw = (float) Math.toRadians((RANDOM.nextFloat() * 2f - 1f) * Math.min(halfAngle, 180f));
			Vec3 out = flat.yRot(yaw);
			BlockPos pos = BlockPos.containing(feet.add(out.scale(distance)).add(0, RANDOM.nextDouble() * 3, 0));
			BlockState state = level.getBlockState(pos);
			// API-CHECK: BlockState#is(TagKey) via TypedInstance
			boolean foliage = state.is(BlockTags.LEAVES) || state.is(BlockTags.FLOWERS) || state.is(BlockTags.REPLACEABLE_BY_TREES);

			// API-CHECK: BlockState#getFluidState().isEmpty() (used to skip water in replaceable_by_trees)
			if (!foliage || state.isAir() || !state.getFluidState().isEmpty()) {
				continue;
			}

			for (int k = 0; k < 2; k++) {
				Vec3 at = new Vec3(pos.getX() + RANDOM.nextDouble(), pos.getY() + RANDOM.nextDouble(), pos.getZ() + RANDOM.nextDouble());
				KineticKit.blockBit(level, state, at, new Vec3(out.x * 0.1, 0.02, out.z * 0.1));
			}
		}
	}

	// ---------------------------------------------------------------- helpers

	/** Lets go of the pebbles held up by a caster's charge: thrown along {@code dir}, or just dropped when it is null. */
	private static void dropHeld(int id, @Nullable Vec3 dir) {
		List<PebbleParticle> held = HELD.remove(id);

		if (held == null) {
			return;
		}

		for (PebbleParticle pebble : held) {
			if (dir == null) {
				pebble.drop(KineticKit.spread(0.02), 0, KineticKit.spread(0.02));
			} else {
				double speed = 0.45 + RANDOM.nextDouble() * 0.35;
				pebble.drop(dir.x * speed + KineticKit.spread(0.08), dir.y * speed + 0.1 + RANDOM.nextDouble() * 0.08, dir.z * speed + KineticKit.spread(0.08));
			}
		}
	}

	/** Forgets every held pebble (disconnect, a new level). */
	static void clear() {
		HELD.clear();
	}

	private PushFx() {
	}
}
