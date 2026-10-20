package com.oldrepublic.enemy.scour;

import java.util.EnumSet;
import java.util.List;

import com.oldrepublic.enemy.hymn.FoeTargets;
import com.oldrepublic.enemy.hymn.Knock;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The sand scour's whole life (spec MILESTONE_5.md 12.1): burrowed, it creeps toward the nearest
 * player and ripples; within 4 blocks it bursts behind its tell and bites; above ground it chases and
 * bites until its time is up, then burrows; hurt below 30% it flees under the sand. The matriarch adds
 * the tail sweep and calls her brood. Always running (it holds MOVE and LOOK).
 */
final class ScourGoal extends Goal {
	private enum State {
		BURROWED,
		BURST_TELL,
		SURFACED,
		SWEEP_TELL
	}

	/** The matriarch counts her brood within this range. */
	private static final double BROOD_RANGE = 24;
	/** A cancelled sweep may come again this much later. */
	private static final int SWEEP_RETRY_TICKS = 60;
	/** The sweep strikes what is behind and beside her: in front of this dot with her facing it does not reach. */
	private static final double SWEEP_FRONT_DOT = 0.35;
	/** The burst's bite reaches this much past {@code biteReach} (it lunges out of the sand). */
	private static final double BURST_LUNGE = 1.2;

	private final SandScour mob;
	private State state = State.BURROWED;
	private int timer;
	private int repath;
	private long restUntil;
	private long surfaceUntil;
	private long fleeUntil;
	private boolean fled;
	private long nextBite;
	private long nextSweep;
	private long nextBrood = -1;
	private long nextRipple;

	ScourGoal(SandScour mob) {
		this.mob = mob;
		setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		return mob.isAlive();
	}

	@Override
	public boolean canContinueToUse() {
		return mob.isAlive();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void start() {
		state = mob.isBurrowed() ? State.BURROWED : State.SURFACED;
		long now = mob.level().getGameTime();
		surfaceUntil = now + ScourBalance.get().surfaceTicks;
	}

	@Override
	public void stop() {
		mob.getNavigation().stop();
		mob.setAction(SandScour.ACTION_NONE);
	}

	@Override
	public void tick() {
		if (!(mob.level() instanceof ServerLevel level)) {
			return;
		}

		ScourBalance b = ScourBalance.get();
		long now = level.getGameTime();

		if (now % FoeTargets.INTERVAL == 0) {
			updateTarget(level, b);
		}

		LivingEntity target = mob.getTarget();
		boolean interrupted = mob.consumeInterrupt();

		if (mob.isMatriarch() && target != null) {
			tickBrood(level, b, now);
		}

		switch (state) {
			case BURROWED -> burrowed(level, target, b, now);
			case BURST_TELL -> {
				mob.getNavigation().stop();

				if (--timer <= 0) {
					burst(level, target, b, now);
				}
			}
			case SURFACED -> surfaced(level, target, b, now);
			case SWEEP_TELL -> {
				mob.getNavigation().stop();

				if (interrupted) {
					mob.tellLog().cancel();
					mob.setAction(SandScour.ACTION_NONE);
					nextSweep = now + SWEEP_RETRY_TICKS;
					state = State.SURFACED;
				} else if (--timer <= 0) {
					sweep(level, b, now);
				}
			}
		}
	}

	/** Under the sand it feels for the nearest player (no sight needed); above it needs to see one. */
	private void updateTarget(ServerLevel level, ScourBalance b) {
		LivingEntity current = mob.getTarget();

		if (current != null && current.isAlive() && current.level() == level && !FoeTargets.ignored(current)
				&& current.distanceToSqr(mob) <= b.followRange * b.followRange * 2.25) {
			return;
		}

		mob.setTarget(FoeTargets.nearestPlayer(mob, level, b.followRange, !mob.isBurrowed()));
	}

	// ---------------------------------------------------------------- burrowed

	private void burrowed(ServerLevel level, LivingEntity target, ScourBalance b, long now) {
		if (now >= nextRipple) {
			nextRipple = now + Math.max(1, b.rippleIntervalTicks);
			mob.ripple(level);
		}

		if (target == null) {
			mob.getNavigation().stop();
			return;
		}

		double speed = b.burrowedSpeed / Math.max(0.01, b.speed);

		if (now < fleeUntil) {
			if (--repath <= 0) {
				repath = 20;
				Vec3 away = DefaultRandomPos.getPosAway(mob, 12, 5, target.position());

				if (away != null) {
					mob.getNavigation().moveTo(away.x, away.y, away.z, speed);
				}
			}

			return;
		}

		if (now >= restUntil && mob.distanceTo(target) <= b.burstRange) {
			state = State.BURST_TELL;
			timer = Math.max(1, b.burstTellTicks);
			mob.getNavigation().stop();
			mob.setAction(SandScour.ACTION_BURST);
			mob.tellLog().tell(now, b.burstTellTicks);
			EffectEvents.toTracking(mob, ScourEffects.EVENT, mob.position(), null, mob.getId(), b.burstTellTicks, ScourEffects.BURST_TELL);
			level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), ModSounds.SCOUR_BURST_TELL, mob.getSoundSource(), 1f, mob.getVoicePitch());
			return;
		}

