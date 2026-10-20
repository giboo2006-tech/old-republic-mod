package com.oldrepublic.planet;

import com.oldrepublic.config.BalanceConfig;

/** Balance section {@code planets} (spec MILESTONE_4.md 14.3): hubs, planet sites, waystones and the planets' hazards. */
public final class PlanetBalance {
	public static PlanetBalance get() {
		return BalanceConfig.section("planets", PlanetBalance.class, PlanetBalance::new);
	}

	/** On a planet without a bed there, players respawn at the hub's medbay (12.2). */
	public boolean respawnAtHub = true;

	/** Ring radii searched from (0, 0) for a hub's surface (3.5). */
	public int[] hubRings = {0, 16, 32};
	/** Side of the square a hub needs as flat natural ground. */
	public int hubFootprint = 63;
	/** Allowed height difference across a hub's footprint. */
	public int hubMaxSlope = 6;
	/** Chunk radius of the temporary ticket around (0, 0) while a hub is placed (11 x 11 chunks). */
	public int hubTicketRadius = 5;

	/** Ring radii of the Heartstone around the Greenwatch (3.4). */
	public int[] heartstoneRing = {80, 100, 120};
	/** A per-player site is placed once its owner is this close to its ring point (3.4). */
	public int siteEnsureRange = 160;
	/** Ring radii from the hub of the crystal expeditions, the Wayfarer cache and the hunting camp. */
	public int[] expeditionRings = {300, 375, 450};
	public int[] cacheRings = {150, 200, 250};
	public int[] campRings = {400, 500, 600};
	/** Ring radii around the player of a hunter ambush's rocks (8.5: the hunters drop in from 24 to 32 blocks). */
	public int[] ambushRings = {24, 28, 32};
	/** An ambush is placed only this far from the hub and from every other site (8.5). */
	public int ambushClearance = 96;
	/** Placement attempts of one owner's site are at least this many ticks apart. */
	public int siteRetryTicks = 40;

	/** The share of worldgen waystones that are corrupted, seeded by position (3.4). */
	public double waystoneCorruptedChance = 0.25;
	/** Sense cleanses a corrupted waystone within this many blocks (8.4). */
	public double waystoneSenseRange = 8;
	/** A corrupted waystone's loop plays for players within this many blocks it is not cleansed for (13.4); its motes show within 8 (13.1). */
	public double waystoneAmbienceRange = 16;

	/** Scale of the client ashfall (1.0 = 40 flakes at Standard, 13.1). */
	public double ashfallDensity = 1.0;
	/** Contact damage of a duskglass cluster (8.4). */
	public float duskglassDamage = 2;
	/** A Wayfarer cache platform sinks this long after it is first stood on (8.4). */
	public int sinkingPlatformTicks = 60;
	/** A sunk platform rises again after this long. */
	public int platformResetTicks = 200;

	/** Discovery XP (W2 pays it through its hooks; listed here as the planet numbers of 14.3). */
	public int planetDiscoveryXp = 400;
	public int hubDiscoveryXp = 200;
	public int siteDiscoveryXp = 150;
	public int greaterTombDiscoveryXp = 300;

	/** The Singing Hollow: how long a humming cluster stays readable after Sense, and the chime order's length. */
	public int hollowHumTicks = 200;
	/** The rift crystal must be held this long while the whispers play (8.4). */
	public int riftBleedTicks = 100;
	/** The hub NPC restore check runs once per this many ticks (3.5: one check per in-game day). */
	public long hubRestoreTicks = 24000;

	// Milestone 5 (spec MILESTONE_5.md 16.4): Ghessar's and Tyrvann's weather, sites and resources.
	/** Ring radii of the verse stones around the holdfast; the ring point is on the middle one (3.3.2). */
	public int[] verseRings = {250, 350, 450};
	/** Ring radii of the Hymnal around the holdfast (3.3.3). */
	public int[] hymnalRings = {600, 750, 900};
	/** Ring radii of the debtor camp around Saltgate (3.2). */
	public int[] debtorCampRings = {200, 300, 400};
	/** Ring radii of the detention post around the Proving (3.4). */
	public int[] detentionRings = {120, 160, 200};

	/** Ghessar has sandstorms on its own cycle (3.5). */
	public boolean sandstorms = true;
	/** In-game days from the end of one sandstorm to the start of the next, inclusive range. */
	public int[] sandstormIntervalDays = {3, 5};
	/** Length of a sandstorm in ticks, inclusive range. */
	public int[] sandstormTicks = {3000, 6000};
	/** The client fog's far end in blocks at a sandstorm's full strength. */
	public double sandstormFogEnd = 24;
	/** Sand scours surface this many times as often in a sandstorm (W8 reads it, 12.1). */
	public double scourSandstormMultiplier = 2;

	/** Ridge lightning on Tyrvann's basalt (3.3). */
	public boolean ridgeLightning = true;
	/** Ticks between two strikes drawn by the same player, inclusive range. */
	public int[] ridgeIntervalTicks = {600, 1200};
	/** A player draws ridge lightning only above this height. */
	public int ridgeMinY = 110;
	/** Distance of a ridge strike from the player, inclusive range (never closer than the minimum). */
	public int[] ridgeDistance = {4, 10};
	/** A stormback this close to the player draws the strike to itself (12.1). */
	public double ridgeStormbackRange = 24;
	/** No ridge strike within this many blocks of the holdfast (its lightning rods). */
	public double ridgeHubExclusion = 48;

	/** The Sunstone Edge schematic's chance in a crashed freighter's hold, and its pity count (11.4; W7 reads them). */
	public double freighterEdgeChance = 0.03;
	public int freighterEdgePity = 30;
	/** Hymnsworn guarding the first, second and third verse stone (6.6). */
	public int[] verseGuards = {3, 4, 5};

	/** Ticks between cosmetic bolts on the Hymnal's arch, inclusive range (3.3.3). */
	public int[] hymnalArchInterval = {400, 800};
	/** Chance that a real lightning strike on exposed basalt grows a stormglass cluster (3.3). */
	public double stormglassChance = 0.5;

	/** Ticks of the channel that silences a verse stone (3.3.2). */
	public int silenceTicks = 100;
	/** Ticks a Force power cast at the stone cuts from the channel, once per channel. */
	public int silenceForceCut = 40;
	/** Players within this many blocks feel a guarded stone's slowing hum. */
	public double humSlowRange = 8;
	/** Ticks between two pulses of the slowing hum. */
	public int humSlowInterval = 60;
	/** The Hymnal's halls sit with their top this many blocks below the arch's base (3.3.3). */
	public int hymnalDepth = 18;
}
