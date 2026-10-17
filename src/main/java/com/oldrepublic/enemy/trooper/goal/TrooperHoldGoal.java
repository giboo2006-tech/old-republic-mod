package com.oldrepublic.enemy.trooper.goal;

import java.util.EnumSet;

import com.oldrepublic.enemy.trooper.TrooperEntity;

import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Keeps a trooper standing where it is when it must not walk: yielded (kneeling while its fate is
 * decided), bound and waiting for its escort, or an officer at its post (spec 7.1, 7.4). An officer
 * pushed off its post is let go, so the home restriction walks it back. Looking around still works.
 */
public final class TrooperHoldGoal extends Goal {
	private final TrooperEntity trooper;

	public TrooperHoldGoal(TrooperEntity trooper) {
		this.trooper = trooper;
		setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP));
	}

	@Override
	public boolean canUse() {
		if (!trooper.isAlive() || trooper.isLeaving() || trooper.isEscort()) {
			return false;
		}

		if (trooper.isYielded()) {
			return true;
		}

		if (trooper.isBound()) {
			return !trooper.isEscorted(trooper.level().getGameTime());
		}

		return trooper.isOfficer() && (!trooper.hasHome() || trooper.isWithinHome());
	}

	@Override
	public boolean canContinueToUse() {
		return canUse();
	}

	@Override
	public void start() {
		trooper.getNavigation().stop();
	}

	@Override
	public void tick() {
		if (!trooper.getNavigation().isDone()) {
			trooper.getNavigation().stop();
		}
	}
}
