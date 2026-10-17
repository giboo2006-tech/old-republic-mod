package com.oldrepublic.force;

import net.minecraft.util.Mth;

/**
 * The numbers of one cast, from balance, branch, mastery rank and charge. Shared by the cast, the
 * branch screen data and the tests so they always agree.
 *
 * @param halfAngle     cone half-angle in degrees (ignored when {@code spherical})
 * @param range         reach in blocks (radius for a spherical pulse)
 * @param knockback     impulse in blocks per tick before falloff and resistance
 * @param cost          Force Energy cost
 * @param cooldownTicks cooldown after the cast
 * @param spherical     true for the 360 degree Deflecting Pulse
 */
public record PushShape(float halfAngle, float range, float knockback, float cost, int cooldownTicks, boolean spherical) {
	public static PushShape of(PushBalance b, PushBranch branch, int masteryRank, float charge) {
		float c = Mth.clamp(charge, 0f, 1f);
		float halfAngle = b.baseHalfAngle;
		float range = b.baseRange;
		float knockback = Mth.lerp(c, b.knockbackMin, b.knockbackMax);
		float cost = Mth.lerp(c, b.costMin, b.costMax);
		int cooldown = b.cooldownTicks;
		boolean spherical = false;

		switch (branch) {
			case REPULSE_WAVE -> {
				halfAngle = b.repulseHalfAngle;
				range = b.repulseRange;
				knockback *= b.repulseKnockback;
			}
			case FOCUSED_BLAST -> {
				halfAngle = b.focusedHalfAngle;
				range = b.focusedRange;
				knockback *= b.focusedKnockback;
			}
			case DEFLECTING_PULSE -> {
				halfAngle = 180f;
				range = b.deflectRadius;
				knockback *= b.deflectKnockback;
				cooldown = Math.round(cooldown * b.deflectCooldownMult);
				spherical = true;
			}
			case NONE -> {
			}
		}

		int rank = Mth.clamp(masteryRank, 0, 3);
		range *= 1f + b.masteryRangePerRank * rank;
		cost *= Math.max(0f, 1f - b.masteryCostPerRank * rank);
		return new PushShape(halfAngle, range, knockback, cost, Math.max(1, cooldown), spherical);
	}

	/** Charge 0 to 1 from a hold time in ticks; negative when the hold is below the minimum. */
	public static float chargeFromHold(PushBalance b, long heldTicks) {
		if (heldTicks < b.minChargeTicks) {
			return -1f;
		}

		int span = Math.max(1, b.fullChargeTicks - b.minChargeTicks);
		return Mth.clamp((heldTicks - b.minChargeTicks) / (float) span, 0f, 1f);
	}
}
