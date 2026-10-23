package com.oldrepublic.boss.ulvaine;

import com.oldrepublic.config.BalanceConfig;

/**
 * Balance section {@code ulvaine} of {@code config/oldrepublic/balance.json} (spec MILESTONE_5.md
 * 12.2, 16.4): Darth Ulvaine's fight and her echo. W4 reads {@code helperXp}. Durations in ticks,
 * distances in blocks, fractions of her health. Keys marked "addition" hold numbers the spec states
 * in prose or leaves open.
 */
public final class UlvaineBalance {
	// ------------------------------------------------ scaling and phases
	public double maxHealth = 600;
	public double perParticipant = 0.6;
	public int scalingCap = 4;
	public float phase2At = 0.65f;
	public float phase3At = 0.30f;
	public float yieldAt = 0.10f;
	/** A phase change takes this long; she takes no damage meanwhile. */
	public int transitionTicks = 60;
	/** Addition: armour, knock-back resistance and walking speed. */
	public double armor = 10;
	public double knockbackResistance = 0.6;
	public double movementSpeed = 0.32;
	/** Addition: the Heart's radius around its centre (31 blocks across) and its height above and below. */
	public double heartRadius = 15.5;
	public double heartHeight = 10;

	// ------------------------------------------------ phase 1: the duel (profile ulvaine)
	public float damage = 10f;
	public float guard = 140f;
	public float parry = 0.40f;
	public int[] strings = {3, 6};
	/** Addition: the duel's tell timings (the M2 duellist's shape). */
	public int windupTicks = 12;
	public int gapTicks = 7;
	public int recoveryTicks = 22;
	public int lightningChannel = 40;
	public float lightningDamage = 3f;
	public int lightningInterval = 10;
	/** Addition: the Force Lightning's range, and how often she may start it. */
	public float lightningRange = 12f;
	public int lightningCooldown = 200;
	/** Addition: her Force Push (a 12-tick tell, 6 blocks, 4 damage), how often. */
	public int pushTellTicks = 12;
	public float pushRange = 6f;
	public float pushDamage = 4f;
	public float pushStrength = 1.4f;
	public int pushCooldown = 240;
	public int stepInterval = 300;
	public int stepTell = 15;
	public float stepDistance = 6f;

	// ------------------------------------------------ phase 2: the storm
	public int rodInterval = 160;
	public int rodTell = 30;
	public float rodDamage = 8f;
	public float rodRadius = 2.5f;
	public int shockTicks = 20;
	/** While either pylon chanter lives she takes this percentage of the damage. */
	public float pylonDamagePct = 20f;

	// ------------------------------------------------ phase 3: the hymn
	/** Her attacks are this percentage slower. */
	public float slowPct = 20f;
	public int pulseInterval = 160;
	public float pulseRadius = 16f;
	public int pulseTicks = 40;
	public float pulseHeight = 1.5f;
	public float pulseDamage = 6f;
	public int pulseSlowTicks = 40;
	public float crescendoAt = 0.20f;
	public float resonatorHealth = 20f;
	public int crescendoTicks = 400;
	public float crescendoDamage = 12f;
	/** Stasis holds her at most this long; Choke at most {@code chokeMaxTicks}. */
	public int stasisMaxTicks = 20;
	public int chokeMaxTicks = 20;

	// ------------------------------------------------ the session
	public int resetTicks = 200;
	public int participantLeaveTicks = 100;
	/** W4 pays a helper this XP on the helper's defeat hook (6.9). */
	public int helperXp = 1200;

	// ------------------------------------------------ the echo
	public int echoXp = 800;
	public float echoShardChance = 0.25f;

	public static UlvaineBalance get() {
		return BalanceConfig.section("ulvaine", UlvaineBalance.class, UlvaineBalance::new);
	}
}
