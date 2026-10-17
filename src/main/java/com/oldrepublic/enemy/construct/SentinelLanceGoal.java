package com.oldrepublic.enemy.construct;

import java.util.EnumSet;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Starts the Ruin Sentinel's lance (spec 7.7): a target it can see between {@code lanceRange[0]} and
 * {@code lanceRange[1]} blocks, off cooldown, while it is free to act. The sentinel then stands and
 * tracks through the 24-tick tell ({@link RuinSentinel#beginLance}); this goal holds movement and
 * look until the bolt is away or the tell is cancelled.
 */
final class SentinelLanceGoal extends Goal {
	private final RuinSentinel sentinel;

	SentinelLanceGoal(RuinSentinel sentinel) {
		this.sentinel = sentinel;
		setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		LivingEntity target = sentinel.getTarget();

		if (target == null || !target.isAlive() || target.isSpectator() || !sentinel.isReady()
				|| sentinel.level().getGameTime() < sentinel.lanceReadyAt()) {
			return false;
		}

		SentinelBalance b = SentinelBalance.get();
		double distance = sentinel.distanceTo(target);
		return distance >= b.lanceMin() && distance <= b.lanceMax() && sentinel.getSensing().hasLineOfSight(target);
	}

	@Override
	public boolean canContinueToUse() {
		return sentinel.action() == RuinSentinel.Action.LANCE_TELL && !sentinel.isDeadOrDying();
	}

	@Override
	public void start() {
		LivingEntity target = sentinel.getTarget();
		sentinel.getNavigation().stop();

		if (target != null) {
			sentinel.beginLance(target);
		}
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		sentinel.getNavigation().stop();
	}
}
