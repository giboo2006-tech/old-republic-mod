package com.oldrepublic.boss.drops;

import com.oldrepublic.config.BalanceConfig;

/**
 * Balance section {@code boss_drops} of {@code config/oldrepublic/balance.json} (spec MILESTONE_7.md 7.3, 7.4): the
 * chances of the bosses' set parts and what the elites add to their loot. A pity of N makes the N-th occasion in a row
 * without the drop a sure one (the Collector's emitter rule, {@link BossDropRules#due}). Count ranges are {min, max}.
 * The chests' and the poacher captain's parts are in their loot tables, not here.
 */
public final class BossDropsBalance {
	// ------------------------------------------------ 7.3: the bosses
	/** The Collector's daily echo: the Reliquary grip, in starfall iron. */
	public float collectorGripChance = 0.25f;
	public int collectorGripPity = 4;
	/** Kaast's daily rematch: the Watchman grip or pommel the player lacks, in aurelite. */
	public float watchmanPartChance = 0.25f;
	public int watchmanPartPity = 3;
	/** Ulvaine's daily echo and a helper's daily roll (one pity between them): the Penitent grip or pommel, in clan iron. */
	public float penitentPartChance = 0.25f;
	public int penitentPartPity = 3;
	/** The Long Hymn's weekly clear: a Choir grip or pommel the player lacks, in hymnsteel; with all three, ingots. */
	public float choirPartChance = 0.30f;
	public int choirPartPity = 2;
	public int choirIngots = 2;

	// ------------------------------------------------ 7.4: the elites, on a player's kill
	/** Clan Hask's champion: clan iron, on top of every warrior's 10% piece. */
	public int[] championClanIron = {1, 2};
	/** The cartel captain: sunstone, and the Knurled grip on a chance. */
	public int[] captainSunstone = {1, 3};
	public float captainKnurledChance = 0.15f;
	/** The Hymnsworn caller: a hymnstone shard and the Domed pommel, each on its own chance. */
	public float callerHymnstoneChance = 0.15f;
	public float callerDomedChance = 0.10f;
	/** The sand scour matriarch: duskglass shards. */
	public int[] matriarchDuskglass = {2, 4};
	/** The hollowhorn bull: aurelite shards. */
	public int[] bullAurelite = {2, 4};
	/** A trooper sergeant or officer (off patrol): the Vanguard emitter. */
	public float trooperVanguardChance = 0.08f;

	public static BossDropsBalance get() {
		return BalanceConfig.section("boss_drops", BossDropsBalance.class, BossDropsBalance::new);
	}
}
