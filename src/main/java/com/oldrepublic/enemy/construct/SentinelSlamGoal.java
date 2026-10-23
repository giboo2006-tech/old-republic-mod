package com.oldrepublic.enemy.construct;

import java.util.EnumSet;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * The Ruin Sentinel's pursuit and slam (spec 7.7): it walks at its target and, within 3 blocks with
 * the slam off cooldown, raises its arms for the 20-tick tell ({@link RuinSentinel#beginSlam}).
 * While any action runs (tell, exposed core, recovery, stagger) it stands still and keeps looking
 * at the target. Runs below {@link SentinelLanceGoal}, which takes over at range.
 */
final class SentinelSlamGoal extends Goal {
	private final RuinSentinel sentinel;
	private int repath;

	SentinelSlamGoal(RuinSentinel sentinel) {
		this.sentinel = sentinel;
		setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		LivingEntity target = sentinel.getTarget();
		return target != null && target.isAlive() && !target.isSpectator() && sentinel.canFight();
	}

	@Override
	public boolean canContinueToUse() {
		return canUse();
	}

	@Override
	public void start() {
		sentinel.setAggressive(true);
		repath = 0;
	}

	@Override
	public void stop() {
		sentinel.setAggressive(false);
		sentinel.getNavigation().stop();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		LivingEntity target = sentinel.getTarget();

		if (target == null) {
			return;
		}

		RuinSentinel.Action action = sentinel.action();

		if (action != RuinSentinel.Action.IDLE) {
			sentinel.getNavigation().stop();

			if (action != RuinSentinel.Action.SLAM_TELL) {
				sentinel.getLookControl().setLookAt(target, 20f, 20f);
			}

			return;
		}

		sentinel.getLookControl().setLookAt(target, 30f, 30f);
		double distance = sentinel.distanceTo(target);
		boolean ready = sentinel.level().getGameTime() >= sentinel.slamReadyAt();

		if (ready && distance <= RuinSentinel.SLAM_START_RANGE && sentinel.getSensing().hasLineOfSight(target)) {
			sentinel.getNavigation().stop();
			sentinel.beginSlam();
			return;
		}

		// Close in (also while the slam cools down, so it is ready when it arrives); hold at arm's length.
		if (distance > RuinSentinel.SLAM_START_RANGE - 1.0) {
			if (--repath <= 0) {
				repath = 10;
				sentinel.getNavigation().moveTo(target, 1.0);
			}
		} else {
			sentinel.getNavigation().stop();
		}
	}
}
