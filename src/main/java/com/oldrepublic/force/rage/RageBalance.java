package com.oldrepublic.force.rage;

/**
 * Balance section {@code force_rage} of {@code config/oldrepublic/balance.json} (MILESTONE_5.md 9.7 and 16.4).
 * Durations in ticks. The modifiers are transient attribute modifiers on the caster, kept in step with the {@code rage}
 * effect; percentages are 0 to 100.
 */
public final class RageBalance {
	/** Force Energy of a cast, and the cooldown. */
	public float cost = 30f;
	public int cooldownTicks = 900;
	/** How long Rage lasts. */
	public int durationTicks = 200;
	/** Melee damage, attack speed and movement speed, in percent. */
	public float meleeDamagePct = 30f;
	public float attackSpeedPct = 15f;
	public float moveSpeedPct = 10f;
	/** The price: the caster takes this much more damage, and regenerates no Force Energy while it lasts. */
	public float damageTakenPct = 10f;
	/** Bloodthirst ({@code rage_unbound}): health each saber hit gives back. */
	public float unboundHeal = 1f;
	/** Cold Fury ({@code rage_cold}): no damage-taken penalty, and this melee damage instead. */
	public float coldDamagePct = 20f;
	/** Mastery: extra ticks per mastery rank (one point per hit landed during Rage). */
	public int masteryTicksPerRank = 20;
	/** Kept for the shared rule of every power; Rage touches only the caster, so it changes nothing. */
	public boolean pvpEffects = false;
}
