package com.oldrepublic.force.valor;

/**
 * Balance section {@code force_valor} of {@code config/oldrepublic/balance.json} (MILESTONE_5.md 9.4 and 16.4).
 * Durations in ticks, distances in blocks. The modifiers are transient attribute modifiers on each affected player, kept
 * in step with the {@code valor} effect.
 */
public final class ValorBalance {
	/** Force Energy of a cast, and the cooldown. */
	public float cost = 35f;
	public int cooldownTicks = 900;
	/** How long Valor lasts on the caster and each ally. */
	public int durationTicks = 200;
	/** Allies (the players Shared Light counts as allies) within this radius share it. */
	public float radius = 8f;
	/** Attack damage, in percent; armour points; knockback resistance (0 to 1). */
	public float attackDamagePct = 20f;
	public float armor = 4f;
	public float knockbackResistance = 0.5f;
	/** Rallying Light ({@code valor_shared}): the wider radius, and the health each ally regains at the start. */
	public float sharedRadius = 16f;
	public float sharedHeal = 4f;
	/** Steadfast ({@code valor_steadfast}): the caster alone, with this attack damage instead, and immune to stagger. */
	public float steadfastDamagePct = 30f;
	/** Mastery: extra ticks per mastery rank (one point per cast that touches an ally or an enemy in combat). */
	public int masteryTicksPerRank = 20;
	/** Kept for the shared rule of every power; Valor only touches allies, so it changes nothing. */
	public boolean pvpEffects = false;
}
