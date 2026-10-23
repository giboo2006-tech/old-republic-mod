package com.oldrepublic.quest.story;

/**
 * Balance section {@code chapter_three} (spec section 14): Chapter Three's timings, placement rings,
 * rewards and the rank 3 requirements. Server side; read it through
 * {@link ChapterThreeService#balance()}. Pure rules take an instance, so tests and the client (the
 * Standing checklist) use the defaults ({@code new ChapterThreeBalance()}).
 */
public final class ChapterThreeBalance {
	/** WAITING lasts this long before the dream is due (any dimension). */
	public int waitTicks = 24000;
	/** Waking from at least this much sleep while WAITING makes the dream due at the next safe moment. */
	public int minSleepTicks = 100;
	/** Length of "The Ones That Sing" dream (addition to spec 14; the M2 dream's length). */
	public int dreamTicks = 120;

	/** Leads needed to leave LEADS, and the level the Reliquary waits for. */
	public int leadsNeeded = 4;
	public int reliquaryMinLevel = 9;
	/** Shown on the RELIQUARY objective. */
	public int recommendedLevel = 10;

	/** LISTENING rewards: the three cairns (Jedi, Sith) or caches (Independent). */
	public int lessonXp = 400;
	public int cacheXp = 400;
	public int lessonTrust = 10;
	public int lessonRep = 40;
	/** Cairns: how many, how far from the camp at most, and how far apart at least. */
	public int cairnCount = 3;
	public int cairnRadius = 24;
	public int cairnSpacing = 8;
	/** The caster's own untouched cairns and caches within this range answer Sense (addition to spec 14). */
	public int cairnSenseRange = 24;

	/** Courier's Fall: the far band [min, max] from the player (MILESTONE_6.md 3.2), then the near rings and fallback rings. */
	public int[] fallRange = {500, 800};
	public int[] fallRings = {64, 80, 96};
	public int[] fallFallback = {32, 48};
	/** The Independent's caches lie this far from the fall. */
	public int cacheMin = 12;
	public int cacheMax = 24;

	/** The first-echo search radius; an Echo Shrine's far band from the player, then its near rings when nothing is found. */
	public int firstEchoRadius = 1024;
	public int[] shrineRange = {400, 700};
	public int[] shrineRings = {96, 128};
	public int[] shrineFallback = {48, 80};
	/** The old-world guarantee: at most this many extra Echo Shrines per needed kind. */
	public int extraShrinesPerKind = 1;

	/** The Reliquary's far band from the player, then its near rings (the command, and a failed far request). */
	public int[] reliquaryRange = {600, 900};
	public int[] reliquaryRings = {96, 128, 144};
	public int[] reliquaryFallback = {48, 80};

	/** A failed placement retries this often; after this many failures the M2 forward fallback is used. */
	public int retryTicks = 1200;
	public int forwardFallbackAfter = 3;

	/** Vasch's presence: the holocron message at this many leads, the counter-hunt at that many. */
	public int vaschMessageLeads = 2;
	public int ambushLeads = 3;
	/** "Give them back.": a MINOR light shift. */
	public int meetGiveBackLight = 1;
	/** The ambush waits until the player is this far from every site. */
	public int ambushMinSiteDistance = 64;
	/** The ambushers spawn this far away (min, max), and despawn when the player is this far. */
	public int[] ambushRange = {24, 32};
	public int ambushDespawnDistance = 128;

	/** The report. */
	public int reportXp = 600;
	public int reportTrust = 10;
	public int reportRep = 50;
	public int reportResonance = 300;

	/** Rank 3 (Knight, Lord). */
	public int rank3MinLevel = 11;
	public int rank3MinTrust = 80;
	public int rank3MinRep = 500;
	public int rank3Alignment = 15;
	public int rank3Holocrons = 2;
	public int rank3Contracts = 3;
	/** Pathfinder (Independent title). */
	public int pathfinderHolocrons = 3;
	public int pathfinderContracts = 3;
	/** Reputation the Pathfinder needs with at least one faction (Trusted). */
	public int pathfinderTier = 100;
	/** Rank 3 and Pathfinder rewards. */
	public int rank3Xp = 1000;
	public int rank3Sp = 2;
	public int rank3Resonance = 700;
}
