package com.oldrepublic.enemy.droid;

import java.util.EnumSet;

import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * The security droid's fight (spec MILESTONE_4.md 10.2): a slow advance into its band, then a
 * 20-tick barrel spin (the tell) and a volley of 8 bolts over 40 ticks from alternating arms, then
 * an 80-tick cooldown. A stagger or shock stops the spin; while staggered it does not start one.
 */
final class DroidCombatGoal extends Goal {
	private enum Phase {
		READY,
		SPIN,
		VOLLEY,
		COOLDOWN
	}

	private final SecurityDroid mob;
	private Phase phase = Phase.READY;
	private int timer;
	private int shotsLeft;
	private int shotGap;
	private int repath;
	private boolean left;
	private boolean alerted;

	DroidCombatGoal(SecurityDroid mob) {
		this.mob = mob;
		setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		LivingEntity target = mob.getTarget();
		return target != null && target.isAlive();
	}

	@Override
	public boolean canContinueToUse() {
		return canUse();
	}

	@Override
	public void start() {
		mob.setAggressive(true);
		phase = Phase.READY;
		timer = 10;

		if (!alerted) {
			alerted = true;
			mob.playSound(ModSounds.DROID_ALERT, 1f, 1f);
		}
	}

	@Override
	public void stop() {
		mob.setAggressive(false);
		mob.setSpinning(false);
		mob.setFiring(false);
		mob.getNavigation().stop();
		phase = Phase.READY;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		LivingEntity target = mob.getTarget();

		if (target == null || !(mob.level() instanceof ServerLevel level)) {
			return;
		}

		DroidBalance b = DroidBalance.get();
		mob.getLookControl().setLookAt(target, 20f, 30f);
		double distance = mob.distanceTo(target);
		boolean sees = mob.getSensing().hasLineOfSight(target);

		if (mob.consumeInterrupt() && (phase == Phase.SPIN || phase == Phase.VOLLEY)) {
			mob.setSpinning(false);
			mob.setFiring(false);
			phase = Phase.COOLDOWN;
			timer = b.volleyCooldownTicks / 2;
		}

		move(target, distance, sees, b);

		switch (phase) {
			case READY -> {
				if (--timer <= 0 && sees && distance <= b.maxRange + 4 && !mob.isStaggered()) {
					phase = Phase.SPIN;
					timer = Math.max(1, b.spinTellTicks);
					mob.setSpinning(true);
					EffectEvents.toTracking(mob, DroidEffects.TELL, mob.barrel(false), null, mob.getId(), timer, DroidEffects.SPIN);
				}
			}
			case SPIN -> {
				if (--timer <= 0) {
					phase = Phase.VOLLEY;
					shotsLeft = Math.max(1, b.volleyBolts);
					shotGap = Math.max(1, b.volleyTicks / shotsLeft);
					timer = 0;
					mob.setSpinning(false);
					mob.setFiring(true);
				}
			}
			case VOLLEY -> {
				if (--timer <= 0) {
					timer = shotGap;
					left = !left;
					mob.fireBolt(level, target, left, b);

					if (--shotsLeft <= 0) {
						mob.setFiring(false);
						phase = Phase.COOLDOWN;
						timer = b.volleyCooldownTicks;
					}
				}
			}
			case COOLDOWN -> {
				if (--timer <= 0) {
					phase = Phase.READY;
				}
			}
		}
	}

	/** Advances slowly while far or blind; holds still (planted) while spinning and firing. */
	private void move(LivingEntity target, double distance, boolean sees, DroidBalance b) {
		if (phase == Phase.SPIN || phase == Phase.VOLLEY || sees && distance <= b.maxRange && distance >= b.minRange) {
			mob.getNavigation().stop();
			return;
		}

		if (distance < b.minRange) {
			mob.getMoveControl().strafe(-0.5f, 0f);
			return;
		}

		if (--repath <= 0) {
			repath = 15;
			mob.getNavigation().moveTo(target, 1.0);
		}
	}
}
