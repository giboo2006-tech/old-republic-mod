package com.oldrepublic.enemy.trooper.goal;

import java.util.EnumSet;

import com.oldrepublic.enemy.trooper.ColdPeace;
import com.oldrepublic.enemy.trooper.Squads;
import com.oldrepublic.enemy.trooper.TrooperEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * A patrol leader walks its route point by point (spec 7.6, {@code Troopers.startPatrol}); the rest
 * of the squad follows it ({@link TrooperFollowGoal}). Long legs are walked in pathfinder-sized
 * steps. The walk pauses while the squad fights or stands off against a rival patrol, and ends at
 * the last point (the patrol spawner removes the squad once nobody is near).
 */
public final class TrooperRouteGoal extends Goal {
	/** A route point counts as reached within this many blocks (horizontally). */
	private static final double REACHED = 2.5;
	private static final int REPATH_TICKS = 20;
	private static final double SPEED = 0.8;

	private final TrooperEntity trooper;
	private int repath;

	public TrooperRouteGoal(TrooperEntity trooper) {
		this.trooper = trooper;
		setFlags(EnumSet.of(Goal.Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		return trooper.canFight() && trooper.getTarget() == null && trooper.routePoint() != null
				&& Squads.standoffPhase(trooper) != ColdPeace.Phase.HALTED;
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
	public void tick() {
		BlockPos point = trooper.routePoint();

		if (point == null) {
			return;
		}

		Vec3 target = Vec3.atBottomCenterOf(point);
		double dx = target.x - trooper.getX();
		double dz = target.z - trooper.getZ();

		if (dx * dx + dz * dz <= REACHED * REACHED) {
			trooper.advanceRoute();
			repath = 0;
			return;
		}

		if (--repath > 0 && !trooper.getNavigation().isDone()) {
			return;
		}

		repath = REPATH_TICKS;
		Vec3 leg = TrooperPaths.legToward(trooper, target);
		trooper.getNavigation().moveTo(leg.x, leg.y, leg.z, SPEED);
	}
}
