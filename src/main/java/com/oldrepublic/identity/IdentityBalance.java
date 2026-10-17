package com.oldrepublic.identity;

/**
 * Tunable numbers of the identity feature: alignment bands and caps, contextual act amounts,
 * reputation tiers, mentor trust, yielding and the Frontier Settler. Stored in the {@code identity}
 * section of {@code config/oldrepublic/balance.json}; read on the server only through
 * {@link Identity#balance()}.
 */
public final class IdentityBalance {
	/** Alignment at or beyond which the band is Light / Dark. */
	public int lightThreshold = 50;
	/** Alignment at or beyond which the band is Leaning to the Light / Dark. */
	public int leaningThreshold = 15;
	/** Side passive nodes, tier 1: Jedi need at least +this, Sith at most -this. */
	public int tier1Alignment = 15;
	/** Side passive nodes, tier 2. */
	public int tier2Alignment = 40;

	/** Contextual shifts per direction and in-game day. */
	public int minorDailyCap = 6;
	/** Contextual shifts never move alignment beyond plus or minus this. */
	public int minorLimit = 60;
	/** Killing innocents: at most this much darkness per in-game day. */
	public int severeDailyCap = 24;

	public int helpedOther = 1;
	public int defendedInnocent = 1;
	public int cruelty = -1;
	public int harmedInnocent = -2;
	public int killedVillager = -4;
	public int killedSettler = -8;
	/** Killing a trooper that was not hostile to the player (minor dark). */
	public int killedSoldier = -2;
	/** A hostile that hurt an innocent within this many ticks counts as defending them when killed. */
	public int defendWindowTicks = 100;
	/** Harming the same innocent (or striking the same yielded enemy) counts once per this many ticks. */
	public int harmRepeatTicks = 200;

	/** Reputation tier edges (Hostile at or below, Distrusted at or below, Trusted from, Honoured from). */
	public int repHostile = -500;
	public int repDistrusted = -100;
	public int repTrusted = 100;
	public int repHonoured = 500;
	/** Reputation with the joined faction when a path is chosen. */
	public int joinRep = 150;
	/** Reputation with the other side's faction when a path is chosen. */
	public int rivalRep = -100;
	/** Frontier reputation lost for each settler the player kills. */
	public int settlerKilledRep = -100;
	/** Republic reputation seeded at Chapter Three SUMMONS, once, by path (spec 5.1). */
	public int republicSeedJedi = 100;
	public int republicSeedSith = -100;
	public int republicSeedIndependent = 0;
	/** Reputation with a trooper's army for killing it while it was not hostile (never capped; spec 5.2). */
	public int neutralKillRep = -40;
	/** Reputation with a trooper's army for killing it while it was hostile (daily cap below). */
	public int hostileKillRep = -10;
	/** At most this much reputation per army and in-game day is lost to hostile kills; the non-hostile losses are never capped. */
	public int killRepDailyCap = 200;

	/** Trust with the chosen mentor on joining. */
	public int trustStart = 20;
	/** Trust lost for attacking one's mentor (the mentor feature applies it, at most once per 200 ticks). */
	public int trustAttackPenalty = 5;

	public int yieldTimeoutTicks = 2400;
	public int yieldLeaveDistance = 32;
	public int executeConfirmTicks = 60;

	public float settlerHealth = 16f;
	public float settlerSpeed = 0.5f;
	/** Damage multiplier for captive settlers hit by player Force area effects. */
	public float captiveAreaDamageMult = 0.25f;
	public int settlerFleeDistance = 24;
	public int settlerFleeTicks = 600;

	/** Dialogue sessions end when the player is farther than this from the speaker. */
	public double dialogueEndDistance = 8;
	/** A choice is only accepted within this distance of the speaker. */
	public double dialogueChoiceDistance = 6;

	/** Turns the contextual (minor and severe) alignment detection on or off; story choices still apply. */
	public boolean contextualAlignment = true;
}
