package com.oldrepublic.boss.kaast;

import com.oldrepublic.config.BalanceConfig;

/**
 * Balance section {@code kaast} of {@code config/oldrepublic/balance.json} (spec MILESTONE_4.md 10.5,
 * 10.6, 14): the fight's keys (W6) and the reward keys W2's {@code KaastDuel} reads through this same
 * class. Durations in ticks, distances in blocks, fractions of Kaast's health. Keys marked
 * "addition" are not in spec 14; they hold numbers the spec states in prose or leaves open.
 */
public final class KaastBalance {
	// ------------------------------------------------ scaling and resistances
	public double maxHealth = 500;
	public double perParticipant = 0.6;
	public int scalingCap = 4;
	public int minesPerParticipant = 2;
	public int huntersPerParticipant = 1;
	public double armor = 12;
	public double knockbackResistance = 0.6;
	/** Control effects on Kaast last this much less (his own rule, not the M3 diminishing returns). */
	public float controlShorten = 0.4f;
	/** Lift raises him at most this high, and only during a phase 1 fall stagger. */
	public float liftFallBlocks = 1f;
	public float rattleHealth = 20f;
	/** Addition: walking speed. */
	public double movementSpeed = 0.3;
	/** Addition: the ring's radius around the pit: participants inside it count as in the fight. */
	public double arenaRadius = 20;

	// ------------------------------------------------ phases
	public float phase2At = 0.65f;
	public float phase3At = 0.30f;
	public float yieldAt = 0.10f;
	/** A phase change takes this long; Kaast takes no damage meanwhile. */
	public int transitionTicks = 60;

	// ------------------------------------------------ phase 1: the hunt
	public int hopTellTicks = 30;
	public int hopInterval = 160;
	public float rifleDamage = 6f;
	public int burst = 3;
	public int burstCooldown = 40;
	public int rocketInterval = 300;
	public int rocketTellTicks = 24;
	public float rocketDamage = 7f;
	/** Kaast's bolts deal this much more against a player Rattle is scanning. */
	public float markBonus = 0.25f;
	public int nestFallStagger = 40;
	/** Addition: a bolt a saber parried back staggers him this long. */
	public int parriedBoltStagger = 20;
	/** Addition: ticks of a hop between nests. */
	public int hopFlightTicks = 24;
	/** Addition: Rattle scans this long, then rests this long. */
	public int rattleScanTicks = 80;
	public int rattleRestTicks = 40;
	public double rattleScanRange = 24;

	// ------------------------------------------------ phase 2: the trap
	public int mines = 6;
	/** A mine blinks and beeps this long before it is live. */
	public int mineArmTicks = 40;
	/** Kaast kneels this long to set a mine; a heavy blow meanwhile interrupts it. */
	public int mineKneelTicks = 30;
	public float mineDamage = 8f;
	public float mineRadius = 1.5f;
	public int netInterval = 400;
	public int netTellTicks = 20;
	public int netRootTicks = 60;
	public int netSaberHits = 3;
	public int hunters = 2;
	public float huntersAt = 0.5f;
	/** Addition: one blow of at least this much counts as heavy against the mine kneel. */
	public float kneelHeavyDamage = 9f;
	/** Addition: the stagger of an interrupted kneel. */
	public int kneelStagger = 30;

	// ------------------------------------------------ phase 3: the last stand
	public float[] comboDamage = {8f, 8f, 12f};
	/** The wind-up (the tell) before the combo's third blow. */
	public int comboWindup = 10;
	/** The axe's guard against sabers. */
	public float guard = 120f;
	/** A parried third blow costs his guard this much (and staggers him {@code comboParryStagger}). */
	public float parryGuardDamage = 40f;
	public int chargeTell = 14;
	public int slamTellTicks = 40;
	public float slamDamage = 12f;
	public float slamRadius = 3f;
	/** Choke holds him this long, once, in phase 3 only. */
	public int chokeTicks = 20;
	/** Addition: a parried third blow staggers him this long; an earlier parried blow {@code comboBreakStagger}. */
	public int comboParryStagger = 30;
	public int comboBreakStagger = 10;
	/** Addition: ticks between the combo's first blows, and after a combo. */
	public int comboGapTicks = 8;
	public int comboCooldown = 40;
	/** Addition: a saber blow the axe blocks costs his guard this much per point of damage, and does this fraction. */
	public float blockDrainPerDamage = 3f;
	public float blockDamageFraction = 0.15f;
	/** Addition: a broken guard staggers him this long and comes back full after {@code guardRegenTicks}. */
	public int guardBreakTicks = 40;
	public int guardRegenTicks = 100;
	/** Addition: the shoulder charge. */
	public float chargeDamage = 8f;
	public double chargeSpeed = 0.7;
	public double chargeRange = 10;
	public int chargeCooldown = 160;
	/** Addition: how high the jet slam rises; it comes once health falls this far into phase 3. */
	public double slamHeight = 10;
	public float slamAt = 0.2f;
	/** Addition: burning off a choke staggers the caster this long. */
	public int chokeBurnStagger = 30;

	// ------------------------------------------------ reset
	public int resetTicks = 200;
	public int participantLeaveTicks = 100;

	// ------------------------------------------------ rewards (W2 reads these)
	public int ownerXp = 4000;
	public int ownerSp = 2;
	public int ownerCredits = 300;
	public int resonance = 500;
	public int helperXp = 1000;
	public int rematchXp = 800;
	public int rematchSchematicPity = 4;
	public int rematchCredits = 100;

	/** When false, an operator places camps with {@code /oldrepublic kaast camp <player>}. */
	public boolean placeCamp = true;

	public static KaastBalance get() {
		return BalanceConfig.section("kaast", KaastBalance.class, KaastBalance::new);
	}

	/** The combo's damage for blow {@code index} (0, 1, 2), the last value repeating. */
	public float comboDamage(int index) {
		if (comboDamage == null || comboDamage.length == 0) {
			return 8f;
		}

		return comboDamage[Math.min(index, comboDamage.length - 1)];
	}
}
