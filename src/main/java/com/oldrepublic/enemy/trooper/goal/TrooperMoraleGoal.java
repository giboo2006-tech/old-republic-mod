package com.oldrepublic.enemy.trooper.goal;

import java.util.EnumSet;

import com.oldrepublic.enemy.trooper.TrooperEntity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * The squad breaks (spec 7.5): with its sergeant dead and half of it down, each remaining member
 * falls back {@code moraleRetreatDistance} blocks for {@code moraleRetreatTicks} without firing,
 * then returns to the fight. The trooper itself decides when its squad breaks (once per
 * encounter); this goal runs the retreat.
 */
public final class TrooperMoraleGoal extends Goal {
	private static final int REPATH_TICKS = 20;
	private static final double SPEED = 1.25;
	private static final double ARRIVED = 2;

	private final TrooperEntity trooper;
	private int repath;

	public TrooperMoraleGoal(TrooperEntity trooper) {
		this.trooper = trooper;
		setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		return trooper.isRetreating() && trooper.canFight();
	}

	@Override
	public boolean canContinueToUse() {
		return canUse();
	}

	@Override
	public void start() {
		repath = 0;
		trooper.setAggressive(false);
	}

	@Override
	public void stop() {
		trooper.getNavigation().stop();
	}

	@Override
	public void tick() {
		Vec3 retreat = trooper.retreatTarget();

		if (retreat == null) {
			return;
		}

		LivingEntity threat = trooper.getTarget();

		if (threat != null) {
			trooper.getLookControl().setLookAt(threat, 30f, 30f);
		}

		if (trooper.position().distanceToSqr(retreat) <= ARRIVED * ARRIVED || --repath > 0) {
			return;
		}

		repath = REPATH_TICKS;
		Vec3 leg = TrooperPaths.legToward(trooper, retreat);
		trooper.getNavigation().moveTo(leg.x, leg.y, leg.z, SPEED);
	}
}
