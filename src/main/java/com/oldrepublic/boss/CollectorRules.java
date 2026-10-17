package com.oldrepublic.boss;

import net.minecraft.util.Mth;

/**
 * The Collector fight's pure rules (spec 8.3, 8.6, 8.9): health scaling, phase thresholds, the
 * Resonance Ward, pylon resonance, sentinel counts, Surge cover, re-sing and the rematch values.
 * No world access, so the game tests check them directly.
 */
public final class CollectorRules {
	/** Phase numbers. Transitions sit between them; the yield follows phase 3. */
	public static final int PHASE_ONE = 1;
	public static final int PHASE_TWO = 2;
	public static final int PHASE_THREE = 3;

	/** Participants counted for scaling: at least 1, at most {@code scalingCap}. */
	public static int scaledCount(int participants, CollectorBalance b) {
		return Mth.clamp(participants, 1, Math.max(1, b.scalingCap));
	}

	/** Max health: 450 x (1 + 0.6 x (n - 1)), n capped at 4; x {@code unchainedHealth} when Unchained (8.6, 8.9). */
	public static float maxHealth(int participants, boolean unchained, CollectorBalance b) {
		int n = scaledCount(participants, b);
		float health = b.maxHealth * (1f + b.perParticipant * (n - 1));
		return unchained ? health * b.unchainedHealth : health;
	}

	/**
	 * The health that keeps its fraction when the maximum changes (a participant joining in phase 1):
	 * {@code health / oldMax x newMax}.
	 */
	public static float keepFraction(float health, float oldMax, float newMax) {
		return oldMax <= 0f ? newMax : health / oldMax * newMax;
	}

	/** Where phase 3 begins: {@code phase3At}, or {@code unchainedPhase3At} when Unchained. */
	public static float phase3At(boolean unchained, CollectorBalance b) {
		return unchained ? b.unchainedPhase3At : b.phase3At;
	}

	/** The phase a health fraction belongs to (ignoring transitions): 1 above 60%, 2 above 30% (40% Unchained), else 3. */
	public static int phaseFor(float fraction, boolean unchained, CollectorBalance b) {
		if (fraction > b.phase2At) {
			return PHASE_ONE;
		}

		return fraction > phase3At(unchained, b) ? PHASE_TWO : PHASE_THREE;
	}

	/**
	 * The health fraction a phase cannot go below: the next threshold, where the transition (or, after
	 * phase 3, the yield) begins. An echo never yields: its phase 3 floor is 0, and it dissolves there.
	 */
	public static float floorFraction(int phase, boolean echo, boolean unchained, CollectorBalance b) {
		return switch (phase) {
			case PHASE_ONE -> b.phase2At;
			case PHASE_TWO -> phase3At(unchained, b);
			default -> echo ? 0f : b.yieldAt;
		};
	}

	/**
	 * Damage taken under the Resonance Ward: x{@code wardHigh} with 3-4 pylons singing, x{@code wardLow}
	 * with 1-2, x1 with none, or once the ward is down for the phase. Unsung overrides it with
	 * x{@code unsungMultiplier}.
	 */
	public static float wardMultiplier(int singing, boolean unsung, boolean wardDown, CollectorBalance b) {
		if (unsung) {
			return b.unsungMultiplier;
		}

		if (wardDown || singing <= 0) {
			return 1f;
		}

		return singing >= 3 ? b.wardHigh : b.wardLow;
	}

	/** A pylon focus's resonance: 30 x (1 + 0.25 x (n - 1)), n capped like the health. */
	public static float pylonResonance(int participants, CollectorBalance b) {
		int n = scaledCount(participants, b);
		return b.pylonResonance * (1f + b.pylonScale * (n - 1));
	}

	/** Bound Watchers in phase 2: 2, +1 per extra participant, at most {@code sentinelsMax}. */
	public static int sentinelCount(int participants, CollectorBalance b) {
		int n = Math.max(1, participants);
		return Math.min(b.sentinelsMax, b.sentinels + (n - 1));
	}

	/**
	 * Surge damage multiplier for one participant (8.3): both rays blocked, nothing; one blocked,
	 * x{@code surgePartialMultiplier}; airborne at release, x{@code surgeAirborneMultiplier}, which stacks.
	 */
	public static float surgeMultiplier(boolean eyeBlocked, boolean chestBlocked, boolean airborne, CollectorBalance b) {
		if (eyeBlocked && chestBlocked) {
			return 0f;
		}

		float mult = eyeBlocked || chestBlocked ? b.surgePartialMultiplier : 1f;
		return airborne ? mult * b.surgeAirborneMultiplier : mult;
	}

	/**
	 * Whether a silenced pylon may start to regrow now (8.3): {@code resingTicks} after it fell
	 * silent, only while at least one other pylon sings, at most {@code resingPerPhase} times per
	 * pylon and phase, and never once the ward is down for the phase.
	 */
	public static boolean mayResing(long now, long silencedAt, int resungThisPhase, boolean anotherSinging, boolean wardDown, CollectorBalance b) {
		return !wardDown && anotherSinging && resungThisPhase < b.resingPerPhase && now - silencedAt >= b.resingTicks;
	}

	/** Outgoing damage multiplier: Unchained x{@code unchainedDamage} (it replaces the echo's), the echo x{@code echoDamageMultiplier}. */
	public static float outgoingMultiplier(boolean echo, boolean unchained, CollectorBalance b) {
		if (unchained) {
			return b.unchainedDamage;
		}

		return echo ? b.echoDamageMultiplier : 1f;
	}

	/**
	 * The Spent and Unsung damage taken: x{@code spentMultiplier} / x{@code unsungMultiplier} in total.
	 * His guard is broken in both, and a broken guard already takes {@code brokenGuardMult} (the saber
	 * pipeline's {@code npcBrokenDamageMult}), so this returns the remainder to apply.
	 */
	public static float remainderOverBrokenGuard(float total, boolean guardBrokenByPipeline, float brokenGuardMult) {
		return guardBrokenByPipeline && brokenGuardMult > 0f ? total / brokenGuardMult : total;
	}

	private CollectorRules() {
	}
}
