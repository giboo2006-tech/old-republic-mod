package com.oldrepublic.enemy.creature;

import com.oldrepublic.config.BalanceConfig;

/**
 * Balance section {@code creatures} of {@code config/oldrepublic/balance.json} (spec MILESTONE_4.md
 * 3.4, 10.3, 10.4 and 14): the hollowhorn and the ash hound, and their natural spawns. Durations in
 * ticks, distances in blocks. Read on the server only (the spawn weights at start-up).
 */
public final class CreatureBalance {
	// ------------------------------------------------ hollowhorn
	public double hollowhornHealth = 30;
	public double bullHealth = 45;
	public double hollowhornSpeed = 0.25;
	public double hollowhornKnockbackResistance = 0.4;
	/** Hostile this long after being hit. */
	public int angerTicks = 400;
	/** A player this close to a calf angers the herd. */
	public double calfGuardRange = 6;
	/** How far a herd's anger spreads. */
	public double herdRange = 16;
	public int chargeTellTicks = 20;
	/** The charge starts from at least this far and runs at most {@code chargeRange} blocks. */
	public double chargeMinRange = 4;
	public double chargeRange = 14;
	/** Blocks per tick of the charge. */
	public double chargeSpeed = 0.6;
	public float chargeDamage = 8f;
	public float chargeKnockback = 1.6f;
	public int chargeCooldownTicks = 80;
	/** Hitting a solid block stuns it this long, and it takes {@code stunDamageMult} damage meanwhile. */
	public int stunTicks = 50;
	public float stunDamageMult = 1.5f;
	/** Up close: a horn butt. */
	public float buttDamage = 4f;
	public int buttCooldownTicks = 30;
	public int herdMin = 3;
	public int herdMax = 5;
	public int hollowhornWeight = 10;
	/** A herd member after the first two is a calf this often. */
	public float calfChance = 0.35f;
	public float calfScale = 0.6f;

	// ------------------------------------------------ ash hound
	public double houndHealth = 18;
	public double houndSpeed = 0.35;
	public float biteDamage = 3f;
	public int biteCooldownTicks = 20;
	public int packMin = 3;
	public int packMax = 4;
	public int houndWeight = 12;
	/** The howl calls every hound this close onto the player. */
	public double howlRange = 24;
	/** Flankers circle at this radius before lunging. */
	public double flankRadius = 5;
	public int lungeTellTicks = 10;
	public double lungeRange = 6;
	public float lungeDamage = 5f;
	public int lungeCooldownTicks = 50;
	/** A parried or pulsed lunge staggers the hound this long. */
	public int lungeStaggerTicks = 30;
	/** No natural hound spawns this close to the Proving (the Varkhast hub), so the hub stays safe at night (3.4). */
	public int houndHubExclusion = 64;

	public static CreatureBalance get() {
		return BalanceConfig.section("creatures", CreatureBalance.class, CreatureBalance::new);
	}
}
