package com.oldrepublic.progression;

/** Balance section {@code progression}. See docs/BALANCE.md for what each value does. */
public final class ProgressionBalance {
	public int maxLevel = 100;
	/** xp_to_next(L) = curveBase * L^curveExponent + curveLinear * L */
	public double curveBase = 120;
	public double curveExponent = 1.55;
	public double curveLinear = 30;
	public int skillPointsPerLevel = 1;
	public int resonanceStartMin = 2000;
	public int resonanceStartMax = 4000;

	public float baseMaxEnergy = 50f;
	public float maxEnergyPerLevel = 2f;
	/** Extra max energy per 1000 resonance above the starting value. */
	public float maxEnergyPerThousandResonance = 6f;
	public float energyRegenPerSecond = 2f;
	public float energyRegenPerThousandResonance = 0.25f;
	public int energyRegenDelayTicks = 30;

	/** Ticks alive before the Awakening triggers by itself (2 in-game days). */
	public int awakeningTicks = 48000;

	/**
	 * XP for killing a hostile mob = mob max health * this, at most {@code killXpCap}, before anti-grind. An enemy
	 * with its own number ({@link KillXp}, Milestone 5) pays that instead.
	 */
	public float killXpPerHealth = 1.5f;
	public float killXpCap = 80f;
	/** Each recent kill of the same type multiplies XP by this. */
	public float fatigueFactor = 0.85f;
	public float fatigueFloor = 0.10f;
	/** Fatigue points removed per minute of game time. */
	public float fatigueDecayPerMinute = 0.75f;

	/** Clamp of the combined {@code ForceModifiers} cost multiplier. */
	public float modifierCostMin = 0.70f;
	public float modifierCostMax = 1.50f;
	/** Clamp of the combined {@code ForceModifiers} regen multiplier. */
	public float modifierRegenMin = 0.50f;
	public float modifierRegenMax = 1.50f;
	/** Clamp of the combined {@code DamageModifiers} multiplier (combat package). */
	public float damageModifierMin = 0.25f;
	public float damageModifierMax = 2.0f;
	/** Most Force Energy all refunds of one kill may restore together. */
	public float killRefundCap = 12f;
	/** Most Force Energy all refunds of one parry may restore together. */
	public float parryRefundCap = 6f;
}
