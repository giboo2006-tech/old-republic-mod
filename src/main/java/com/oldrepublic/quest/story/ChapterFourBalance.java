package com.oldrepublic.quest.story;

/**
 * Balance section {@code chapter_four} (spec MILESTONE_4.md 14): Chapter Four's timings, rewards
 * and the hunt. Server side; read it through {@link ChapterFourService#balance()}. Pure rules take
 * an instance, so tests and the client (the journal page) use the defaults
 * ({@code new ChapterFourBalance()}). The duel's own rewards are the {@code kaast} section's (W6's
 * {@code KaastBalance}).
 */
public final class ChapterFourBalance {
	/** MARKED lasts this long before the seeker is due; waking from this much sleep makes it due at once. */
	public int waitTicks = 24000;
	public int minSleepTicks = 100;
	/** The seeker droid comes down this far from above the player and scans for this long. */
	public int seekerDescend = 20;
	public int seekerScanTicks = 40;
	/** No hostile may be this close for the seeker scene (8.1; addition to spec 14). */
	public int seekerHostileRange = 24;

	public int briefXp = 300;
	public int partXp = 300;
	/** Sense within this many blocks of a Wreck Field's {@code HEART} anchor finds the motivator. */
	public int salvageRange = 8;
	public int offTheBooksXp = 1200;
	public int offTheBooksCredits = 100;

	public int arcMissionXp = 1500;
	public int arcPowerMissionXp = 2000;
	public int arcMissionCredits = 150;
	public int expeditionXp = 2000;
	public int expeditionSp = 1;
	public int arcRep = 40;
	public int arcPowerRep = 60;
	public int arcPowerTrust = 10;
	/** Discovery XP on the first landing on a planet, and on the first visit to its hub (8.3; addition to spec 14). */
	public int planetXp = 400;
	public int hubXp = 200;

	/** The Heartstone's cleansing: its length, the Barrier grant, and the three waves (ticks from the start). */
	public int heartstoneTicks = 1800;
	public int heartstoneGrantTicks = 900;
	public int[] heartstoneWaves = {0, 600, 1200};
	/** Each Heartstone wave: corrupted hollowhorns and Watcher sentinels (addition to spec 14). */
	public int heartstoneHollowhorns = 2;
	public int heartstoneSentinels = 1;
	/** Standing this close to the Heartstone (or Zarvos's echo) counts as being in its ring (addition to spec 14). */
	public int trialRing = 12;
	/** Zarvos's echo holds the player this long before Choke is granted. */
	public int sepulchreGripTicks = 40;

	public int sabotageXp = 300;
	public int ambushXp = 800;
	public int[] ambushSize = {3, 4};
	public int[] ambushRange = {24, 32};
	public int ambushMinDistance = 96;
	public int ambushGapDays = 2;
	public int ambushLeaveRange = 160;
	public int ambushRetryDays = 1;

	public int duelLevel = 14;
	/** Shown on the CHALLENGED objective (addition to spec 14). */
	public int recommendedLevel = 15;
	/** The marker beacon lands this far ahead of the player (addition to spec 14). */
	public int beaconDistance = 6;
	/** The owner within this many blocks of the hunting camp's anchor is in its ring (addition to spec 14). */
	public int campRing = 20;
	/** The camp's stash (10.6): alloy plates, hyperfuel cells, and the credits given when no Armstech schematic is lacking. */
	public int stashAlloy = 2;
	public int stashFuel = 4;
	public int stashCredits = 200;
	public int reportXp = 800;
	public int reportTrust = 10;
	public int reportResonance = 300;
	public int spareLight = 10;
	public int endDark = -10;
	public int endJediTrust = -15;
	public int endSithTrust = 10;

	public boolean hunterBounties = true;
	public float hunterChance = 0.10f;
	public int hunterCheckTicks = 1200;
	/** A killed hunter's chance of a Mandalorian armour schematic piece (7.3, 10.1: 8%), and the kills by which one is guaranteed. */
	public float hunterSchematicChance = 0.08f;
	public int hunterSchematicPity = 10;

	/** A failed placement (the hulk, the camp, an ambush) retries this often; the hulk falls forward after this many (8.8). */
	public int retryTicks = 1200;
	public int forwardFallbackAfter = 3;
	/** The forward fallback: this many blocks ahead of the mentor (8.8). */
	public int forwardFallback = 12;
}
