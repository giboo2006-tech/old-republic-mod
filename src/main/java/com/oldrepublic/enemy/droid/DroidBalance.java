package com.oldrepublic.enemy.droid;

import com.oldrepublic.config.BalanceConfig;

/**
 * Balance section {@code security_droid} of {@code config/oldrepublic/balance.json} (spec
 * MILESTONE_4.md 10.2 and 14): every number of the Mk IV security droid. Durations in ticks,
 * distances in blocks, multipliers as factors. Read on the server only.
 */
public final class DroidBalance {
	public double maxHealth = 50;
	public double armor = 12;
	public double movementSpeed = 0.2;
	public double knockbackResistance = 0.5;
	public double followRange = 32;
	/** It holds between these distances. */
	public double minRange = 6;
	public double maxRange = 16;

	/** Blaster bolts deal this much (-50%). */
	public float blasterMult = 0.5f;
	/** Its own bolts parried back at it deal this much. */
	public float parriedBoltMult = 1.5f;
	/** Force Lightning (shock) deals this much; the electrostaff and shock edges apply their own droid multiplier. */
	public float shockMult = 1.5f;
	/** Force Push moves it this much as far. */
	public float pushMult = 0.5f;

	/** Fire: a spin tell, then {@code volleyBolts} bolts over {@code volleyTicks}, then the cooldown. */
	public int spinTellTicks = 20;
	public int volleyBolts = 8;
	public int volleyTicks = 40;
	public float boltDamage = 4f;
	public float boltSpeed = 1.8f;
	public int boltLifetimeTicks = 50;
	public float aimInaccuracy = 4f;
	public int volleyCooldownTicks = 80;

	/** Below {@code shieldAt} of its health, once: a frontal plate for {@code shieldTicks} that stops bolts within {@code shieldDegrees} of its facing. */
	public float shieldAt = 0.5f;
	public int shieldTicks = 100;
	public float shieldDegrees = 70f;

	/** Death: {@code deathTellTicks} of sparking, then a burst of {@code deathBurstDamage} within {@code deathBurstRadius}. */
	public int deathTellTicks = 20;
	public float deathBurstDamage = 2f;
	public double deathBurstRadius = 1.5;

	/** An owned droid whose owner is gone this long powers down and leaves (as the hunters do). */
	public double ownerLeash = 160;
	public int ownerAbsentTicks = 200;

	public static DroidBalance get() {
		return BalanceConfig.section("security_droid", DroidBalance.class, DroidBalance::new);
	}
}
