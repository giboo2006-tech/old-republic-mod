package com.oldrepublic.boss.raid;

import com.oldrepublic.config.BalanceConfig;

/**
 * Balance section {@code raid} of {@code config/oldrepublic/balance.json} (spec MILESTONE_5.md 12.3,
 * 12.4, 16.4): the Long Hymn, the First Cantor and the raid session. Durations in ticks, distances in
 * blocks, fractions of the Cantor's health. Keys marked "addition" hold numbers the spec states in
 * prose or leaves open.
 */
public final class RaidBalance {
	// ------------------------------------------------ the session
	public int maxPlayers = 8;
	/** In-game days per raid week: the reward lockout is {@code Clocks.day / weekDays}. */
	public int weekDays = 7;
	public int countdownTicks = 200;
	public int joinTicks = 600;
	public int wipeResetTicks = 200;
	public int participantLeaveTicks = 100;

	// ------------------------------------------------ the First Cantor
	public double maxHealth = 2000;
	public double perParticipant = 0.5;
	public float phase2At = 0.70f;
	public float phase3At = 0.30f;
	/** Addition: armour and the transition between phases (the M3 rule). */
	public double armor = 8;
	public int transitionTicks = 60;
	/** Addition: how long he takes to rise out of the engine, and to unravel at the end. */
	public int riseTicks = 40;
	public int unravelTicks = 60;

	// ------------------------------------------------ phase 1: the choir
	public float choirHealth = 60f;
	public int choirReturnTicks = 900;
	/** Addition: a Push staggers an echo's song this long (diminished on the push-resistant echoes, the M2 rule). */
	public int choirStaggerTicks = 40;
	public int beamTell = 40;
	public float beamDamage = 8f;
	/** Addition: the beam's sweep (degrees, ticks, reach, width) and how often it comes. */
	public float beamArcDegrees = 120f;
	public int beamSweepTicks = 40;
	public float beamLength = 18f;
	public float beamWidth = 1.2f;
	public int beamInterval = 200;

	// ------------------------------------------------ phase 2: the verses
	public int slamTell = 20;
	public float slamDamage = 10f;
	/** Addition: the slam's reach, its knock-back, how often; the chain's range and how often. */
	public float slamRadius = 4f;
	public float slamKnockback = 1.2f;
	public int slamInterval = 140;
	public int chainTargets = 3;
	public float chainDamage = 5f;
	public float chainRange = 16f;
	public int chainInterval = 180;
	public int verseInterval = 600;
	public int rootsTell = 30;
	public float rootsRadius = 6f;
	public int rootsTicks = 60;
	public int fogPools = 3;
	public float fogWidth = 4f;
	public int fogTicks = 200;
	public float fogDamage = 2f;
	public int basaltMarks = 5;
	public int basaltTell = 40;
	public float basaltDamage = 10f;
	/** Addition: a basalt mark's radius and shock. */
	public float basaltRadius = 2f;
	public int basaltShockTicks = 20;
	public int shadeInterval = 1200;
	public int shades = 2;
	/** Addition: the first wave comes this long into the second phase; at most two waves stand at once. */
	public int shadeFirstTicks = 300;

	// ------------------------------------------------ phase 3: the long hymn
	public int ringTell = 30;
	public int ringTicks = 100;
	public float ringDamage = 4f;
	/** Addition: a floor ring's radius. */
	public float ringRadius = 4f;
	public int silenceTicks = 60;
	public int windowTicks = 400;
	public float windowMultiplier = 2f;
	/** Addition: how close a player stands to a silencing point to channel it. */
	public float silenceReach = 2.5f;

	// ------------------------------------------------ rewards (per participant, once a raid week)
	public int clearXp = 2000;
	public int clearShards = 3;
	public int clearCredits = 400;
	public float schematicChance = 0.25f;
	/** The 11.4 guarantee: after this many clears in a row without a schematic, the next one has it. */
	public int schematicPity = 2;
	public int helpXp = 300;
	public int firstClearResonance = 500;

	public static RaidBalance get() {
		return BalanceConfig.section("raid", RaidBalance.class, RaidBalance::new);
	}
}
