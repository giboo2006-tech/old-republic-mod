package com.oldrepublic.enemy.stormback;

import com.oldrepublic.config.BalanceConfig;

/**
 * Balance section {@code stormback} of {@code config/oldrepublic/balance.json} (spec MILESTONE_5.md
 * 12.1, 16.3, 16.4): Tyrvann's plated grazer, its charge, the lightning charge and the roar.
 * Durations in ticks, distances in blocks.
 */
public final class StormbackBalance {
	public float health = 80f;
	public float armor = 6f;
	public float speed = 0.24f;
	public float followRange = 28f;
	public float knockbackResistance = 0.6f;

	// ------------------------------------------------ kill XP (16.3, paid through progression.KillXp) and drops
	public int xp = 120;
	public int chargedXp = 160;
	/** {@code stormglass_shard} dropped by a stormback that dies charged (the loop of 12.1). */
	public int stormglassMin = 1;
	public int stormglassMax = 2;

	// ------------------------------------------------ natural spawn (Tyrvann's basalt and stormwood)
	public int weight = 8;
	public int groupMin = 1;
	public int groupMax = 2;

	// ------------------------------------------------ provocation
	/** A player this close for {@code provokeTicks} ticks provokes it. */
	public float provokeRange = 6f;
	public int provokeTicks = 60;
	/** It stays angry this long after the last provocation. */
	public int angerTicks = 600;

	// ------------------------------------------------ the charge (the M4 hollowhorn pattern)
	public float chargeDamage = 10f;
	public float chargedChargeDamage = 15f;
	public float chargeKnockback = 1.6f;
	public int chargeTellTicks = 20;
	public float chargeSpeed = 0.55f;
	public float chargeMinRange = 4f;
	public float chargeRange = 14f;
	public int chargeCooldownTicks = 80;
	public int stunTicks = 40;
	public float buttDamage = 5f;
	public int buttCooldownTicks = 30;

	// ------------------------------------------------ charged by lightning
	public int chargedTicks = 400;
	/** +50% damage while charged. */
	public float chargedDamageBonus = 0.5f;
	/** A charged charge shocks for this long. */
	public int chargedShockTicks = 20;

	// ------------------------------------------------ the roar (once per fight)
	public float roarBelow = 0.5f;
	public int roarTellTicks = 30;
	public float roarDamage = 6f;
	public float roarRadius = 3f;
	public int roarShockTicks = 20;

	public static StormbackBalance get() {
		return BalanceConfig.section("stormback", StormbackBalance.class, StormbackBalance::new);
	}
}
