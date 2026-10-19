package com.oldrepublic.force.drain;

/**
 * Balance section {@code force_drain} of {@code config/oldrepublic/balance.json} (MILESTONE_5.md 9.5 and 16.4).
 * Durations in ticks, distances in blocks.
 */
public final class DrainBalance {
	/** Force Energy to start the channel, and per pulse. */
	public float startCost = 10f;
	public float pulseCost = 2f;
	/** One pulse every {@code pulseTicks}, at most {@code maxPulses}. */
	public int pulseTicks = 10;
	public int maxPulses = 6;
	/** Cooldown, counted from the end of the channel. */
	public int cooldownTicks = 300;
	/** Reach of the crosshair ray, and the half-angle of the aim-assist cone when the ray finds no one. */
	public float range = 10f;
	public float aimAssistHalfAngle = 8f;
	/** Each pulse: damage to the target (damage type {@code force_drain}), health to the caster, Force Energy given back. */
	public float pulseDamage = 2f;
	public float pulseHeal = 1.5f;
	public float pulseEnergy = 2f;
	/** Drain Life ({@code drain_life}): heal and damage multipliers. */
	public float lifeHealMultiplier = 2f;
	public float lifeDamageMultiplier = 0.75f;
	/** Drain Essence ({@code drain_essence}): extra Force Energy per pulse (and each pulse interrupts a wind-up). */
	public float essenceEnergy = 4f;
	/** A drained player's powers cost this many times as much (PvP only). */
	public float drainedCostMultiplier = 2f;
	/** Mastery: extra damage per pulse per mastery rank (one point per pulse that lands). */
	public float masteryDamagePerRank = 0.25f;
	/** Whether Drain may target other players (server PvP must be on as well). */
	public boolean pvpEffects = false;
}
