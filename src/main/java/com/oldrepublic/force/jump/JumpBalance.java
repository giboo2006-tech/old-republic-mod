package com.oldrepublic.force.jump;

/**
 * Tunable Force Jump numbers, stored in the {@code force_jump} section of
 * {@code config/oldrepublic/balance.json}. Speeds in blocks per tick, distances in blocks,
 * durations in ticks.
 */
public final class JumpBalance {
	public int minChargeTicks = 4;
	public int fullChargeTicks = 16;
	/** Holding longer than this cancels the leap with no cost. */
	public int maxHoldTicks = 60;
	/** The leap may still start this many ticks after leaving the ground. */
	public int coyoteTicks = 3;

	public float costMin = 14f;
	public float costMax = 24f;
	public int cooldownTicks = 100;

	public float verticalMin = 0.75f;
	public float verticalMax = 1.15f;
	public float horizontalMin = 0.45f;
	public float horizontalMax = 0.9f;

	/** Fall protection covers this leap's apex height plus this many blocks. */
	public float fallProtectionExtra = 4f;
	public int protectionWindowTicks = 80;
	/** Slowness II after landing (skipped while Ataru is the active form). */
	public int landingSlowTicks = 6;

	public float masteryMinDistance = 4f;
	public float masteryHostileRadius = 16f;
	public float masteryCostPerRank = 0.05f;
	public float masteryHeightPerRank = 0.04f;

	// Crushing Descent
	public float crushMinFall = 3f;
	public float crushRadius = 3f;
	public float crushDamageBase = 3f;
	public float crushDamagePerBlock = 0.5f;
	public float crushDamageMax = 8f;
	public float crushKnock = 0.8f;
	public int crushStaggerTicks = 15;
	public float crushResistantMult = 0.5f;

	// Soaring Leap
	public float soarHorizontalMult = 1.35f;
	public float soarVerticalMult = 1.1f;
	public float glideMaxFallSpeed = 0.15f;
	public int glideMaxTicks = 30;
	public float glideCostPerTick = 0.4f;

	/** Players are hit by the Crushing Descent shockwave only when this is on and the server allows PvP. */
	public boolean pvpEffects = false;
}
