package com.oldrepublic.client.force;

import static com.oldrepublic.client.force.ForceFxUtil.*;

import org.jspecify.annotations.Nullable;

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
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Builds every Force Push visual and sound on the client from effect events (see
 * {@code docs/VFX_SFX_SPEC.md}, "Force Push"): bent air, dust and debris, never glyphs or glows.
 * All particle counts go through {@link Vfx#count} and positions through {@link Vfx#inRange}.
 */
final class PushFx {
	/** Travel speed of the bent-air shells: 18 blocks per second. */
	private static final double SHELL_SPEED = 0.9;

	// ---------------------------------------------------------------- effect event handlers

	static void onCharge(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		if (entity == null) {
			return;
		}

		boolean self = entity == mc.player;

		if (payload.variant() == ForceEffects.CHARGE_STOP) {
			PushAnimations.stopCharge(entity.getId());
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
			return;
		}

		LivingEntity caster = entity instanceof LivingEntity living ? living : null;
		Vec3 hand = caster != null ? handPos(caster, ForceFxUtil.freeArmIsLeft(caster), 1f) : eye.add(dir.scale(0.5));
		Vec3 feet = caster != null ? caster.position() : eye.subtract(0, 1.6, 0);

		if (branch == PushBranch.DEFLECTING_PULSE) {
			Vec3 center = caster != null ? caster.position().add(0, caster.getBbHeight() * 0.5, 0) : eye;
			spherePulse(center, range, charge);
		} else {
			conePulse(hand, dir, range, halfAngle, branch, charge);
		}

		groundDust(level, feet, dir, range, branch == PushBranch.DEFLECTING_PULSE ? 180f : halfAngle);
		shakeFoliage(level, feet, dir, range, branch == PushBranch.DEFLECTING_PULSE ? 180f : halfAngle);
	}

	static void onImpact(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity target) {
		Vec3 pos = payload.pos();
		Vec3 dir = payload.dir();
		boolean projectile = payload.variant() == ForceEffects.IMPACT_PROJECTILE;

		if (!projectile && payload.variant() < ForceEffects.IMPACT_SOUND_LIMIT) {
			play(level, pos, ModSounds.FORCE_PUSH_IMPACT, 0.8f, 0.92f + RANDOM.nextFloat() * 0.16f);
		}

		if (!Vfx.inRange(pos)) {
			return;
		}

		ripple(pos, dir.scale(0.05), 0.25f, 0.8f, 0.18f, 7);

		if (projectile) {
			return;
		}

		for (int i = 0, spawned = Vfx.count(5); i < spawned; i++) {
			Vec3 at = pos.add(jitter(0.2));
			Vec3 vel = dir.scale(0.04 + RANDOM.nextDouble() * 0.04).add(jitter(0.03));
			ForceParticle p = spawn(ForceParticles.PUSH_IMPACT, at, vel);

			if (p != null) {
				p.size(0.25f, 0.65f + RANDOM.nextFloat() * 0.2f);
			}
		}

		Vec3 feet = target != null ? target.position() : pos.subtract(0, 0.9, 0);
		BlockState ground = target != null ? target.getBlockStateOn() : null;

		if (ground != null && !ground.isAir()) {
			BlockParticleOption debris = new BlockParticleOption(ParticleTypes.BLOCK, ground);

			for (int i = 0, spawned = Vfx.count(6); i < spawned; i++) {
				level.addParticle(debris, feet.x + jitterScalar(0.4), feet.y + 0.1, feet.z + jitterScalar(0.4), dir.x * 0.2, 0.15, dir.z * 0.2);
			}
		}

		settlingDust(level, feet, dir, Vfx.count(3));
	}

	static void onSlam(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity target) {
		Vec3 pos = payload.pos();
		Vec3 normal = payload.dir();
		play(level, pos, ModSounds.FORCE_PUSH_SLAM, 1f, 0.95f + RANDOM.nextFloat() * 0.1f);

		if (!Vfx.inRange(pos)) {
			return;
		}

		BlockState wall = level.getBlockState(BlockPos.containing(pos.subtract(normal.scale(0.35))));

		if (wall.isAir() && target != null) {
			wall = target.getBlockStateOn();
		}

		if (!wall.isAir()) {
			BlockParticleOption debris = new BlockParticleOption(ParticleTypes.BLOCK, wall);

			for (int i = 0, spawned = Vfx.count(14); i < spawned; i++) {
				Vec3 vel = normal.scale(0.1 + RANDOM.nextDouble() * 0.15).add(jitter(0.08)).add(0, 0.1, 0);
				level.addParticle(debris, pos.x + jitterScalar(0.3), pos.y + jitterScalar(0.4), pos.z + jitterScalar(0.3), vel.x, vel.y, vel.z);
			}
		}

		for (int i = 0, spawned = Vfx.count(6); i < spawned; i++) {
			ForceParticle p = spawn(ForceParticles.PUSH_IMPACT, pos.add(jitter(0.3)), normal.scale(0.05).add(jitter(0.04)));

			if (p != null) {
				p.size(0.45f, 1.1f).life(9);
			}
		}

		ripple(pos, Vec3.ZERO, 0.4f, 1.4f, 0.22f, 8);
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

	/** Anticipation while charging: ground dust drifts to the hand, the palm shimmers faintly. */
	static void chargeTick(ClientLevel level, LivingEntity caster, float charge, long age) {
		if (!Vfx.inRange(caster.position())) {
			return;
		}

		Vec3 hand = handPos(caster, ForceFxUtil.freeArmIsLeft(caster), 0f);

		for (int i = 0, spawned = Vfx.count(2); i < spawned; i++) {
			double angle = RANDOM.nextDouble() * Math.PI * 2;
			double distance = 1.4 + RANDOM.nextDouble() * 1.6;
			Vec3 around = caster.position().add(Math.cos(angle) * distance, 0.6, Math.sin(angle) * distance);
			BlockPos ground = groundBelow(level, around);

			if (ground == null) {
				continue;
			}

			Vec3 start = new Vec3(around.x, ground.getY() + 1.05, around.z);
			ForceParticle p = spawn(ForceParticles.GATHER_DUST, start, hand.subtract(start).scale(1 / 14.0));

			if (p != null) {
				float[] rgb = dustColor(level, ground);
				p.color(rgb[0], rgb[1], rgb[2]).size(0.05f + RANDOM.nextFloat() * 0.04f, 0.04f).alpha(0.75f).life(14);
			}

			if (age % 4 == 0 && i == 0) {
				BlockState state = level.getBlockState(ground);
				level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), start.x, start.y, start.z, 0, 0.12, 0);
			}
		}

		if (age % 3 == 0) {
			ripple(hand, Vec3.ZERO, 0.12f + 0.25f * charge, 0.3f + 0.35f * charge, 0.10f + 0.10f * charge, 8);
		}
	}

	// ---------------------------------------------------------------- shapes

	/** 3 to 5 expanding shells and edge streaks travelling along the cone. */
	private static void conePulse(Vec3 origin, Vec3 dir, float range, float halfAngle, PushBranch branch, float charge) {
		Vec3 u = dir.cross(new Vec3(0, 1, 0));
		u = u.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : u.normalize();
		Vec3 v = u.cross(dir).normalize();
		boolean focused = branch == PushBranch.FOCUSED_BLAST;
		boolean wide = branch == PushBranch.REPULSE_WAVE;
		double speed = focused ? SHELL_SPEED * 1.4 : SHELL_SPEED;
		double tan = Math.tan(Math.toRadians(Math.min(halfAngle, 75f)));
		double flatten = wide ? 0.45 : 1.0;
		int shells = 3 + Math.round(2f * charge);
		int ring = Vfx.count(focused ? 5 : wide ? 10 : 8);
		float alpha = focused ? 0.16f : 0.10f + 0.06f * charge;

		for (int s = 0; s < shells; s++) {
			double start = 0.4 + s * 0.7;
			int life = (int) Math.max(4, (range - start) / speed);

			for (int k = 0; k < ring; k++) {
				double a = Math.PI * 2 * k / ring + s * 0.4;
				Vec3 ray = coneRay(dir, u, v, a, tan, flatten);
				float endSize = (float) (0.35 + range * tan * 0.3);
				ripple(origin.add(ray.scale(start)), ray.scale(speed), 0.35f, endSize, alpha, life);
			}
		}

		int streaks = Vfx.count(focused ? 14 : 10);

		for (int i = 0; i < streaks; i++) {
			double a = RANDOM.nextDouble() * Math.PI * 2;
			Vec3 ray = coneRay(dir, u, v, a, tan * (0.85 + RANDOM.nextDouble() * 0.15), flatten);

			if (wide) {
				ray = ray.add(0, -0.12, 0).normalize();
			}

			double streakSpeed = speed * (1.05 + RANDOM.nextDouble() * 0.25);
			ForceParticle p = spawn(ForceParticles.PUSH_STREAK, origin.add(ray.scale(0.3 + RANDOM.nextDouble() * 0.6)), ray.scale(streakSpeed));

			if (p != null) {
				p.alpha(focused ? 0.55f : 0.35f).size(focused ? 0.3f : 0.25f, focused ? 0.45f : 0.35f).life((int) Math.max(4, range / streakSpeed));
			}
		}

		ripple(origin, dir.scale(0.1), 0.2f, 0.9f, 0.25f, 6);
	}

	/** Deflecting Pulse: a spherical shell expanding from the body. */
	private static void spherePulse(Vec3 center, float radius, float charge) {
		int n = Vfx.count(18);
		double golden = Math.PI * (3 - Math.sqrt(5));
		double speed = SHELL_SPEED * 0.5;
		int life = (int) Math.max(4, radius / speed);

		for (int i = 0; i < n; i++) {
			double y = 1 - (i + 0.5) * 2.0 / n;
			double r = Math.sqrt(1 - y * y);
			double a = golden * i;
			Vec3 ray = new Vec3(Math.cos(a) * r, y, Math.sin(a) * r);
			ripple(center.add(ray.scale(0.4)), ray.scale(speed), 0.4f, 1.0f, 0.10f + 0.05f * charge, life);
		}

		for (int i = 0, spawned = Vfx.count(12); i < spawned; i++) {
			double a = RANDOM.nextDouble() * Math.PI * 2;
			Vec3 ray = new Vec3(Math.cos(a), -0.05, Math.sin(a)).normalize();
			ForceParticle p = spawn(ForceParticles.PUSH_STREAK, center.add(ray.scale(0.5)).subtract(0, 0.6, 0), ray.scale(speed * 1.3));

			if (p != null) {
				p.life(life);
			}
		}
	}

	/** Dust kicked off the floor inside the shape, settling over about 1.5 s (aftermath). */
	private static void groundDust(ClientLevel level, Vec3 feet, Vec3 dir, float range, float halfAngle) {
		Vec3 flat = horizontal(dir);

		for (int i = 0, spawned = Vfx.count(halfAngle >= 180f ? 14 : 10); i < spawned; i++) {
			double distance = 1 + RANDOM.nextDouble() * Math.max(0.5, range - 1);
			float yaw = (float) Math.toRadians((RANDOM.nextFloat() * 2f - 1f) * Math.min(halfAngle, 180f));
			Vec3 out = flat.yRot(yaw);
			Vec3 at = feet.add(out.scale(distance)).add(0, 1, 0);
			BlockPos ground = groundBelow(level, at);

			if (ground == null) {
				continue;
			}

			Vec3 start = new Vec3(at.x, ground.getY() + 1.05, at.z);
			ForceParticle p = spawn(ForceParticles.GATHER_DUST, start, out.scale(0.12).add(0, 0.04 + RANDOM.nextDouble() * 0.04, 0));

			if (p != null) {
				float[] rgb = dustColor(level, ground);
				p.color(rgb[0], rgb[1], rgb[2]).size(0.08f + RANDOM.nextFloat() * 0.06f, 0.06f).alpha(0.6f)
						.life(25 + RANDOM.nextInt(10)).drag(0.9f).fall(0.02f, true);
			}

			if (i % 2 == 0) {
				BlockState state = level.getBlockState(ground);
				level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), start.x, start.y, start.z, out.x * 0.15, 0.1, out.z * 0.15);
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

			BlockParticleOption leaf = new BlockParticleOption(ParticleTypes.BLOCK, state);

			for (int k = 0; k < 2; k++) {
				level.addParticle(leaf, pos.getX() + RANDOM.nextDouble(), pos.getY() + RANDOM.nextDouble(), pos.getZ() + RANDOM.nextDouble(), out.x * 0.1, 0.02, out.z * 0.1);
			}
		}
	}

	/** Slow motes that drift and settle near a target's feet. */
	private static void settlingDust(ClientLevel level, Vec3 feet, Vec3 dir, int count) {
		BlockPos ground = groundBelow(level, feet.add(0, 0.5, 0));
		float[] rgb = ground != null ? dustColor(level, ground) : new float[] {DEFAULT_DUST_R, DEFAULT_DUST_G, DEFAULT_DUST_B};

		for (int i = 0; i < count; i++) {
			ForceParticle p = spawn(ForceParticles.GATHER_DUST, feet.add(jitter(0.4)).add(0, 0.2, 0), dir.scale(0.05).add(0, 0.03, 0));

			if (p != null) {
				p.color(rgb[0], rgb[1], rgb[2]).size(0.1f, 0.07f).alpha(0.55f).life(25 + RANDOM.nextInt(6)).drag(0.92f).fall(0.02f, true);
			}
		}
	}

	// ---------------------------------------------------------------- helpers

	private static Vec3 coneRay(Vec3 dir, Vec3 u, Vec3 v, double angle, double tan, double flatten) {
		return dir.add(u.scale(Math.cos(angle) * tan)).add(v.scale(Math.sin(angle) * tan * flatten)).normalize();
	}

	private PushFx() {
	}
}