		if (--repath <= 0) {
			repath = 10;
			mob.getNavigation().moveTo(target, speed);
		}
	}

	/** Out of the sand in a spray: the told bite (6 and Slowness II) if its target is near enough. */
	private void burst(ServerLevel level, LivingEntity target, ScourBalance b, long now) {
		mob.setBurrowed(false);
		mob.setAction(SandScour.ACTION_NONE);
		mob.tellLog().heavy(now);
		EffectEvents.toTracking(mob, ScourEffects.EVENT, mob.position(), null, mob.getId(), mob.getBbWidth(), ScourEffects.BURST);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), ModSounds.SCOUR_BURST, mob.getSoundSource(), 1.2f, mob.getVoicePitch());
		state = State.SURFACED;
		surfaceUntil = now + Math.max(1, b.surfaceTicks);

		if (target != null && target.isAlive()) {
			Vec3 toward = target.position().subtract(mob.position()).multiply(1, 0, 1);

			if (toward.lengthSqr() > 1.0e-4) {
				mob.setDeltaMovement(toward.normalize().scale(0.45).add(0, 0.35, 0));
				mob.hurtMarked = true;
			}

			if (mob.distanceTo(target) <= b.biteReach + BURST_LUNGE) {
				bite(level, target, b);
			}
		}

		nextBite = now + Math.max(1, b.biteCooldownTicks);
	}

	// ---------------------------------------------------------------- above ground

	private void surfaced(ServerLevel level, LivingEntity target, ScourBalance b, long now) {
		boolean low = mob.getHealth() < mob.getMaxHealth() * b.fleeBelow;

		if (!low) {
			fled = false;
		}

		if (low && !fled && !mob.isMatriarch()) {
			fled = true;
			fleeUntil = now + Math.max(1, b.fleeTicks);
			burrow(level, b, now);
			return;
		}

		if (now >= surfaceUntil) {
			burrow(level, b, now);
			return;
		}

		if (target == null) {
			mob.getNavigation().stop();
			return;
		}

		mob.getLookControl().setLookAt(target, 30f, 30f);
		double distance = mob.distanceTo(target);

		if (mob.isMatriarch() && now >= nextSweep && distance <= b.sweepRadius + 1) {
			state = State.SWEEP_TELL;
			timer = Math.max(1, b.sweepTellTicks);
			mob.getNavigation().stop();
			mob.setAction(SandScour.ACTION_SWEEP);
			mob.tellLog().tell(now, b.sweepTellTicks);
			EffectEvents.toTracking(mob, ScourEffects.EVENT, mob.position(), facing(), mob.getId(), b.sweepTellTicks, ScourEffects.SWEEP_TELL);
			level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), ModSounds.SCOUR_MATRIARCH_SWEEP, mob.getSoundSource(), 1f, 0.8f);
			return;
		}

		if (distance <= b.biteReach * mob.getScale() && now >= nextBite) {
			nextBite = now + Math.max(1, b.biteCooldownTicks);
			bite(level, target, b);
		}

		if (distance > b.biteReach * 0.8 * mob.getScale() && --repath <= 0) {
			repath = 8;
			mob.getNavigation().moveTo(target, 1.15);
		}
	}

	/** A bite: 6 and Slowness II for 40 ticks. */
	private void bite(ServerLevel level, LivingEntity target, ScourBalance b) {
		mob.swing(InteractionHand.MAIN_HAND);

		if (target.hurtServer(level, mob.damageSources().mobAttack(mob), b.biteDamage)) {
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, b.biteSlowTicks, b.biteSlowAmplifier), mob);
		}
	}

	/** It sinks, and the sand slumps in over it; it stays down at least {@code burrowTicks}. */
	private void burrow(ServerLevel level, ScourBalance b, long now) {
		mob.setBurrowed(true);
		mob.setAction(SandScour.ACTION_NONE);
		mob.getNavigation().stop();
		EffectEvents.toTracking(mob, ScourEffects.EVENT, mob.position(), null, mob.getId(), mob.getBbWidth(), ScourEffects.BURROW);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), ModSounds.SCOUR_BURROW, mob.getSoundSource(), 1f, mob.getVoicePitch());
		state = State.BURROWED;
		restUntil = now + Math.max(1, b.burrowTicks);
		repath = 0;
	}

	// ---------------------------------------------------------------- the matriarch

	/** The tail lashes round: 8 and knock-back to everything behind and beside her within the radius (never her brood). */
	private void sweep(ServerLevel level, ScourBalance b, long now) {
		mob.setAction(SandScour.ACTION_NONE);
		mob.tellLog().heavy(now);
		nextSweep = now + Math.max(1, b.sweepIntervalTicks);
		state = State.SURFACED;
		Vec3 facing = facing();
		Vec3 at = mob.position();
		EffectEvents.toTracking(mob, ScourEffects.EVENT, at, facing, mob.getId(), b.sweepRadius, ScourEffects.SWEEP);
		level.playSound(null, at.x, at.y, at.z, ModSounds.SCOUR_MATRIARCH_SWEEP, mob.getSoundSource(), 1.3f, 1.1f);
		double r = b.sweepRadius;
		List<LivingEntity> hits = level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(r + 1, 2, r + 1),
				e -> e != mob && e.isAlive() && !(e instanceof SandScour) && !FoeTargets.ignored(e));

		for (LivingEntity hit : hits) {
			Vec3 to = hit.position().subtract(at).multiply(1, 0, 1);
			double reach = r + hit.getBbWidth() * 0.5;

			if (to.lengthSqr() > reach * reach || to.lengthSqr() > 1.0e-4 && to.normalize().dot(facing) > SWEEP_FRONT_DOT) {
				continue;
			}

			if (hit.hurtServer(level, mob.damageSources().mobAttack(mob), b.sweepDamage)) {
				Knock.away(hit, at, b.sweepKnockback, 0.3);
			}
		}
	}

	/** Every {@code broodIntervalTicks} in a fight she calls {@code broodCount} scours up, while fewer than {@code broodMax} live near her. */
	private void tickBrood(ServerLevel level, ScourBalance b, long now) {
		if (nextBrood < 0) {
			nextBrood = now + b.broodIntervalTicks / 2;
			return;
		}

		if (now < nextBrood) {
			return;
		}

		nextBrood = now + Math.max(1, b.broodIntervalTicks);
		int alive = level.getEntitiesOfClass(SandScour.class, mob.getBoundingBox().inflate(BROOD_RANGE), s -> s != mob && s.isAlive() && !s.isMatriarch()).size();
		int count = Math.min(b.broodCount, b.broodMax - alive);

		if (count <= 0) {
			return;
		}

		EffectEvents.toTracking(mob, ScourEffects.EVENT, mob.position(), null, mob.getId(), count, ScourEffects.CALL);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), ModSounds.SCOUR_MATRIARCH_CALL, mob.getSoundSource(), 1.5f, 0.9f);

		for (int i = 0; i < count; i++) {
			double angle = mob.getRandom().nextDouble() * Math.PI * 2;
			double d = 2.5 + mob.getRandom().nextDouble() * 2;
			BlockPos at = BlockPos.containing(mob.getX() + Math.cos(angle) * d, mob.getY(), mob.getZ() + Math.sin(angle) * d);
			Scours.spawn(level, at, false, null);
		}
	}

	/** Her facing along the ground. */
	private Vec3 facing() {
		float yaw = mob.yBodyRot * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
	}
}
