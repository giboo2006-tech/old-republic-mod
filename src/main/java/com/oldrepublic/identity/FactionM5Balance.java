package com.oldrepublic.identity;

import com.oldrepublic.config.BalanceConfig;

/**
 * Balance section {@code factions_m5} (spec MILESTONE_5.md 7, 16.4): the Vessba kajidic's and Clan
 * Hask's standing rules that are numbers, and the space bounty's pay (5.8). Server side; read it
 * through {@link #get()}. Rules take an instance, so tests pass {@code new FactionM5Balance()}.
 */
public final class FactionM5Balance {
	/** The section as configured. */
	public static FactionM5Balance get() {
		return BalanceConfig.section("factions_m5", FactionM5Balance.class, FactionM5Balance::new);
	}

	/** Clan Hask's first standing, set once on the first arrival at Saltgate, from Kaast's fate (7). */
	public int clanStartSpared = 100;
	public int clanStartStripped = 0;
	public int clanStartEnded = -150;
	/** Giving Varra Kaast's helmet back, once (7). */
	public int helmetReturnRep = 100;

	/** A contract at Saltgate's terminal or the holdfast's board (7.2). */
	public int contractRep = 25;
	/** A space bounty, paid in place of the contract formula (5.8): XP, credits and the board's faction. */
	public int bountyXp = 600;
	public int bountyCredits = 400;
	public int bountyRep = 15;
	/** The Hymn-Tuned Drive schematic's roll on a bounty, and its guarantee (11.2, 11.4). */
	public float driveSchematicChance = 0.02f;
	public int driveSchematicPity = 25;
	/** The fence's two services (7.1): the cipher (once a day) and the hymnstone shard (at Trusted, once a week); addition to spec 16.4. */
	public int cipherCredits = 50;
	public int shardCredits = 400;
}
