package com.oldrepublic.boss;

import com.oldrepublic.saber.duel.DuelistProfile;

/**
 * Orren Vasch's two saber duel profiles (spec 8.3), built from the {@code collector} balance section
 * so they follow balance reloads. Both deflect blaster bolts while guarding (his cone and spread are
 * {@code deflectConeDegrees} / {@code deflectSpreadDegrees}); neither shoves (his Force moves are his
 * own) and neither yields through the spar rules (the session decides his yield).
 *
 * <ul>
 * <li>{@code collector_archivist} (phases 1 and 2): light 6, heavy last swing 11, guard 140, parries
 * light attacks above 70% stamina 30% of the time, strings of 2 to 3, wind-up 10, recovery 16.</li>
 * <li>{@code collector_unkept} (phase 3): light 7, heavy 12, guard 100, parry 15% (at the saber
 * feature's default stamina threshold), strings of 3 to 4, wind-up 8, recovery 12.</li>
 * </ul>
 *
 * The rematch's damage multiplier ({@code echoDamageMultiplier}, or {@code unchainedDamage}) scales
 * both swing damages through {@code outgoing}.
 */
public final class CollectorProfiles {
	public static final String ARCHIVIST = "collector_archivist";
	public static final String UNKEPT = "collector_unkept";

	/** The phase 1 and 2 profile, its damage times {@code outgoing}. */
	public static DuelistProfile archivist(CollectorBalance b, float outgoing) {
		return new DuelistProfile(ARCHIVIST, b.p1LightDamage * outgoing, b.p1Guard, b.p1ParryChance, b.p1StringMin, b.p1StringMax,
				b.p1WindupTicks, b.p1GapTicks, b.p1RecoveryTicks, false, 0f, true, b.p1HeavyDamage * outgoing, b.p1ParryMinStamina);
	}

	/** The phase 3 profile, its damage times {@code outgoing}. */
	public static DuelistProfile unkept(CollectorBalance b, float outgoing) {
		return new DuelistProfile(UNKEPT, b.p3LightDamage * outgoing, b.p3Guard, b.p3ParryChance, b.p3StringMin, b.p3StringMax,
				b.p3WindupTicks, b.p3GapTicks, b.p3RecoveryTicks, false, 0f, true, b.p3HeavyDamage * outgoing, 0f);
	}

	/** The profile of {@code phase} (3 is Unkept, anything else the Archivist). */
	public static DuelistProfile forPhase(int phase, float outgoing, CollectorBalance b) {
		return phase >= CollectorRules.PHASE_THREE ? unkept(b, outgoing) : archivist(b, outgoing);
	}

	private CollectorProfiles() {
	}
}
