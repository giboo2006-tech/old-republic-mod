package com.oldrepublic.force;

/**
 * Tunable Force Push numbers, stored in the {@code force_push} section of
 * {@code config/oldrepublic/balance.json}. Angles are half-angles in degrees (a 70 degree cone has
 * a half-angle of 35), distances in blocks, durations in ticks.
 */
public final class PushBalance {
	// Charge
	public int minChargeTicks = 5;
	public int fullChargeTicks = 20;
	/** Holding longer than this cancels the push with no cost, so a charge can never hang. */
	public int maxHoldTicks = 100;

	// Base push
	public float baseHalfAngle = 35f;
	public float baseRange = 6f;
	public float knockbackMin = 1.1f;
	public float knockbackMax = 1.8f;
	public float costMin = 18f;
	public float costMax = 28f;
	public int cooldownTicks = 70;
	/** Fraction of the impulse lost at the edge of the range. */
	public float distanceFalloff = 0.35f;
	/** Upward lift added to living targets so they leave the ground, plus the charge bonus. */
	public float lift = 0.18f;
	public float liftPerCharge = 0.12f;
	/** How strongly the knockback resistance attribute reduces the push (0 ignores it). */
	public float knockbackResistanceWeight = 0.6f;

	// Repulse Wave
	public float repulseHalfAngle = 65f;
	public float repulseRange = 7f;
	public float repulseKnockback = 0.8f;
	public int repulseStaggerTicks = 30;
	public int repulseStaggerAmplifier = 2;

	// Focused Blast
	public float focusedHalfAngle = 12.5f;
	public float focusedRange = 12f;
	public float focusedKnockback = 1.8f;
	public float slamDamageBase = 4f;
	public float slamDamagePerCharge = 4f;
	/** Minimum horizontal speed (blocks per tick) at the moment of hitting a wall. */
	public float slamMinSpeed = 0.35f;
	public int slamWindowTicks = 12;

	// Deflecting Pulse
	public float deflectRadius = 4f;
	public float deflectKnockback = 0.6f;
	public float deflectCooldownMult = 0.6f;

	// Resistance
	public float resistantKnockbackMult = 0.4f;
	/** Within this window each further stagger on a push-resistant target is halved; the third is ignored. */
	public int staggerDrWindowTicks = 200;

	// Mastery
	public float masteryRangePerRank = 0.04f;
	public float masteryCostPerRank = 0.05f;

	// Other targets
	public float itemKnockbackMult = 1.0f;
	/** Minimum speed (blocks per tick) of a pushed or reflected projectile. */
	public float projectilePushSpeed = 1.4f;
	/** Players are affected only when this is on and the server allows PvP. */
	public boolean pvpEffects = false;

	// Milestone
	public int firstPushResonance = 250;
}
