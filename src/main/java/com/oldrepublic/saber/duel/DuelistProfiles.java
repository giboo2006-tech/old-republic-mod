package com.oldrepublic.saber.duel;

import com.oldrepublic.boss.mentor.MentorDuelBalance;
import com.oldrepublic.boss.ulvaine.UlvaineBalance;
import com.oldrepublic.boss.ulvaine.UlvaineRules;
import com.oldrepublic.enemy.AcolyteBalance;

/**
 * The named duelist profiles (spec section 9.6), built from the {@code sith_acolyte} balance
 * section so they follow balance reloads: {@code acolyte}, {@code rival} (Brenn),
 * {@code jedi_master} (the mentors) and {@code training} (one telegraphed strike every 50 ticks,
 * infinite stamina, never guards or parries). Only {@code acolyte} deflects blaster bolts (M3).
 *
 * <p>Milestone 5 (spec MILESTONE_5.md 12.1, 12.2) adds {@code mentor_true} (the mentor at full
 * strength in the Fall and the Return, from {@code mentor_duel}: damage 9, guard 120, parry 0.35,
 * strings of 3 to 5), {@code ulvaine} (Darth Ulvaine's duel, from {@code ulvaine}: damage 10, guard
 * 140, parry 0.40, strings of 3 to 6, deflecting bolts) and {@code ulvaine_hymn} (the same, 20%
 * slower: her third phase).
 */
public final class DuelistProfiles {
	public static final String ACOLYTE = "acolyte";
	public static final String RIVAL = "rival";
	public static final String JEDI_MASTER = "jedi_master";
	public static final String TRAINING = "training";
	/** The mentor at full strength (Milestone 5, 12.1). */
	public static final String MENTOR_TRUE = "mentor_true";
	/** Darth Ulvaine's duel in her first two phases (Milestone 5, 12.2). */
	public static final String ULVAINE = "ulvaine";
	/** Darth Ulvaine's duel in her third phase: {@code slowPct} slower. */
	public static final String ULVAINE_HYMN = "ulvaine_hymn";

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
			case MENTOR_TRUE -> mentorTrue(MentorDuelBalance.get());
			case ULVAINE -> ulvaine(UlvaineBalance.get(), UlvaineRules.PHASE_DUEL);
			case ULVAINE_HYMN -> ulvaine(UlvaineBalance.get(), UlvaineRules.PHASE_HYMN);
			// Acolytes send blaster bolts back while they guard (spec 7.8); nothing else changes.
			default -> new DuelistProfile(ACOLYTE, b.damage, b.guardStamina, b.parryChance, b.stringMin, b.stringMax,
					b.firstWindupTicks, b.swingGapTicks, b.recoveryTicks, true, 0f, true, 0f, 0f);
		};
	}

	/** {@code mentor_true}: the mentor's real duel; the yield is the duel's own (12.1), not the profile's. */
	private static DuelistProfile mentorTrue(MentorDuelBalance m) {
		int min = m.strings.length > 0 ? m.strings[0] : 3;
		int max = m.strings.length > 1 ? Math.max(min, m.strings[1]) : min;
		return new DuelistProfile(MENTOR_TRUE, m.damage, m.guard, m.parry, min, max, m.windupTicks, m.gapTicks, m.recoveryTicks, false, 0f);
	}

	/** {@code ulvaine} and {@code ulvaine_hymn}: her duel; she deflects bolts, and her third phase is slower (12.2). */
	private static DuelistProfile ulvaine(UlvaineBalance u, int phase) {
		int min = u.strings.length > 0 ? u.strings[0] : 3;
		int max = u.strings.length > 1 ? Math.max(min, u.strings[1]) : min;
		return new DuelistProfile(phase >= UlvaineRules.PHASE_HYMN ? ULVAINE_HYMN : ULVAINE, u.damage, u.guard, u.parry, min, max,
				UlvaineRules.slowed(u.windupTicks, phase, u), UlvaineRules.slowed(u.gapTicks, phase, u), UlvaineRules.slowed(u.recoveryTicks, phase, u),
				false, 0f, true, 0f, 0f);
	}

	/** Whether a duelist with this profile ever raises its guard (the training profile does not). */
	public static boolean guards(DuelistProfile profile) {
		return Float.isFinite(profile.guardStamina());
	}

	private DuelistProfiles() {
	}
}
