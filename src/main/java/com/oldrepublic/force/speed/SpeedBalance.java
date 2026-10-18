package com.oldrepublic.force.speed;

/**
 * Balance section {@code force_speed} of {@code config/oldrepublic/balance.json} (spec
 * MILESTONE_4.md 9.1 and 14). Durations in ticks, distances in blocks, percentages as 0-100.
 */
public final class SpeedBalance {
	/** Force Energy of a cast. */
	public float cost = 20f;
	/** Cooldown, counted from the end of the run. */
	public int cooldownTicks = 600;
	/** How long the run lasts. */
	public int durationTicks = 160;
	/** Movement speed bonus ({@code ADD_MULTIPLIED_BASE}). */
	public float speedPct = 40f;
	/** Extra step height while the run lasts. */
	public float stepHeight = 0.5f;
	/** How much further a sprint jump carries (the take-off's horizontal speed). */
	public float sprintJumpPct = 20f;
	/** Sprinting hunger is multiplied by this while the run lasts. */
	public float sprintHungerMult = 0.5f;
	/** Burst ({@code speed_burst}): the first ticks run at this bonus, and the cast dashes along the look. */
	public int burstTicks = 20;
	public float burstSpeedPct = 100f;
	public float burstDistance = 6f;
	/** Ticks the burst dash takes to cover its distance. */
	public int burstDashTicks = 4;
	/** Long Road ({@code speed_long_road}): a longer run that costs more. */
	public int longRoadTicks = 300;
	public float longRoadExtraCost = 5f;
	/** Wayfarer ({@code speed_wayfarer}): the chance a projectile aimed at the runner misses, and its largest deflection. */
	public float wayfarerMissChance = 0.25f;
	public float wayfarerMaxDegrees = 1.5f;
	/** Wayfarer: falls shorter than this deal no damage while the run lasts. */
	public float wayfarerSafeFall = 8f;
	/** Mastery: one point per this many blocks run (horizontal, counted on the server). */
	public float masteryBlocks = 200f;
	/** Each mastery rank: this much cheaper and this many ticks longer. */
	public float masteryCostPerRank = 2f;
	public int masteryTicksPerRank = 20;
	/** Speed affects only its caster; kept for the shared balance shape. */
	public boolean pvpEffects = false;
}
