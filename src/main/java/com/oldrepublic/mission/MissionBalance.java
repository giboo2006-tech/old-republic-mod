package com.oldrepublic.mission;

/**
 * Tunable numbers of the mission feature (spec sections 5.4 to 5.7 and 14): story mission pay, the
 * contract board and its soft cap, mission sites, yields, tending, the interrogation pull,
 * discovery and the tracker. Stored in the {@code missions} section of
 * {@code config/oldrepublic/balance.json}; read on the server only through
 * {@link MissionFeature#balance()}. The per-choice rewards of the four story missions live in
 * their mission JSON, not here.
 */
public final class MissionBalance {
	/** XP paid by every story mission. */
	public int storyXp = 1200;
	/** Commendations of the giver's army paid by a story mission unless its choice says otherwise. */
	public int storyCommendations = 2;
	/** A Jedi's Republic gains from Republic work also pay this share as Jedi Order standing. */
	public double jediOrderShare = 0.5;

	/** Contracts each bounty terminal offers a player per in-game day. */
	public int offersPerBoard = 2;
	/** Mentor duties per in-game day. */
	public int dutiesPerDay = 1;
	/** Used board offers remembered per day (terminal and offer index). */
	public int usedOffersMax = 16;
	/** Contract completions per in-game day that pay in full. */
	public int fullPayPerDay = 3;
	/** XP multiplier of the completions past the full-pay count (no currency or reputation then). */
	public double overCapMultiplier = 0.25;

	/** Contract XP = min(contractXpCap, contractXpBase + contractXpPerLevel x level). */
	public int contractXpBase = 250;
	public int contractXpPerLevel = 40;
	public int contractXpCap = 900;
	/** Issuer reputation paid by a contract. */
	public int contractRep = 25;
	/** Commendations of the issuing army paid by an army contract. */
	public int contractCommendations = 1;
	/** Mentor trust paid by a duty. */
	public int dutyTrust = 5;
	/** Own-faction reputation paid by a duty. */
	public int dutyRep = 25;

	/**
	 * The distance band (blocks from the giver: the board, else the player) a runtime mission site is
	 * placed in by far placement, on the ground its template prefers (docs/MILESTONE_6.md 3.2).
	 */
	public int[] siteRange = {300, 600};
	/** Far placement never puts a mission site nearer the giver than this, whatever {@link #siteRange} says. */
	public int siteMinRange = 200;
	/** Rings (blocks from the player) of the near search a mission site falls back to when far placement fails, searched together. */
	public int[] siteRings = {96, 128, 144};
	/** Rings searched when the main rings found nothing. */
	public int[] siteFallback = {48, 80};
	/** A site's entities spawn the first time its owner comes this close. */
	public int siteSpawnDistance = 64;
	/** Minimum distance between a new mission site and other players' story and mission sites. */
	public int siteSeparation = 48;
	/** A survey contract looks for an unsurveyed ruin, tomb or wreck this close. */
	public int surveyRadius = 512;
	/** Sense must be cast this close to the site's heart to complete a survey. */
	public int surveyHeartRange = 8;
	/** A secure contract clears the nearest ruin, tomb or wreck this close. */
	public int secureRadius = 384;
	/** Ticks between placement attempts while a mission site could not be placed (addition, not in spec 14). */
	public int siteRetryTicks = 200;
	/** After this many failed placements the site goes straight ahead of the player (addition, not in spec 14). */
	public int siteForwardFallbackAfter = 3;
	/** A board's offers can be accepted this many blocks from its terminal (addition, not in spec 14). */
	public int boardReach = 8;

	/**
	 * Health fractions at which the mission's yielding enemies yield. The trooper sergeants read
	 * {@code interceptYield} and {@code borderYield} (W4); the R2 chief and the I1 fence are poacher
	 * captains given {@code chiefYield} and {@code fenceYield} at spawn ({@code Yielding.setYieldFraction}).
	 */
	public double interceptYield = 0.25;
	public double borderYield = 0.20;
	public double chiefYield = 0.25;
	public double fenceYield = 0.25;

	/** Ticks a player kneels over a downed trooper to tend them. */
	public int tendTicks = 60;
	/** Channelling Force Heal this close to a downed mission trooper revives it at once. */
	public int healTendRange = 4;
	/** A downed trooper bleeds out this long after its mission ends untended (the trooper reads it). */
	public int downedBleedTicks = 2400;
	/** Escorts and their prisoner are removed this long after the hand-over at the latest. */
	public int escortDespawnTicks = 300;

	/** The interrogation pull (and the freed settler's lead) lasts this long. */
	public int interrogatePullTicks = 12000;
	/** The interrogation pull points to an unused chamber this close. */
	public int interrogateRadius = 1024;

	/** The Calling points to an unvisited outpost of an army this close (addition, not in spec 14). */
	public int outpostGuidanceRadius = 1024;

	/** XP for discovering the first Jedi Ruin, Sith Tomb or Wreck Field. */
	public int discoveryStructureXp = 300;
	/** XP for discovering the first outpost of each army. */
	public int discoveryOutpostXp = 150;

	/** Ticks between tracker hints while a mission is active. */
	public int trackerHintTicks = 20;
	/** Beyond this many blocks the tracker shows Far. */
	public int trackerNear = 160;
	/** Within this many blocks the tracker shows Here. */
	public int trackerHere = 48;
	/** Ticks between Calling pulses while the journal is held. */
	public int guidanceIntervalTicks = 40;
}
