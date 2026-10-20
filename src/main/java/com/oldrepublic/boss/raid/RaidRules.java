package com.oldrepublic.boss.raid;

import com.oldrepublic.boss.kaast.KaastRules;

import net.minecraft.util.Mth;

/**
 * The Long Hymn's pure rules (spec MILESTONE_5.md 12.3, 12.4, 11.4), apart from the entities so the
 * tests can check them without a world: the raid week, the scaling from 1 to 8 players with the M4
 * health cap, the phases, the choir's and the shades' and the basalt marks' counts, and the
 * schematic's pity.
 */
public final class RaidRules {
	public static final int PHASE_CHOIR = 1;
	public static final int PHASE_VERSES = 2;
	public static final int PHASE_HYMN = 3;

	/** The raid week of in-game day {@code day}: {@code day / weekDays} (week 0 is a real week). */
	public static long week(long day, RaidBalance b) {
		return Math.floorDiv(day, Math.max(1, b.weekDays));
	}

	/** The participant count the scaling uses: at least 1, at most {@code maxPlayers}. */
	public static int scaled(int participants, RaidBalance b) {
		return Mth.clamp(participants, 1, Math.max(1, b.maxPlayers));
	}

	/** The scaling factor for {@code participants}: 1 + {@code perParticipant} x (n - 1). */
	public static double factor(int participants, RaidBalance b) {
		return 1.0 + b.perParticipant * (scaled(participants, b) - 1);
	}

	/** The First Cantor's maximum health: 2,000 x (1 + 0.5 x (n - 1)). */
	public static double maxHealth(int participants, RaidBalance b) {
		return b.maxHealth * factor(participants, b);
	}

	/** A choir echo's maximum health, scaled like the Cantor's. */
	public static double choirHealth(int participants, RaidBalance b) {
		return b.choirHealth * factor(participants, b);
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
	public static int phaseOf(float fraction, RaidBalance b) {
		if (fraction > b.phase2At) {
			return PHASE_CHOIR;
		}

		return fraction > b.phase3At ? PHASE_VERSES : PHASE_HYMN;
	}

	/** The shades of one wave: {@code shades}, plus one for every two participants beyond two (5 at eight). */
	public static int shades(int participants, RaidBalance b) {
		return Math.max(0, b.shades) + Math.max(0, scaled(participants, b) - 2) / 2;
	}

	/** The basalt verse's marks: {@code basaltMarks}, plus one for each participant beyond four. */
	public static int basaltMarks(int participants, RaidBalance b) {
		return Math.max(0, b.basaltMarks) + Math.max(0, scaled(participants, b) - 4);
	}

	/** Whether a newcomer may still join: within {@code joinTicks} of the Hymn's start, with room left. */
	public static boolean mayJoin(int age, int participants, RaidBalance b) {
		return age <= b.joinTicks && participants < Math.max(1, b.maxPlayers);
	}

	/** A participant away (alive and out, another dimension, offline) this long leaves the raid. */
	public static boolean leftRaid(int awayTicks, RaidBalance b) {
		return awayTicks >= b.participantLeaveTicks;
	}

	/** The Hymn stops once no participant has been alive inside for {@code wipeResetTicks}. */
	public static boolean wiped(int emptyTicks, RaidBalance b) {
		return emptyTicks >= b.wipeResetTicks;
	}

	/**
	 * Whether this clear gives a legendary schematic (11.4): always once {@code pity} clears in a row
	 * gave none, else when {@code draw} (0 to 1) falls under {@code schematicChance}.
	 */
	public static boolean schematicDue(int misses, float draw, RaidBalance b) {
		return b.schematicPity > 0 && misses >= b.schematicPity || draw < b.schematicChance;
	}

	/** The damage a hit does while a silenced point's window is open. */
	public static float windowMultiplier(boolean windowOpen, RaidBalance b) {
		return windowOpen ? Math.max(1f, b.windowMultiplier) : 1f;
	}

	private RaidRules() {
	}
}
