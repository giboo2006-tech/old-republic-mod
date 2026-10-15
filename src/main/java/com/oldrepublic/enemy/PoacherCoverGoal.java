package com.oldrepublic.enemy;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * When a poacher is badly hurt and was just hit, it runs to a nearby spot the target cannot see
 * (a block between the target's eyes and the poacher's chest), waits there briefly, then lets
 * {@link PoacherCombatGoal} take over again, which peeks out to regain a firing line. If no such
 * spot is reachable the goal simply does not start.
 */
final class PoacherCoverGoal extends Goal {
	private static final int RECENT_HIT_TICKS = 40;
	private static final int SAMPLES = 12;
	/** Gives up running for cover after this long. */
	private static final int MAX_TICKS = 120;

	private final CrystalPoacher mob;
	private @Nullable Path path;
	private int nextAllowedTick;
	private int holdTicks;
	private int runningTicks;

	PoacherCoverGoal(CrystalPoacher mob) {
		this.mob = mob;
		setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		LivingEntity target = mob.getTarget();

		if (target == null || !target.isAlive() || mob.tickCount < nextAllowedTick) {
			return false;
		}

		EnemyBalance b = EnemyBalance.get();

		if (mob.getHealth() >= mob.getMaxHealth() * b.coverHealthFraction || mob.tickCount - mob.getLastHurtByMobTimestamp() > RECENT_HIT_TICKS) {
			return false;
		}

		path = findCover(target);

		if (path == null) {
			// Nothing feasible nearby; try again a little later rather than every tick.
			nextAllowedTick = mob.tickCount + 20;
		}

		return path != null;
	}

	@Override
	public boolean canContinueToUse() {
		LivingEntity target = mob.getTarget();
		return target != null && target.isAlive() && runningTicks < MAX_TICKS && holdTicks != 0;
	}

	@Override
	public void start() {
		mob.setCharging(false);
		mob.setAggressive(true);
		mob.getNavigation().moveTo(path, EnemyBalance.get().coverSpeed);
		holdTicks = -1;
		runningTicks = 0;
	}

	@Override
	public void stop() {
		EnemyBalance b = EnemyBalance.get();
		nextAllowedTick = mob.tickCount + b.coverCooldownTicks;
		mob.getNavigation().stop();
		path = null;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		LivingEntity target = mob.getTarget();

		if (target != null) {
			mob.getLookControl().setLookAt(target, 30f, 30f);
		}

		if (holdTicks < 0) {
			runningTicks++;

			if (mob.getNavigation().isDone()) {
				EnemyBalance b = EnemyBalance.get();
				holdTicks = b.coverHoldMinTicks + mob.getRandom().nextInt(Math.max(1, b.coverHoldMaxTicks - b.coverHoldMinTicks + 1));
			}
		} else if (holdTicks > 0) {
			holdTicks--;
		}
	}

	/** The nearest reachable spot within about 8 blocks that the target cannot see. */
	private @Nullable Path findCover(LivingEntity target) {
		Vec3 targetEye = target.getEyePosition();
		Path best = null;
		double bestDistance = Double.MAX_VALUE;

		for (int i = 0; i < SAMPLES; i++) {
			Vec3 candidate = (i & 1) == 0
					? DefaultRandomPos.getPosAway(mob, 8, 4, target.position())
					: LandRandomPos.getPos(mob, 8, 4);

			if (candidate == null) {
				continue;
			}

			Vec3 chest = candidate.add(0, 1.4, 0);
			HitResult hit = mob.level().clip(new ClipContext(targetEye, chest, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, target));

			if (hit.getType() != HitResult.Type.BLOCK) {
				continue;
			}

			double distance = mob.distanceToSqr(candidate);

			if (distance >= bestDistance) {
				continue;
			}

			Path candidatePath = mob.getNavigation().createPath(BlockPos.containing(candidate), 0);

			if (candidatePath != null && candidatePath.canReach()) {
				best = candidatePath;
				bestDistance = distance;
			}
		}

		return best;
	}
}
