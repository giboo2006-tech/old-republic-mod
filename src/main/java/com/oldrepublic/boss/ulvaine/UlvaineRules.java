package com.oldrepublic.boss.ulvaine;

import com.oldrepublic.boss.kaast.KaastRules;

import net.minecraft.util.Mth;

/**
 * Darth Ulvaine's pure rules (spec MILESTONE_5.md 12.2), apart from the entities so the tests can check
 * them without a world: the M3 scaling with the M4 health cap, the phases, the yield floor, the pylons'
 * damage cut, phase 3's slower duel and the Crescendo's threshold.
 */
public final class UlvaineRules {
	public static final int PHASE_DUEL = 1;
	public static final int PHASE_STORM = 2;
	public static final int PHASE_HYMN = 3;

	/** The participant count the scaling uses: at least 1, at most {@code scalingCap}. */
	public static int scaled(int participants, UlvaineBalance b) {
		return Mth.clamp(participants, 1, Math.max(1, b.scalingCap));
	}

	/** Her maximum health for {@code participants} (the M3 formula): 600 x (1 + 0.6 x (n - 1)). */
	public static double maxHealth(int participants, UlvaineBalance b) {
		return b.maxHealth * (1.0 + b.perParticipant * (scaled(participants, b) - 1));
	}

	/** The max health attribute for a scaled maximum: at most {@link KaastRules#HEALTH_CAP} (the M4 trick). */
	public static double attributeHealth(double scaledMaxHealth) {
		return KaastRules.attributeHealth(scaledMaxHealth);
	}

	/** What each hit is multiplied by so the capped attribute lasts as long as the scaled health would. */
	public static float damageScale(double scaledMaxHealth) {
		return KaastRules.damageScale(scaledMaxHealth);
	}

	/** The phase (1, 2, 3) for a health fraction. */
	public static int phaseOf(float fraction, UlvaineBalance b) {
		if (fraction > b.phase2At) {
			return PHASE_DUEL;
		}

		return fraction > b.phase3At ? PHASE_STORM : PHASE_HYMN;
	}

	/** Whether she yields now (the real fight; the echo fades at 0 instead). */
	public static boolean yieldDue(float health, float maxHealth, UlvaineBalance b) {
		return maxHealth > 0f && health <= maxHealth * b.yieldAt + 1.0e-3f;
	}

	/** The lowest her health goes in the real fight: nothing kills her before the yield. */
	public static float floor(float maxHealth, UlvaineBalance b) {
		return Math.max(1f, maxHealth * b.yieldAt);
	}

	/** The damage she takes while a pylon chanter lives (phase 2): {@code pylonDamagePct} percent. */
	public static float pylonMultiplier(boolean chanterAlive, UlvaineBalance b) {
		return chanterAlive ? Mth.clamp(b.pylonDamagePct / 100f, 0f, 1f) : 1f;
	}

	/** Her duel's timings in phase 3: {@code slowPct} percent slower (longer wind-ups, gaps and recoveries). */
	public static int slowed(int ticks, int phase, UlvaineBalance b) {
		return phase >= PHASE_HYMN ? Math.round(ticks * (1f + Math.max(0f, b.slowPct) / 100f)) : ticks;
	}

	/** Whether the Crescendo begins now: in phase 3, at {@code crescendoAt}, once. */
	public static boolean crescendoDue(float fraction, int phase, boolean begun, UlvaineBalance b) {
		return !begun && phase >= PHASE_HYMN && fraction <= b.crescendoAt;
	}

	/** The fight resets once no participant has been alive inside for {@code resetTicks}. */
	public static boolean resetDue(int emptyTicks, UlvaineBalance b) {
		return emptyTicks >= b.resetTicks;
	}

	/** A participant out of the Heart (alive and away, another dimension, offline) this long leaves the bar. */
	public static boolean leftFight(int outsideTicks, UlvaineBalance b) {
		return outsideTicks >= b.participantLeaveTicks;
	}

	/** The health that starts {@code phase} when an operator or a test jumps to it: just under its threshold. */
	public static float healthForPhase(int phase, float maxHealth, UlvaineBalance b) {
		return switch (phase) {
			case PHASE_STORM -> maxHealth * b.phase2At - 1f;
			case PHASE_HYMN -> maxHealth * b.phase3At - 1f;
			default -> maxHealth;
		};
	}

	/** The radius the Hymn pulse's front has reached {@code age} ticks after it began. */
	public static double pulseFront(int age, UlvaineBalance b) {
		return b.pulseTicks <= 0 ? b.pulseRadius : b.pulseRadius * Mth.clamp(age / (double) b.pulseTicks, 0.0, 1.0);
	}

	/** Whether the echo may be called today: Chapter Five complete, not yet today, not Peaceful. */
	public static boolean echoDue(long echoDay, long today) {
		return echoDay != today;
	}

	private UlvaineRules() {
	}
}
