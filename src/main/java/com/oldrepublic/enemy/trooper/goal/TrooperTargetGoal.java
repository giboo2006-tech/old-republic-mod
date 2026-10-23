package com.oldrepublic.enemy.trooper.goal;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.enemy.trooper.BarkCategory;
import com.oldrepublic.enemy.trooper.Barks;
import com.oldrepublic.enemy.trooper.Squads;
import com.oldrepublic.enemy.trooper.TrooperBalance;
import com.oldrepublic.enemy.trooper.TrooperEntity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;

/**
 * Picks what a trooper fights (spec 5.2, 7.5, 7.8), using {@link TrooperEntity#wants}: players by
 * attitude (HOSTILE on sight within {@code hostileRange}; provoked, engaged and incident players
 * wherever they are within follow range), poachers, bound and awake constructs on sight, the other army
 * only without the cold peace or under an incident, and whatever hurt the trooper or the player it
 * defends. A new target is shared with the squad and the spotter barks. A target the trooper no
 * longer wants (an expired provocation, a player gone creative, Peaceful) is dropped.
 */
public final class TrooperTargetGoal extends Goal {
	/** Ticks between scans for a new target. */
	private static final int SCAN_INTERVAL = 10;
	/** A target farther than follow range plus this is dropped. */
	private static final double LOSE_MARGIN = 8;

	private final TrooperEntity trooper;
	private long nextScan;
	private @Nullable LivingEntity found;

	public TrooperTargetGoal(TrooperEntity trooper) {
		this.trooper = trooper;
		setFlags(EnumSet.of(Goal.Flag.TARGET));
	}

	@Override
	public boolean canUse() {
		LivingEntity current = trooper.getTarget();

		if (current != null && !keeps(current)) {
			trooper.setTarget(null);
			current = null;
		}

		if (current != null || !trooper.canFight()) {
			return false;
		}

		long now = trooper.level().getGameTime();

		if (now < nextScan) {
			return false;
		}

		nextScan = now + SCAN_INTERVAL;
		found = find();
		return found != null;
	}

	@Override
	public boolean canContinueToUse() {
		// One-shot: the target is set in start() and checked again by canUse().
		return false;
	}

	@Override
	public void start() {
		LivingEntity target = found;
		found = null;

		if (target == null) {
			return;
		}

		trooper.setTarget(target);
		Squads.shareTarget(trooper, target);
		Barks.say(trooper, BarkCategory.SPOT);
	}

	/** Whether the current target is still worth fighting. */
	private boolean keeps(LivingEntity target) {
		if (!trooper.canFight() || target.level() != trooper.level() || !trooper.wants(target)) {
			return false;
		}

		double range = TrooperBalance.get().followRange + LOSE_MARGIN;
		return trooper.distanceToSqr(target) <= range * range;
	}

	/** The nearest living thing this trooper wants: on-sight targets only when seen within {@code hostileRange}. */
	private @Nullable LivingEntity find() {
		TrooperBalance b = TrooperBalance.get();
		double followSq = (double) b.followRange * b.followRange;
		double hostileSq = (double) b.hostileRange * b.hostileRange;
		LivingEntity best = null;
		double bestDistance = Double.MAX_VALUE;
		AABB box = trooper.getBoundingBox().inflate(b.followRange);

		for (LivingEntity candidate : trooper.level().getEntitiesOfClass(LivingEntity.class, box, e -> e != trooper && trooper.wants(e))) {
			double distance = trooper.distanceToSqr(candidate);

			if (distance > followSq || distance >= bestDistance) {
				continue;
			}

			if (trooper.onSightOnly(candidate) && (distance > hostileSq || !trooper.getSensing().hasLineOfSight(candidate))) {
				continue;
			}

			best = candidate;
			bestDistance = distance;
		}

		return best;
	}
}
