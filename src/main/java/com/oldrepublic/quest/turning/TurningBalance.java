package com.oldrepublic.quest.turning;

/**
 * Balance section {@code turning} (spec MILESTONE_5.md 16.4): the offers, the steps of the Fall and the Return, what
 * turning costs and the rank 4 requirements and ceremonies. Server side; read it through
 * {@link TurningService#balance()}. Pure rules take an instance, so tests use the defaults
 * ({@code new TurningBalance()}).
 */
public final class TurningBalance {
	/** When false the Fall and the Return are never offered (14.4). */
	public boolean enabled = true;
	/** A Jedi at or below this alignment, or a Sith at or above the other, sits in the turning's band (8.2). */
	public int fallAlignment = -50;
	public int returnAlignment = 50;
	/** In-game days in the band before the offer comes; a declined offer comes back after {@link #reofferDays}. */
	public int bandDays = 1;
	public int reofferDays = 3;
	/** The refusal that ends the offers until the alignment leaves the band and comes back. */
	public int refusals = 3;

	/** The task's rewards (8.3): XP, the new order's gain, the old order's loss, and the alignment shift. */
	public int taskXp = 1500;
	public int taskRepGain = 100;
	public int taskRepLoss = -150;
	public int fallDark = -5;
	public int returnLight = 5;

	public int confrontationXp = 1500;
	/** Taking Saelen's crystal (dark) and breaking Sorn's blade (light). */
	public int crystalDark = -8;
	public int bladeLight = 3;
	/** A death during the confrontation resets it to its start after this long. */
	public int confrontationResetTicks = 1200;

	public int riteXp = 1000;
	public int riteResonance = 300;
	/** What turning costs (8.4): the new mentor's trust, the refund of the old tree, the rank cap and the reputations. */
	public int newTrust = 40;
	public int refundPct = 50;
	public int rankCap = 2;
	public int oldFactionCap = -500;
	public int newFactionBonus = 150;

	/** Rank 4's requirements (8.5). */
	public int rank4Level = 20;
	public int rank4Trust = 90;
	public int rank4Rep = 800;
	public int rank4Alignment = 50;
	public int rank4MasteryRank = 3;
	public int rank4MasteryPowers = 2;
	public int rank4Deeds = 2;
	public int deedEchoes = 3;
	public int deedBounties = 5;
	public int deedContracts = 5;
	/** A Wayfarer needs this many factions at {@link #wayfarerTier} reputation or better (Trusted). */
	public int wayfarerFactions = 3;
	public int wayfarerTier = 100;

	/** The ceremony's rewards, paid once per character ({@code rank4.paid}). */
	public int ceremonyXp = 1500;
	public int ceremonySp = 2;
	public int ceremonyResonance = 900;
}
