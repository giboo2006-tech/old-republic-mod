package com.oldrepublic.enemy.scour;

import com.oldrepublic.config.BalanceConfig;

/**
 * Balance section {@code sand_scour} of {@code config/oldrepublic/balance.json} (spec MILESTONE_5.md
 * 12.1, 16.3, 16.4): the burrowing sand scour, its matriarch and its natural spawns. Durations in
 * ticks, distances in blocks.
 */
public final class ScourBalance {
	public float scourHealth = 30f;
	public float matriarchHealth = 120f;
	public float speed = 0.3f;
	public float followRange = 20f;
	public float matriarchScale = 1.6f;

	// ------------------------------------------------ kill XP (16.3, paid through progression.KillXp)
	public int scourXp = 60;
	public int matriarchXp = 400;

	// ------------------------------------------------ natural spawn (Ghessar's dunes and canyons)
	public int weight = 10;
	/** Sandstorms double the spawns: outside one, half the natural spawn attempts are turned away. */
	public int stormMultiplier = 2;
	public int groupMin = 1;
	public int groupMax = 2;

	// ------------------------------------------------ burrowed, the burst and the fight
	/** The ripple and the hiss carry this far while it lies burrowed. */
	public float rippleRange = 6f;
	public int rippleIntervalTicks = 60;
	/** A player this close makes a burrowed scour burst. */
	public float burstRange = 4f;
	public int burstTellTicks = 15;
	public float biteDamage = 6f;
	public int biteSlowTicks = 40;
	/** Slowness II. */
	public int biteSlowAmplifier = 1;
	public int biteCooldownTicks = 20;
	public float biteReach = 2.2f;
	/** It fights above ground this long, then burrows for {@code burrowTicks} (taking damage only above ground). */
	public int surfaceTicks = 120;
	public int burrowTicks = 30;
	/** Below this health fraction it flees under the sand. */
	public float fleeBelow = 0.3f;
	/** A fleeing scour keeps away under the sand this long, then turns to fight again (so a cull can finish). */
	public int fleeTicks = 160;
	public float burrowedSpeed = 0.18f;

	// ------------------------------------------------ the matriarch
	public int sweepIntervalTicks = 200;
	public int sweepTellTicks = 20;
	public float sweepDamage = 8f;
	public float sweepRadius = 4f;
	public float sweepKnockback = 0.8f;
	public int broodIntervalTicks = 400;
	public int broodCount = 2;
	/** She calls no more brood while this many of hers live. */
	public int broodMax = 6;

	public static ScourBalance get() {
		return BalanceConfig.section("sand_scour", ScourBalance.class, ScourBalance::new);
	}
}
