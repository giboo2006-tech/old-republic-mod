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
}
