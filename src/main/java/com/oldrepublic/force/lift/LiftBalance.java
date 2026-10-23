package com.oldrepublic.force.lift;

/**
 * Balance section {@code force_lift} of {@code config/oldrepublic/balance.json} (spec 10.2 and 14).
 * Durations in ticks, distances and heights in blocks.
 */
public final class LiftBalance {
	/** Force Energy to start the channel. */
	public float startCost = 20f;
	/** Force Energy per second while the channel holds. */
	public float costPerSecond = 8f;
	/** Cooldown, counted from the release. */
	public int cooldownTicks = 280;
	/** The longest hold, and the hold with Suspend ({@code lift_suspend}). */
	public int maxHoldTicks = 60;
	public int suspendHoldTicks = 100;
	/** Reach of the crosshair ray, and the cone around it (full angle) when the ray finds no one. */
	public float range = 14f;
	public float coneDegrees = 8f;
	/** Creatures taller than this cannot be lifted. */
	public float maxTargetHeight = 2.4f;
	/** How high a target rises; push-resistant targets rise less. */
	public float height = 2.5f;
	public float resistantHeight = 1.2f;
	/** Ticks of the rise to full height. */
	public int riseTicks = 10;
	/** The slam on release or at the end of the hold. */
	public float slamDamage = 6f;
	public int slamStaggerTicks = 15;
	/** Hurl ({@code lift_hurl}): how far the target is thrown, its damage and stagger. */
	public float hurlDistance = 10f;
	public float hurlDamage = 6f;
	public int hurlStaggerTicks = 20;
	/** Suspend: allies deal this much more damage to the lifted target. */
	public float suspendAllyBonus = 0.2f;
	/** One hit of at least this much damage on the caster ends the channel. */
	public float interruptDamage = 6f;
	/** A boss's own rule (its lift height handler) decides; these are the caps it is given. */
	public float bossHeight = 1.2f;
	public int bossStaggerTicks = 10;
	/** Ticks of channel a rubble block needs (the block handler counts them; shown in the tooltip). */
	public int rubbleTicks = 20;
	/** Each mastery rank: this fraction cheaper, this many blocks further. */
	public float masteryCostPerRank = 0.05f;
	public float masteryRangePerRank = 1f;
	/** Whether Lift may hold other players (server PvP must be on as well). */
	public boolean pvpEffects = false;
}
