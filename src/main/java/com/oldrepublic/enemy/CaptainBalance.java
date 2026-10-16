package com.oldrepublic.enemy;

import com.oldrepublic.config.BalanceConfig;

/**
 * Balance section {@code poacher_captain} of {@code config/oldrepublic/balance.json}. Durations in
 * ticks, distances in blocks.
 */
public final class CaptainBalance {
	public float health = 36f;
	public float armor = 5f;
	public float speed = 0.28f;
	public float knockbackResistance = 0.4f;
	public float followRange = 24f;

	/** Vibroblade swing and its tell. */
	public float swingDamage = 6f;
	public int swingTellTicks = 12;
	/** Overhead heavy: wind-up, damage, cooldown, guard drain multiplier, stagger when parried. */
	public float heavyDamage = 9f;
	public int heavyWindupTicks = 16;
	public int heavyCooldownTicks = 100;
	public float heavyGuardDrainMult = 2f;
	public int parryStaggerTicks = 40;

	/** Blaster pistol: used between the two ranges, with a muzzle-glow tell. */
	public float pistolMinRange = 4f;
	public float pistolMaxRange = 12f;
	public int pistolTellTicks = 12;
	public float pistolDamage = 4f;
	public int pistolCooldownTicks = 40;

	/** Health fraction at which she yields. */
	public float yieldFraction = 0.25f;

	public static CaptainBalance get() {
		return BalanceConfig.section("poacher_captain", CaptainBalance.class, CaptainBalance::new);
	}
}
