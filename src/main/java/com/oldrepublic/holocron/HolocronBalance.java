package com.oldrepublic.holocron;

import com.oldrepublic.config.BalanceConfig;

/**
 * The {@code holocron} section of {@code balance.json} (spec section 14): attunement rewards, the
 * opposed-side shifts, return and carry, progression, echoes and shards, restoring and the Archive.
 */
public final class HolocronBalance {
	/** XP for a first attunement, and the bonus fraction with {@code relic_lore}. */
	public int baseXp = 600;
	/** Resonance milestone {@code holocron_<id>}. */
	public int resonance = 120;
	public float relicLoreXpBonus = 0.25f;

	/** Major shifts of the opposed-side choices (4.5). */
	public int sealShift = 3;
	public int jediListenShift = -4;
	public int breakShift = -3;
	public int sithListenShift = 4;
	/** Extra Resonance for a Jedi-path Listen (to a Sith holocron) and for Break; Sorn's trust for Break. */
	public int listenResonance = 80;
	public int breakResonance = 80;
	public int breakTrust = 10;

	/** Carry bonus (4.6): regen percent per distinct current-serial holocron, the cap, and the refresh period. */
	public int carriedRegenPct = 4;
	public int carriedMax = 3;
	public int carryRefreshTicks = 20;

	/** Returning a holocron: mentor trust, reputation and commendations from an officer. */
	public int returnTrust = 10;
	public int returnRep = 40;
	public int returnCommendations = 1;
	/** "Make amends" at a Wary officer: standing gained and shards taken. */
	public int amendsRep = 40;
	public int amendsShards = 2;

	/** No awake hostile creature within this range of a pedestal. */
	public int pedestalClearRange = 16;

	/** One skill point for every this many world holocrons (4.7). */
	public int spEvery = 2;
	public int milestone3Resonance = 150;
	public int milestone6Sp = 1;
	public int milestone9Resonance = 250;
	public int milestone12Resonance = 400;

	/** Echoes (4.7): XP, shards, the size of the claimed list, and shards per commendation. */
	public int echoXp = 150;
	public int echoShards = 1;
	public int echoRing = 64;
	public int shardsPerCommendation = 2;

	/** Ticks per restore (one restore per this many ticks of game time: one day). */
	public int restoreCooldownTicks = 24000;

	/** The rise of the attunement moment, and the delay before the gatekeeper and the dialogue. */
	public int riseTicks = 30;
	public int gatekeeperDelayTicks = 30;
	/** Force Sense's follow-up pulse window (read by Force Sense). */
	public int senseFollowUpTicks = 40;

	/** The Archive (4.8). */
	public int archiveXp = 300;
	public int archiveReturnOwnRep = 150;
	public int archiveReturnRepublicRep = 60;
	public int archiveReturnTrust = 20;
	public int archiveReturnLight = 5;
	public int archiveReturnFrontierRep = 100;
	public int archiveReturnIndependentLight = 3;
	public int archiveKeepJediShift = -6;
	public int archiveKeepIndependentShift = -2;
	public int archiveUnmakeResonance = 400;
	public int archiveUnmakeLight = 3;
	public int archiveUnmakeRepLoss = -50;

	/** The current section (defaults when the file has none). */
	public static HolocronBalance get() {
		return BalanceConfig.section("holocron", HolocronBalance.class, HolocronBalance::new);
	}
}
