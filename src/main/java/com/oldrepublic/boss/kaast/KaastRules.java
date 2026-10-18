package com.oldrepublic.boss.kaast;

import net.minecraft.util.Mth;

/**
 * The Kaast fight's pure rules (spec MILESTONE_4.md 10.5), apart from the entities so the tests can
 * check them without a world.
 */
public final class KaastRules {
	/** What tried to interrupt him. Only the listed ones do (the M3 rule). */
	public enum Interrupt {
		/** A bolt of his a saber parried back at him. */
		PARRIED_BOLT,
		/** A parried blow of his axe combo. */
		PARRIED_COMBO,
		/** A Push that reached him on a nest. */
		PUSH_OFF_NEST,
		/** A heavy blow while he kneels to set a mine. */
		KNEEL_HEAVY,
		/** A heavy bolt returned at him. */
		RETURNED_HEAVY_BOLT,
		/** Anything else: a stagger, a stun, a shock, a Push on the ground. */
		OTHER
	}

	/** The phase (1, 2, 3) for a health fraction. */
	public static int phaseOf(float fraction, KaastBalance b) {
		if (fraction > b.phase2At) {
			return 1;
		}

		return fraction > b.phase3At ? 2 : 3;
	}

	/** The participant count the scaling uses: at least 1, at most {@code scalingCap}. */
	public static int scaled(int participants, KaastBalance b) {
		return Mth.clamp(participants, 1, Math.max(1, b.scalingCap));
	}

	/** Maximum health for {@code participants} (the M3 formula). */
	public static double maxHealth(int participants, KaastBalance b) {
		return b.maxHealth * (1.0 + b.perParticipant * (scaled(participants, b) - 1));
	}

	public static int mines(int participants, KaastBalance b) {
		return b.mines + b.minesPerParticipant * (scaled(participants, b) - 1);
	}

	public static int hunters(int participants, KaastBalance b) {
		return b.hunters + b.huntersPerParticipant * (scaled(participants, b) - 1);
	}

	/** Whether he yields now. */
	public static boolean yieldDue(float health, float maxHealth, KaastBalance b) {
		return maxHealth > 0f && health <= maxHealth * b.yieldAt + 1.0e-3f;
	}

	/** The lowest his health goes before the yield: nothing kills him. */
	public static float floor(float maxHealth, KaastBalance b) {
		return Math.max(1f, maxHealth * b.yieldAt);
	}

	/** How long a Choke holds him: only in phase 3, and only once. */
	public static int chokeTicks(int phase, boolean used, int requested, KaastBalance b) {
		return phase == 3 && !used ? Math.min(requested, b.chokeTicks) : 0;
	}

	/** Whether {@code kind} interrupts him now. */
	public static boolean interrupts(Interrupt kind, int phase, boolean onNest, boolean kneeling) {
		return switch (kind) {
			case PARRIED_BOLT, RETURNED_HEAVY_BOLT -> true;
			case PARRIED_COMBO -> phase == 3;
			case PUSH_OFF_NEST -> phase == 1 && onNest;
			case KNEEL_HEAVY -> kneeling;
			default -> false;
		};
	}

	/** A control effect's length on him: shortened by {@code controlShorten}. */
	public static int controlTicks(int ticks, KaastBalance b) {
		return Math.max(0, Math.round(ticks * (1f - b.controlShorten)));
	}

	/** Lift on him: refused, except up to {@code liftFallBlocks} during a phase 1 fall stagger. */
	public static float liftHeight(boolean fallStagger, float requested, KaastBalance b) {
		return fallStagger ? Math.min(requested, b.liftFallBlocks) : 0f;
	}

	/** His rifle bolt's damage; more against a player Rattle is scanning. */
	public static float rifleDamage(boolean marked, KaastBalance b) {
		return b.rifleDamage * (marked ? 1f + b.markBonus : 1f);
	}

	/** Whether he calls his hunters now: once, at {@code huntersAt}. */
	public static boolean huntersDue(float fraction, boolean called, KaastBalance b) {
		return !called && fraction <= b.huntersAt;
	}

	/** Whether the camp resets: nobody in the ring for {@code resetTicks}. */
	public static boolean resetDue(int emptyTicks, KaastBalance b) {
		return emptyTicks >= b.resetTicks;
	}

	/** A participant out of the ring this long leaves the fight. */
	public static boolean leftFight(int outsideTicks, KaastBalance b) {
		return outsideTicks >= b.participantLeaveTicks;
	}

	/** The health that starts {@code phase} when an operator jumps to it: just under its threshold. */
	public static float healthForPhase(int phase, float maxHealth, KaastBalance b) {
		return switch (phase) {
			case 2 -> maxHealth * b.phase2At - 1f;
			case 3 -> maxHealth * b.phase3At - 1f;
			default -> maxHealth;
		};
	}

	private KaastRules() {
	}
}
