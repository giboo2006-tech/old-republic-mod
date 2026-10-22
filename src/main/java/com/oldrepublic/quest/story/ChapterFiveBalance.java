package com.oldrepublic.quest.story;

/**
 * Balance section {@code chapter_five} (spec MILESTONE_5.md 16.4): Chapter Five's timings, rewards
 * and choices. Server side; read it through {@link ChapterFiveService#balance()}. Pure rules take an
 * instance, so tests and the client (the journal page) use the defaults
 * ({@code new ChapterFiveBalance()}). The fight's helper XP is the {@code ulvaine} section's
 * ({@code helperXp}, W8); the faction numbers are {@code factions_m5}'s
 * ({@link com.oldrepublic.identity.FactionM5Balance}).
 */
public final class ChapterFiveBalance {
	/** WAITING lasts this long before the call is due; waking from this much sleep makes it due at once (6.1). */
	public int waitTicks = 24000;
	public int minSleepTicks = 100;

	/** The call (6.2) and the helm lesson's three sub-objectives. */
	public int callXp = 300;
	public int helmXp = 200;
	public int orbitXp = 300;
	public int jumpXp = 300;

	/** The kajidic's way (6.3): each job, the debt's two prices and the audience. */
	public int jobXp = 1200;
	public int jobCredits = 150;
	public int jobRep = 60;
	public int audienceRep = 80;
	public int debtCredits = 200;
	public int teelBribe = 150;

	/** The clan's way (6.3): the matriarch hunt and the duel of honour. */
	public int matriarchXp = 1500;
	public int matriarchCredits = 150;
	public int matriarchRep = 80;
	public int honourXp = 800;
	public int honourRep = 100;

	/** The Force's way (6.3): the kajidic's loss after Fear or a forced door, and after a Mind Trick. */
	public int forceWayXp = 600;
	public int forceWayRep = -50;
	public int mindTrickWayRep = -25;

	/** The counting-house (6.3): its pay, and its guards by the first way taken: kajidic, clan, Mind Trick, Fear or a forced door. */
	public int countingHouseXp = 1500;
	public int countingHouseCredits = 150;
	public int[] countingHouseGuards = {0, 6, 4, 8};

	/** The factor (6.4): every choice's XP, then each choice's standing and alignment. */
	public int daskXp = 300;
	public int releaseRep = 40;
	public int releaseLight = 3;
	public int clanRep = 80;
	public int clanKajidicRep = -60;
	public int endKajidicRep = -100;
	public int endClanRep = -20;
	public int endDark = -5;

	/** Arming and the blockade (6.5). */
	public int cannonsXp = 200;
	public int blockadeXp = 2000;
	public int blockadeCredits = 200;
	public int clanHelpFailures = 2;
	public int clanHelpCredits = 1000;

	/** The verse stones (6.6): each stone's pay, and the SP of the third silenced. */
	public int verseXp = 1500;
	public int verseCredits = 100;
	public int versesSp = 1;

	/** VERSES passes to THE_HYMNAL with three stones silenced at this level (6.1). */
	public int duelLevel = 17;
	/** The journal's recommended level for the fight (6.1; addition to spec 16.4). */
	public int recommendedLevel = 18;

	/** The fight's own reward, once whatever the choice (6.7). */
	public int ulvaineXp = 5000;
	public int ulvaineSp = 2;
	public int ulvaineCredits = 400;
	public int ulvaineResonance = 600;

	/** The choice over Ulvaine (6.7). */
	public int spareLight = 8;
	public int bindJediLight = 5;
	public int bindJediRep = 100;
	public int bindSithRep = 150;
	public int bindClanRep = 150;
	public int bindClanCredits = 500;
	public int endDarkUlvaine = -10;
	public int endJediTrust = -10;
	public int endSithTrust = 10;

	/** The report (6.7). */
	public int reportXp = 1000;
	public int reportTrust = 10;
	public int reportResonance = 400;

	/** Ticks before a placement, a search or a spawn that failed is tried again (addition to spec 16.4). */
	public int retryTicks = 200;
	/** The matriarch's hunting ground lies in this band [min, max] from Saltgate (6.3; MILESTONE_6.md 3.2). */
	public int[] matriarchRange = {300, 500};
	/** The owner within this many blocks of a story spot (the debtor's camp, the hunt, the counting-house's top) counts as there (addition to spec 16.4). */
	public int arriveRange = 24;
	/** The verse vision's length on screen (6.6, 15.3: 6 seconds; addition to spec 16.4). */
	public int visionTicks = 120;
	/** The matriarch hunt's brood, the matriarch included (6.3; addition to spec 16.4). */
	public int matriarchBrood = 4;
	/** The debtor's salvagers when the box is taken by force (3.2: the {@code scavengers} group, 2 to 3; addition to spec 16.4). */
	public int salvagers = 3;
	/** The duel of honour (6.3): the champion yields at this fraction of her health, and hits this hard (a real blade; addition to spec 16.4). */
	public float honourYield = 0.2f;
	public float honourDamage = 1.0f;
	/** Clan Hask warriors who break into the counting-house beside a player who took the clan's way (6.3; addition to spec 16.4). */
	public int clanAllies = 3;
	/** How long Ulvaine's hologram plays on Dask's desk (6.4, 15.3; addition to spec 16.4). */
	public int hologramTicks = 240;
}
