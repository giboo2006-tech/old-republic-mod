package com.oldrepublic.enemy.mandalorian;

import com.oldrepublic.config.BalanceConfig;

/**
 * Balance section {@code mandalorian} of {@code config/oldrepublic/balance.json} (spec
 * MILESTONE_4.md 10.1 and 14): every number of the Mandalorian hunter. Durations in ticks, distances
 * in blocks. Read on the server only.
 */
public final class HunterBalance {
	public double maxHealth = 40;
	public double armor = 10;
	public double movementSpeed = 0.28;
	public double knockbackResistance = 0.2;
	public double followRange = 40;
	/** The rifle band: the hunter holds between these distances and strafes. */
	public double minRange = 8;
	public double maxRange = 18;

	/** Rifle: gold {@code MANDALORIAN} bolts, bursts of {@code burst}, {@code burstCooldownTicks} between bursts. */
	public float rifleDamage = 6f;
	public int burst = 2;
	public int burstCooldownTicks = 40;
	public int burstGapTicks = 5;
	/** The rifle's aim tell before each burst (the muzzle glow and whine). */
	public int rifleTellTicks = 10;
	public float boltSpeed = 2.0f;
	public int boltLifetimeTicks = 50;
	public float aimInaccuracy = 2.5f;
	/** Lead prediction: the share of the target's motion over the bolt's flight added to the aim; Force Speed runners get the larger one. */
	public float lead = 0.75f;
	public float speedLead = 1.0f;

	/** Jet hop: every {@code jetIntervalTicks}, or when a player closes within {@code jetCloseRange}. */
	public int jetIntervalTicks = 200;
	public double jetCloseRange = 5;
	public int jetTellTicks = 20;
	public double jetMinDistance = 6;
	public double jetMaxDistance = 10;
	/** Ticks the hop's flight takes, and how high it arcs. */
	public int jetFlightTicks = 16;
	public double jetArcHeight = 3;
	/** Pushed out of a hop: staggered this long on landing. */
	public int jetStaggerTicks = 40;
	/** A hop never comes sooner than this after the last one, even when rushed. */
	public int jetMinGapTicks = 60;

	/** Wrist flamer: under {@code flamerRange}, a tell, then a cone. */
	public double flamerRange = 4;
	public int flamerTellTicks = 14;
	public double flamerConeLength = 3;
	/** Half-angle of the cone. */
	public float flamerConeDegrees = 30f;
	public int flamerTicks = 30;
	public float flamerDamage = 2f;
	public int flamerDamageInterval = 5;
	public float flamerFireSeconds = 3f;
	public int flamerCooldownTicks = 120;

	/** Wrist rocket: every {@code rocketIntervalTicks} at {@code rocketMinRange} to {@code rocketMaxRange}. */
	public int rocketIntervalTicks = 300;
	public double rocketMinRange = 8;
	public double rocketMaxRange = 20;
	public int rocketTellTicks = 24;
	public float rocketSpeed = 0.55f;
	public float rocketDamage = 7f;
	public float rocketRadius = 2f;
	public int rocketLifetimeTicks = 100;
	/** How fast a pushed rocket flies back. */
	public float rocketPushSpeed = 0.9f;

	/** The last hunter of a story ambush yields at this fraction of its health. */
	public float yieldAt = 0.2f;

	/** Cover (the M3 trooper cover-seeking goal). */
	public double coverHealthFraction = 0.4;
	public double coverSpeed = 1.3;
	public int coverHoldMinTicks = 40;
	public int coverHoldMaxTicks = 70;
	public int coverCooldownTicks = 240;

	/** An owned hunter whose owner is gone (offline, another level, or past {@code ownerLeash} blocks) this long leaves. */
	public double ownerLeash = 160;
	public int ownerAbsentTicks = 200;
	/** Ambush hunters drop in on jet packs from this high above the ground. */
	public int dropHeight = 14;
	/** Ambush hunters land this far apart around the ambush point. */
	public double ambushSpread = 4;

	public static HunterBalance get() {
		return BalanceConfig.section("mandalorian", HunterBalance.class, HunterBalance::new);
	}
}
