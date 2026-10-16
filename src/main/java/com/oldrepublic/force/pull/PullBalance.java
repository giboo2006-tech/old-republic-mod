package com.oldrepublic.force.pull;

/**
 * Tunable Force Pull numbers, stored in the {@code force_pull} section of
 * {@code config/oldrepublic/balance.json}. Distances in blocks, speeds in blocks per tick, durations
 * in ticks, angles are half-angles in degrees.
 */
public final class PullBalance {
	public float cost = 16f;
	public int cooldownTicks = 120;
	/** The visible reach before the pull lands. */
	public int windupTicks = 3;

	public float range = 12f;
	/** Cone around the crosshair used when no entity is on the ray. */
	public float aimAssistHalfAngle = 8f;

	public float speedBase = 0.35f;
	public float speedPerBlock = 0.11f;
	public float speedMax = 1.6f;
	public float lift = 0.25f;
	/** Extra lift per block the target is below the caster. */
	public float liftPerBlockAbove = 0.03f;

	/** Within this distance of the caster a pulled target is braked so it lands at the caster's feet. */
	public float brakeDistance = 2.5f;
	public float brakeFactor = 0.3f;
	public int brakeWindowTicks = 20;

	/** Radius around the ray end in which loose items are pulled when there is no living target. */
	public float itemRadius = 2.5f;

	/** Multiplier for a target that guards with a saber and faces the caster. */
	public float guardingMult = 0.5f;
	public float knockbackResistanceWeight = 0.6f;

	public float masteryRangePerRank = 0.05f;
	public float masteryCostPerRank = 0.05f;

	// Disarming Pull
	public float disarmKnockMult = 0.6f;
	public int disarmTicks = 120;
	public float acolyteGuardDrain = 40f;
	public int acolyteNoGuardTicks = 40;
	public int pvpItemCooldownTicks = 40;

	// Reeling Pull
	public float reelKnockMult = 1.2f;
	public int reelStaggerTicks = 20;
	public float reelBonus = 0.30f;
	public int reelWindowTicks = 30;

	/** Players are affected only when this is on and the server allows PvP. */
	public boolean pvpEffects = false;
}
