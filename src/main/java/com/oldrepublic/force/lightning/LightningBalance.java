package com.oldrepublic.force.lightning;

/**
 * Tunable Force Lightning numbers, stored in the {@code force_lightning} section of
 * {@code config/oldrepublic/balance.json}. Durations in ticks, damage in HP, angles are half-angles
 * in degrees.
 */
public final class LightningBalance {
	public int chargeTicks = 8;
	/** Longest arc phase after the charge. */
	public int maxChannelTicks = 80;
	public int retargetTicks = 4;

	public float startCost = 6f;
	public float costPerTick = 0.75f;

	public int cooldownTicks = 160;
	public int fullChannelCooldownTicks = 200;

	public float range = 9f;
	public float halfAngle = 20f;

	public float damage = 1.5f;
	public int damageIntervalTicks = 5;
	public float masteryDamagePerRank = 0.08f;

	/** Slowness I applied while hit, refreshed every damage tick. */
	public int shockTicks = 10;

	/** After this many ticks of arcs the caster starts to burn. */
	public int burnAfterTicks = 60;
	public float burnDamage = 0.5f;

	/** A single hit of at least this much damage (after armour) on the caster ends the channel. */
	public float interruptDamage = 4f;

	public float guardDamageMult = 0.2f;
	public float guardDrainPerTick = 3f;
	public float resistantDamageMult = 0.6f;

	// Chain Lightning
	public int chainTargets = 2;
	public float chainRange = 4f;
	public float chainDamageMult = 0.6f;
	public float chainCostMult = 1.2f;

	// Consuming Lightning
	public float consumeRampPerSecond = 0.15f;
	public float consumeRampMax = 0.6f;
	public float consumeDrain = 0.2f;

	public int masteryPerCastMax = 5;

	/** Players are affected only when this is on and the server allows PvP. */
	public boolean pvpEffects = false;
}
