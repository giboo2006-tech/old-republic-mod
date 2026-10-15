package com.oldrepublic.saber;

/**
 * Tunable lightsaber numbers, section {@code "saber"} of {@code config/oldrepublic/balance.json}.
 * Server side only; clients see the results through item attribute modifiers and menu data.
 */
public final class SaberBalance {
	/** Ignited damage before components (the player's base 1 is included). */
	public float baseDamage = 7.0f;
	/** Ignited attacks per second before components. */
	public float baseAttackSpeed = 1.6f;
	/** Damage of a retracted hilt. */
	public float retractedDamage = 1.0f;

	public float guardianReach = 0.25f;
	public float guardianSpeed = -0.1f;
	public float duelistSpeed = 0.15f;
	public float duelistDamage = -0.5f;
	public float wrappedStaminaMultiplier = 1.2f;
	public float ridgedSpeed = 0.1f;
	public float weightedDamage = 1.0f;
	public float weightedSpeed = -0.15f;
	public float balancedParryMultiplier = 1.1f;

	/** Guard stamina before the grip bonus. */
	public float guardStamina = 100f;
	public float guardRegenPerSecond = 15f;
	/** Ticks without a drain before stamina regenerates. */
	public int guardRegenDelayTicks = 20;
	/** Parry window at the start of a guard, in ticks, before the pommel bonus. */
	public float parryWindowTicks = 6f;
	/** Total width of the guarded arc in front of the player, in degrees. */
	public float guardArcDegrees = 100f;
	/** Fraction of melee damage removed by a successful (non-parry) block. */
	public float meleeReduction = 0.70f;
	public float meleeDrainBase = 6f;
	public float meleeDrainPerDamage = 3f;
	public float boltDrain = 10f;
	/** Half-width of the random forward arc a deflected bolt leaves in, in degrees. */
	public float deflectArcDegrees = 50f;
	/** Ticks the guard is unusable after it breaks (also the slowness on the defender). */
	public int guardBreakTicks = 20;
	/** Stagger applied to a parried melee attacker, in ticks. */
	public int staggerTicks = 20;
	public float parryKnockback = 0.6f;
}
