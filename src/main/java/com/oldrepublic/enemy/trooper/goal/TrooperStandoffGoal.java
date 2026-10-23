package com.oldrepublic.enemy.trooper.goal;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.enemy.trooper.ColdPeace;
import com.oldrepublic.enemy.trooper.Squads;
import com.oldrepublic.enemy.trooper.TrooperAction;
import com.oldrepublic.enemy.trooper.TrooperBalance;
import com.oldrepublic.enemy.trooper.TrooperEntity;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

/**
 * The cold-peace standoff (spec 7.5) for each member of a squad whose leader saw an untagged patrol
 * of the other army within {@code standoffRange} ({@code TrooperSocial} runs
 * {@link ColdPeace#standoffStep} on the leader). While halted, the trooper stops, faces the rival
 * with its rifle lowered (the STANDOFF pose) and steps back if it stands closer than
 * {@code standoffDistance}. Walking on, it only keeps that distance; the route and following take
 * over otherwise. A fight ends the standoff at once.
 */
public final class TrooperStandoffGoal extends Goal {
	private static final int STEP_BACK_TICKS = 20;
	private static final double STEP_BACK_SPEED = 0.9;

	private final TrooperEntity trooper;
	private int stepBack;

	public TrooperStandoffGoal(TrooperEntity trooper) {
		this.trooper = trooper;
		setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (!trooper.canFight() || trooper.getTarget() != null) {
			return false;
		}

		return switch (Squads.standoffPhase(trooper)) {
			case HALTED -> true;
			case WALKING_ON -> tooClose(Squads.standoffRival(trooper));
			case NONE -> false;
		};
	}

	@Override
	public boolean canContinueToUse() {
		return canUse();
	}

	@Override
	public void start() {
		stepBack = 0;
		trooper.getNavigation().stop();
	}

	@Override
	public void stop() {
		if (trooper.action() == TrooperAction.STANDOFF) {
			trooper.setAction(TrooperAction.IDLE);
		}
	}

	@Override
	public void tick() {
		Vec3 rival = Squads.standoffRival(trooper);
		boolean halted = Squads.standoffPhase(trooper) == ColdPeace.Phase.HALTED;

		if (rival != null) {
			trooper.getLookControl().setLookAt(rival.add(0, 1.6, 0));
		}

		if (halted) {
			trooper.setAction(TrooperAction.STANDOFF);
		} else if (trooper.action() == TrooperAction.STANDOFF) {
			trooper.setAction(TrooperAction.IDLE);
		}

		if (tooClose(rival)) {
			if (--stepBack <= 0) {
				stepBack = STEP_BACK_TICKS;
				Vec3 away = DefaultRandomPos.getPosAway(trooper, 6, 3, rival);

				if (away != null) {
					trooper.getNavigation().moveTo(away.x, away.y, away.z, STEP_BACK_SPEED);
				}
			}
		} else if (halted) {
			trooper.getNavigation().stop();
		}
	}

	private boolean tooClose(@Nullable Vec3 rival) {
		double distance = TrooperBalance.get().standoffDistance;
		return rival != null && trooper.position().distanceToSqr(rival) < distance * distance;
	}
}
