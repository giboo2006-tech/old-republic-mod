package com.oldrepublic.saber.duel;

import com.oldrepublic.enemy.AcolyteBalance;

/**
 * The named duelist profiles (spec section 9.6), built from the {@code sith_acolyte} balance
 * section so they follow balance reloads: {@code acolyte}, {@code rival} (Brenn),
 * {@code jedi_master} (the mentors) and {@code training} (one telegraphed strike every 50 ticks,
 * infinite stamina, never guards or parries). Only {@code acolyte} deflects blaster bolts (M3).
 */
public final class DuelistProfiles {
	public static final String ACOLYTE = "acolyte";
	public static final String RIVAL = "rival";
	public static final String JEDI_MASTER = "jedi_master";
	public static final String TRAINING = "training";

	/** The profile with this id; unknown ids fall back to {@code acolyte}. */
	public static DuelistProfile get(String id) {
		AcolyteBalance b = AcolyteBalance.get();
		return switch (id) {
			case RIVAL -> new DuelistProfile(RIVAL, b.damage, b.rivalGuardStamina, b.parryChance, b.stringMax, b.stringMax,
					b.firstWindupTicks, b.swingGapTicks, b.recoveryTicks, true, b.rivalTrialYield);
			case JEDI_MASTER -> new DuelistProfile(JEDI_MASTER, b.masterDamage, b.masterGuardStamina, b.masterParryChance, 2, 2,
					b.masterWindupTicks, b.swingGapTicks, b.recoveryTicks, false, b.masterYield);
			case TRAINING -> new DuelistProfile(TRAINING, b.trainingDamage, Float.POSITIVE_INFINITY, 0f, 1, 1,
					b.trainingWindupTicks, b.trainingStrikeInterval, b.recoveryTicks, false, 0f);
			// Acolytes send blaster bolts back while they guard (spec 7.8); nothing else changes.
			default -> new DuelistProfile(ACOLYTE, b.damage, b.guardStamina, b.parryChance, b.stringMin, b.stringMax,
					b.firstWindupTicks, b.swingGapTicks, b.recoveryTicks, true, 0f, true, 0f, 0f);
		};
	}

	/** Whether a duelist with this profile ever raises its guard (the training profile does not). */
	public static boolean guards(DuelistProfile profile) {
		return Float.isFinite(profile.guardStamina());
	}

	private DuelistProfiles() {
	}
}
