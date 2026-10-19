package com.oldrepublic.skill;

/**
 * Balance section {@code skills} of {@code config/oldrepublic/balance.json}: skill-tree rules and the
 * numbers of the passives this feature applies or exposes. Durations in ticks.
 */
public final class SkillBalance {
	/** Extra skill points to switch inside an exclusive group (the old node is not refunded). */
	public int switchPenalty = 2;
	/** Fraction of spent points lost on a full respec (rounded up, at least 1). */
	public float respecLoss = 0.10f;
	/** Minimum game time between two full respecs (3 in-game days). */
	public int respecCooldownTicks = 72000;
	/** Switching, slot changes and respecs are refused this long after dealing or taking damage. */
	public int combatLockTicks = 60;
	/** Open ability slots from the start, and after the first rank. */
	public int baseSlots = 3;
	public int maxSlots = 4;
	/** How close a mentor (or, for Independents, a meditation stone) must be for a respec. */
	public int respecAnchorRadius = 8;

	public int deepReservesPerRank = 8;
	public int quickenedRegenPctPerRank = 10;
	public int quickenedDelayPerRank = 5;
	public int centeredParryTicksPerRank = 1;
	public int calmFocusDelay = 10;
	public int calmFocusMaxEnergyPct = 10;
	public float guardiansResolveReduction = 0.10f;
	public float guardiansResolveRadius = 6f;
	public float furyPerRank = 0.06f;
	public float furyHealthFraction = 0.5f;
	public int hungeringEnergy = 6;
	public int hungeringCooldownTicks = 40;
	public int dreadFleeTicks = 40;
	public float unboundCostMult = 0.85f;
	public float unboundCooldownMult = 0.9f;
	public float opposedCostMult = 1.5f;

	// ---------------------------------------------------------------- Exploration tree (Milestone 3)

	/** {@code trailwise}: fall damage reduction per rank (fractions). */
	public float[] trailwiseFall = {0.2f, 0.4f};
	/** {@code trailwise}: sprint hunger reduction per rank (fractions). */
	public float[] trailwiseHunger = {0.15f, 0.3f};
	/** {@code hardy}: extra max health per rank (half hearts). */
	public int[] hardyHealth = {2, 4};
	/** {@code scavenger}: chance of one extra material from a trooper or sentinel kill. */
	public float scavengerChance = 0.35f;
	/** {@code quiet_step}: dormant sentinels wake at this distance (blocks) instead of their own. */
	public int quietStepWake = 4;
	/** {@code quiet_step}: wary troopers warn at this distance (blocks). */
	public int quietStepWarn = 10;
	/** {@code quiet_step}: wary troopers open fire after at least this many ticks. */
	public int quietStepWaryTicks = 200;
	/** {@code pathfinders_poise}: movement speed bonus out of combat (fraction). */
	public float pathfinderSpeed = 0.10f;
	/** {@code pathfinders_poise}: fall damage reduction (fraction). */
	public float pathfinderFall = 0.25f;
	/** All fall damage reductions together never exceed this fraction. */
	public float fallReductionCap = 0.6f;

	// ---------------------------------------------------------------- granted holocron nodes (Milestone 3)

	/** {@code watchers_insight}: Force Heal cost reduction (fraction). */
	public float watchersInsightHealCost = 0.10f;
	/** {@code tomb_hunger}: Force Energy for a kill soon after a power hit that target. */
	public int tombHungerFe = 6;
	/** {@code tomb_hunger}: how long after the power hit the kill still counts. */
	public int tombHungerWindowTicks = 60;
	/** {@code crackling_grasp}: movement slowdown after Force Lightning ends (fraction). */
	public float cracklingSlow = 0.3f;
	/** {@code crackling_grasp}: how long the slowdown lasts. */
	public int cracklingTicks = 40;
	/** {@code archivists_hunger}: extra Push and Lightning damage (fraction). */
	public float archivistsHungerBonus = 0.15f;
	/** {@code archivists_hunger}: applies above this fraction of the target's max health. */
	public float archivistsHungerThreshold = 0.75f;
	/** {@code archivists_road}: Force Sense cooldown reduction (fraction). */
	public float archivistsRoadSenseCooldown = 0.3f;
	/** {@code archivists_road}: Telekinetic Lift cost reduction (fraction). */
	public float archivistsRoadLiftCost = 0.1f;

	// ---------------------------------------------------------------- Milestone 5 (MILESTONE_5.md 9.1 and 8.5)

	/** The second power bar's first two slots (5 and 6) open at this level; all four open at rank 4 or as a Wayfarer. */
	public int secondBarLevel = 15;
	/** {@code wayfarers_stride} (Wayfarer's Grace): extra maximum Force Energy (percent). */
	public int wayfarerEnergyPct = 10;
	/** {@code wayfarers_stride}: Force Speed costs this much less (percent). */
	public int wayfarerSpeedCostPct = 20;
}
