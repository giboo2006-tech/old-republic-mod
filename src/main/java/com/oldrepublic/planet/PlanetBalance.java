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

	// Milestone 5 (spec MILESTONE_5.md 16.4): the ring radii of the new planet sites, which Phase 0's
	// PlanetSites reads; W3 adds the section's other keys.
	/** Ring radii of the verse stones around the holdfast; the ring point is on the middle one (3.3.2). */
	public int[] verseRings = {250, 350, 450};
	/** Ring radii of the Hymnal around the holdfast (3.3.3). */
	public int[] hymnalRings = {600, 750, 900};
	/** Ring radii of the debtor camp around Saltgate (3.2). */
	public int[] debtorCampRings = {200, 300, 400};
	/** Ring radii of the detention post around the Proving (3.4). */
	public int[] detentionRings = {120, 160, 200};
}
