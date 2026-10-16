package com.oldrepublic.quest.story;

/**
 * Balance section {@code story}: timings, distances and rewards of Chapter Two (spec section 13,
 * plus the reputation, trust and alignment tables of sections 3.2, 4.2 and 6.3 that the story
 * applies).
 */
public final class StoryBalance {
	// The Two Voices
	public int crossingDelayTicks = 24000;
	public int crossingMinSleepTicks = 100;
	/** No damage from a mob for this long before the timed dream may start. */
	public int crossingSafeTicks = 100;
	/** Length of the Two Voices dream (6 s). */
	public int dreamTicks = 120;

	// Meeting and encounter sites
	public int[] meetingRings = {64, 96, 128};
	public int[] meetingFallback = {24, 40};
	public int siteAngles = 16;
	public int meetingFootprint = 9;
	/** Side of the area around a site that must hold no built blocks. */
	public int meetingClearance = 13;
	public int encounterFootprint = 13;
	public int encounterClearance = 17;
	public int maxSlope = 2;
	public int siteMinSeparation = 48;
	public int fallbackForward = 12;
	/** Smoke on the Ridge's fallback rings when nothing fits 96 to 160 blocks out. */
	public int[] encounterFallback = {48, 80};
	/** The journal's pull is at full strength this close to a story site. */
	public double callingFarDistance = 192;

	// The Crossing
	/** The owner arrives at the meeting point within this distance. */
	public double arrivalDistance = 24;
	/** Mentors are put in place once the owner is within this distance of the meeting point. */
	public double mentorSpawnDistance = 64;
	/** Mentors at the Crossing turn to face the owner within this distance. */
	public double mentorFaceDistance = 12;
	public double mentorSpacing = 6;
	public double departDistance = 16;
	public int departTimeoutTicks = 600;
	public int crossingXp = 300;

	// Mentors
	public int mentorLeash = 6;
	public double mentorLookDistance = 8;
	public int mentorAttackCooldownTicks = 200;
	public int mentorSummonTicks = 1200;
	/** "Ask them to come" is offered this far from the camp. */
	public double summonMinDistance = 48;
	/** Where a summoned mentor sets up the new camp, around the player. */
	public int[] summonRings = {8, 12};
	public int[] summonFallback = {4, 6};
	/** The owner must be this close to the mentor for a guided session or atonement. */
	public double sessionRadius = 8;
	/** A missing mentor is respawned after the owner has waited this long near an empty camp. */
	public int lostMentorTicks = 100;
	public double lostMentorRadius = 24;

	// Lessons
	public int lessonXp = 300;
	public int lessonTrust = 15;
	public int lessonRep = 40;
	public int lessonTwoTrust = 30;
	public int pullLessonTargets = 5;
	public int sithPullKills = 3;
	public int sithPullKillWindowTicks = 60;
	public int parryLessonCount = 3;
	public int techniqueLessonCount = 3;
	/** Health fractions at which the master and Brenn yield (a spar is then won). */
	public float masterYield = 0.35f;
	public float rivalLessonYield = 0.4f;
	public float rivalTrialYield = 0.15f;

	// Smoke on the Ridge
	public int encounterMinDist = 96;
	public int encounterMaxDist = 160;
	public double encounterSpawnDistance = 48;
	public int poachers = 4;
	public int extraPerPlayer = 1;
	public int extraMax = 3;
	public double extraPlayerRadius = 32;
	public int captiveCount = 3;
	public double abandonDistance = 128;
	public double cleanupDistance = 128;
	public int independentDelayTicks = 24000;
	public int reckoningXp = 1000;
	public int reckoningSp = 1;
	public int reckoningSpIndependent = 3;
	public int reckoningRep = 80;
	public int independentFrontierRep = 100;
	public int reckoningTrust = 10;

	// Choices (alignment is a one-time major shift per source id)
	public int captainSpareAlignment = 12;
	public int captainExecuteAlignment = -12;
	public int captainFledAlignment = 3;
	public int captivesFreeAlignment = 10;
	public int captivesPayAlignment = -10;
	public int captivesLeaveAlignment = -3;
	public int brennSpareAlignment = 15;
	public int brennKillAlignment = -15;
	public int brennSpareTrust = -20;
	public int brennKillTrust = 15;

	// Reputation by choice: Jedi path / Sith path / Frontier (any path)
	public int captainSparedJediRep = 20;
	public int captainExecutedJediRep = -40;
	public int captainSparedSithRep = -30;
	public int captainExecutedSithRep = 20;
	public int captivesFreedJediRep = 20;
	public int captivesPaidJediRep = -40;
	public int captivesFreedSithRep = -20;
	public int captivesPaidSithRep = 20;
	public int captivesFreedFrontierRep = 150;
	public int captivesPaidFrontierRep = -150;
	public int captivesLeftFrontierRep = -60;

	// Trust by choice
	public int captainSparedJediTrust = 10;
	public int captainExecutedJediTrust = -20;
	public int captainFledJediTrust = 0;
	public int captainSparedSithTrust = -15;
	public int captainExecutedSithTrust = 10;
	public int captainFledSithTrust = -5;
	public int captivesFreedJediTrust = 10;
	public int captivesPaidJediTrust = -20;
	public int captivesLeftJediTrust = -5;
	public int captivesFreedSithTrust = -5;
	public int captivesPaidSithTrust = 10;
	public int captivesLeftSithTrust = 0;

	// Trials
	public int trialMinLevel = 6;
	public int trialMinTrust = 60;
	public int trialMinRep = 100;
	public int trialXp = 800;
	public int trialSp = 2;
	public int trialRep = 150;
	public int trialRetryTicks = 600;
	public float jediTrialDamageScale = 0.6f;
	public float sithTrialDamageScale = 1f;

	// Resonance milestones
	public int resonanceCrossing = 200;
	public int resonanceReckoning = 300;
	public int resonanceFirstRank = 500;

	// Daily sessions
	public int sessionXp = 120;
	public int sessionTrust = 5;
	public int atoneTrust = 3;
	public int atoneAlignment = 4;

	// Server switches
	public boolean placeStructures = true;
	public boolean respectSpawnProtection = true;
}
