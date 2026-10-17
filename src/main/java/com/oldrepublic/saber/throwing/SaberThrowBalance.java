package com.oldrepublic.saber.throwing;

import com.oldrepublic.config.BalanceConfig;

/** Saber Throw numbers, section {@code saber_throw} of {@code config/oldrepublic/balance.json}. Server only. */
public final class SaberThrowBalance {
	public float cost = 20f;
	/** Counted from the catch. */
	public int cooldownTicks = 160;
	/** Blocks per tick outward and on the way back. */
	public float speed = 1.3f;
	public float returnSpeed = 1.5f;
	/** Outward range in blocks before it turns back. */
	public float range = 14f;
	/** A saber still out after this many ticks snaps back to the hand. */
	public int maxTicks = 60;
	/** Damage as a fraction of the saber's ignited damage. */
	public float damageMult = 0.8f;
	/** Living targets hit at most per throw (out and back). */
	public int maxTargets = 3;
	public float blockDrainPlayer = 15f;
	public float blockDrainNpc = 20f;
	public float ricochetRange = 6f;
	public int ricochetBounces = 2;
	/** Damage lost per bounce. */
	public float ricochetFalloff = 0.2f;
	public float rendingSpeed = 1.0f;
	public float rendingRange = 12f;
	/** Multiplies {@link #damageMult} (0.8 x 1.4 = 1.12). */
	public float rendingDamageMult = 1.4f;
	public int rendingStaggerTicks = 15;
	public float masteryRangePerRank = 0.05f;
	/** A stack marked as thrown with no matching saber in flight is cleared after this many ticks. */
	public int orphanClearTicks = 60;

	public static SaberThrowBalance get() {
		return BalanceConfig.section("saber_throw", SaberThrowBalance.class, SaberThrowBalance::new);
	}
}
