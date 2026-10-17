package com.oldrepublic.force.lift;

import com.oldrepublic.skill.SkillBalance;

import net.minecraft.util.Mth;

/** Pure rules of Telekinetic Lift (spec 10.2), shared by the power, the tooltips and the tests. */
public final class LiftRules {
	/** The height Lift asks for: a boss's cap, a push-resistant target's, or the normal height. */
	public static float requested(boolean pushResistant, boolean boss, LiftBalance b) {
		return boss ? b.bossHeight : pushResistant ? b.resistantHeight : b.height;
	}

	/**
	 * How high a target rises: the requested height capped by what the reaction handlers allow
	 * ({@code handlerHeight}, from {@code ForceReactions.liftHeight}). 0 or less refuses the lift.
	 */
	public static float height(boolean pushResistant, boolean boss, float handlerHeight, LiftBalance b) {
		float height = Math.min(requested(pushResistant, boss, b), handlerHeight);
		return height > 0f ? height : 0f;
	}

	/** The longest hold: 60 ticks, or 100 with Suspend. */
	public static int holdTicks(boolean suspend, LiftBalance b) {
		return suspend ? b.suspendHoldTicks : b.maxHoldTicks;
	}

	/**
	 * An energy amount after mastery (-5% per rank) and {@code archivists_road} (-10%), before the
	 * skill, alignment and other features' multipliers.
	 */
	public static float cost(float base, int masteryRank, boolean archivistsRoad, LiftBalance b, SkillBalance s) {
		float mastery = Math.max(0f, 1f - b.masteryCostPerRank * Mth.clamp(masteryRank, 0, 3));
		return base * mastery * (archivistsRoad ? Math.max(0f, 1f - s.archivistsRoadLiftCost) : 1f);
	}

	/** Reach after mastery (+1 block per rank). */
	public static float range(int masteryRank, LiftBalance b) {
		return b.range + b.masteryRangePerRank * Mth.clamp(masteryRank, 0, 3);
	}

	/** Whether a creature of this height can be lifted at all. */
	public static boolean liftable(float bbHeight, LiftBalance b) {
		return bbHeight <= b.maxTargetHeight + 1.0e-3f;
	}

	/** Horizontal launch speed that carries a thrown target about {@code distance} blocks through air drag. */
	public static double hurlSpeed(float distance) {
		// Airborne living entities keep 0.91 of their horizontal speed per tick: distance = v / (1 - 0.91).
		return distance * (1.0 - 0.91);
	}

	private LiftRules() {
	}
}
