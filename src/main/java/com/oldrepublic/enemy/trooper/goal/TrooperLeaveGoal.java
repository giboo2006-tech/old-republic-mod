package com.oldrepublic.enemy.trooper.goal;

import java.util.EnumSet;

import com.oldrepublic.enemy.trooper.TrooperEntity;
import com.oldrepublic.enemy.trooper.TrooperSight;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * A trooper walking off: a tended trooper going home to its army's outpost, a spared, interrogated
 * or fled sergeant, a handed-over prisoner nobody came for (spec 5.4, 5.5, 7.1). It walks toward its
 * destination in steps the pathfinder can manage and is removed once no player can see it, after
 * a short first stretch or at its destination. Leaving troopers are never saved, so an unloaded
 * chunk ends the walk too.
 */
public final class TrooperLeaveGoal extends Goal {
	/** Ticks of walking before it may vanish on the way. */
	private static final int MIN_WALK_TICKS = 40;
	private static final double ARRIVED = 3;
	private static final int REPATH_TICKS = 20;
	private static final double SPEED = 0.9;

	private final TrooperEntity trooper;
	private int repath;

	public TrooperLeaveGoal(TrooperEntity trooper) {
		this.trooper = trooper;
		setFlags(EnumSet.of(Goal.Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		return trooper.isLeaving() && trooper.isAlive() && !trooper.isYielded();
	}

	@Override
	public boolean canContinueToUse() {
		return canUse();
	}

	@Override
	public void start() {
		repath = 0;
	}

	@Override
	public void stop() {
		trooper.getNavigation().stop();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		if (!(trooper.level() instanceof ServerLevel level)) {
			return;
		}

		Vec3 destination = trooper.leaveTarget();
		Vec3 here = trooper.position();
		boolean arrived = destination == null || here.multiply(1, 0, 1).distanceToSqr(destination.multiply(1, 0, 1)) <= ARRIVED * ARRIVED;
		long walked = level.getGameTime() - trooper.leaveSince();

		if ((arrived || walked >= MIN_WALK_TICKS) && TrooperSight.unseen(level, trooper)) {
			trooper.discard();
			return;
		}

		if (arrived || --repath > 0) {
			return;
		}

		repath = REPATH_TICKS;
		Vec3 leg = TrooperPaths.legToward(trooper, destination);
		trooper.getNavigation().moveTo(leg.x, leg.y, leg.z, SPEED);
	}
}
