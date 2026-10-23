package com.oldrepublic.enemy;

import com.oldrepublic.config.BalanceConfig;

/**
 * Balance section {@code sith_acolyte} of {@code config/oldrepublic/balance.json}: the Sith Acolyte,
 * its Force Shove, and the duelist profiles of the other saber fighters (the rival Brenn, the Jedi
 * master and the training dummy profile) that {@code saber.duel.DuelistProfiles} builds from it.
 * Durations in ticks, distances in blocks.
 */
public final class AcolyteBalance {
	public float health = 40f;
	public float armor = 4f;
	public float speed = 0.30f;
	public float followRange = 24f;
	public float knockbackResistance = 0.3f;
	public float damage = 6f;

	/** Saber NPC guard stamina, regeneration per second, and the delay after a drain. */
	public float guardStamina = 60f;
	public float guardRegen = 10f;
	public int guardRegenDelayTicks = 30;
	public float parryChance = 0.35f;

	/** Attack strings: swings per string, the first wind-up, the gap between swings, the recovery after. */
	public int stringMin = 2;
	public int stringMax = 3;
	public int firstWindupTicks = 10;
	public int swingGapTicks = 7;
	public int recoveryTicks = 30;

	/** Force Shove. */
	public int shoveCooldownTicks = 200;
	public int shoveWindupTicks = 16;
	public float shoveRange = 6f;
	public float shoveConeDegrees = 50f;
	public float shoveStrength = 1.1f;
	public float shoveLift = 0.3f;
	public float shoveDamage = 2f;
	/** Stagger after a Push cancels the shove wind-up (push clash). */
	public int shoveClashStaggerTicks = 30;

	/** Brenn (rival mode). The yield fractions are what the story passes as spar rules. */
	public float rivalGuardStamina = 70f;
	public float rivalTrialYield = 0.15f;
	public float rivalLessonYield = 0.4f;

	/** The mentors' {@code jedi_master} profile. */
	public float masterHealth = 60f;
	public float masterDamage = 5f;
	public float masterGuardStamina = 120f;
	public float masterParryChance = 0.5f;
	public float masterYield = 0.35f;
	public float masterSparDamageScale = 0.6f;
	public int masterWindupTicks = 12;

	/** The {@code training} profile: one telegraphed strike at a time, never guards. */
	public int trainingStrikeInterval = 50;
	public int trainingWindupTicks = 14;
	public float trainingDamage = 1f;

	public static AcolyteBalance get() {
		return BalanceConfig.section("sith_acolyte", AcolyteBalance.class, AcolyteBalance::new);
	}
}
