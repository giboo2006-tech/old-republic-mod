package com.oldrepublic.force.heal;

/**
 * Tunable Force Heal numbers, stored in the {@code force_heal} section of
 * {@code config/oldrepublic/balance.json}. Durations in ticks, health in half-hearts (HP).
 */
public final class HealBalance {
	public int startupTicks = 6;
	public int intervalTicks = 5;
	/** Longest heal phase after the start-up. */
	public int maxChannelTicks = 60;

	public float startCost = 8f;
	public float costPerHeal = 3f;

	public float healPerTick = 1.0f;
	public float masteryHealPerRank = 0.10f;

	public int cooldownTicks = 200;
	public float interruptedCooldownMult = 0.5f;
	/** A single hit of at least this much damage (after armour) interrupts the channel. */
	public float interruptDamage = 4f;
	/** Slowness at or above this amplifier counts as a stagger and interrupts. */
	public int staggerAmplifier = 2;

	/** Movement speed multiplier while channelling. */
	public float moveMult = 0.5f;

	// Shared Light
	public float sharedRadius = 6f;
	public float sharedRate = 0.6f;
	public float sharedCostMult = 1.25f;
	/** Players damaged by or damaging the caster within this window are not healed. */
	public int combatExcludeTicks = 200;

	// Serene Ward
	public int wardMinChannelTicks = 20;
	public int wardAbsorptionTicks = 200;
	public int wardAbsorptionAmplifier = 0;
	public float wardStamina = 30f;

	public float masteryHpPerPoint = 4f;
	public float masteryHostileRadius = 16f;
}
