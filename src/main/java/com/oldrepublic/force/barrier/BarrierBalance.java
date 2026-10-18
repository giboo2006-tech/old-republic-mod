package com.oldrepublic.force.barrier;

/**
 * Balance section {@code force_barrier} of {@code config/oldrepublic/balance.json} (spec
 * MILESTONE_4.md 9.2, 9.5 and 14). Durations in ticks, distances in blocks.
 */
public final class BarrierBalance {
	/** Force Energy to raise the barrier, and per second while it holds. */
	public float startCost = 15f;
	public float channelCost = 6f;
	/** The longest hold. */
	public int maxTicks = 120;
	/** Cooldown, counted from the release. */
	public int cooldownTicks = 400;
	/** Radius of the sphere of bent air around the caster's chest; Shelter ({@code barrier_shelter}) widens it. */
	public float radius = 2f;
	public float shelterRadius = 3.5f;
	/** Force Energy each stopped bolt costs; a heavy bolt costs more. */
	public float boltCost = 3f;
	public float heavyBoltCost = 10f;
	/** Return ({@code barrier_return}): stopped bolts fly back at their shooter with this share of their damage (heavy ones drop). */
	public float returnDamageMult = 0.6f;
	/** Damage a rocket or grenade burst at the surface still deals inside. */
	public float blastMult = 0.3f;
	/** Melee and flame damage to the protected are cut by these percentages. */
	public float meleePct = 50f;
	public float flamePct = 50f;
	/** A single hit of at least this much (before the barrier) breaks it and staggers the caster. */
	public float breakDamage = 10f;
	public int breakStaggerTicks = 20;
	/** Movement while the barrier holds: sprinting is cut back to walking speed. */
	public float sprintMoveMult = 0.77f;
	/** Releasing after at least this long sends the pulse: it pushes enemies within its radius about this far and staggers them. */
	public int pulseMinTicks = 40;
	public float pulseRadius = 3f;
	public float pulseKnockback = 0.85f;
	public int pulseStaggerTicks = 10;
	/** {@code guardians_patience}: saber parries within this many ticks of the barrier ending restore Force Energy. */
	public float guardiansPatienceFe = 2f;
	public int guardiansPatienceTicks = 40;
	/** Mastery: one point per this many stopped projectiles, or per this much melee damage absorbed. */
	public int masteryProjectiles = 5;
	public float masteryMeleeAbsorbed = 30f;
	/** Each mastery rank: this much less energy per second and this many ticks longer. */
	public float masteryCostPerRank = 1f;
	public int masteryTicksPerRank = 10;
	/** Whether the pulse pushes other players (server PvP must be on as well). Bolts are always stopped. */
	public boolean pvpEffects = false;
}
