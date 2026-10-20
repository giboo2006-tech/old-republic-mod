package com.oldrepublic.enemy.hymn;

import com.oldrepublic.config.BalanceConfig;

/**
 * Balance section {@code hymnsworn} of {@code config/oldrepublic/balance.json} (spec MILESTONE_5.md
 * 12.1, 16.3, 16.4): Ulvaine's cultists, their chant, blades and the storm call. Durations in ticks,
 * distances in blocks.
 */
public final class HymnswornBalance {
	// ------------------------------------------------ health (12.1: no armour)
	public float chanterHealth = 24f;
	public float bladeHealth = 28f;
	public float callerHealth = 36f;
	public float speed = 0.27f;
	public float followRange = 24f;

	// ------------------------------------------------ kill XP (16.3; unread until a per-type kill XP hook exists)
	public int chanterXp = 50;
	public int bladeXp = 50;
	public int callerXp = 100;

	// ------------------------------------------------ chanter: the chant and weak staff blows
	/** Health a second to every Hymnsworn ally within {@code chantRange} while the chanter chants. */
	public float chantHeal = 1f;
	public float chantRange = 8f;
	public int chantIntervalTicks = 20;
	public float staffDamage = 3f;
	public int staffCooldownTicks = 30;
	/** A chanter keeps about this far from its target while it chants. */
	public float chantDistance = 6f;

	// ------------------------------------------------ blade: fast vibroblade strikes
	public float bladeDamage = 5f;
	public int bladeCooldownTicks = 14;
	public float bladeReach = 2.4f;

	// ------------------------------------------------ storm-caller: the storm call and Force lightning
	public int callTellTicks = 40;
	public float callDamage = 8f;
	public int callShockTicks = 20;
	public float callRadius = 2.5f;
	public int callIntervalTicks = 160;
	public float callRange = 20f;
	public int lightningBurstTicks = 20;
	public int lightningIntervalTicks = 200;
	public float lightningDamage = 1.5f;
	public int lightningDamageInterval = 5;
	public float lightningRange = 12f;
	/** The storm-caller keeps about this far from its target. */
	public float callerDistance = 9f;

	public static HymnswornBalance get() {
		return BalanceConfig.section("hymnsworn", HymnswornBalance.class, HymnswornBalance::new);
	}
}
