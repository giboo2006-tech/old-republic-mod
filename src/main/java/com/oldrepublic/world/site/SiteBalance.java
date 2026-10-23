package com.oldrepublic.world.site;

import com.oldrepublic.config.BalanceConfig;

/**
 * Balance section {@code sites} (spec section 14): puzzle timers, vents, cairns, spawners, outposts,
 * garrisons and the structure locator, and far placement's tuning in the nested {@link Far} object
 * {@code far} (docs/MILESTONE_6.md 3.2). Server side; rules take an instance so tests can pass the
 * defaults ({@code new SiteBalance()}) without touching the config file.
 */
public final class SiteBalance {
	public static SiteBalance get() {
		return BalanceConfig.section("sites", SiteBalance.class, SiteBalance::new);
	}

	/** How long Sense leaves veiled stone revealed. */
	public int veilRevealTicks = 160;
	/** Most veiled blocks one push or hit opens (the flood fill). */
	public int veilGroupMax = 27;

	/** Resettable puzzle blocks reset after this many ticks with no player within {@link #puzzleResetRange}. */
	public int puzzleResetTicks = 1200;
	public int puzzleResetRange = 24;
	/** Interval of the resettable blocks' own scheduled tick. */
	public int puzzleTickInterval = 200;

	/** How long a sunk counterweight stays down, and the warning before it rises. */
	public int counterweightTicks = 600;
	public int counterweightWarnTicks = 40;

	/** A pulled anchor strains this long, then stays released this long. */
	public int anchorStrainTicks = 10;
	public int anchorOpenTicks = 600;

	/** Ticks of Lift channel before collapsed rubble clears. */
	public int rubbleTicks = 20;

	/** Sense reaches an urn keeper within this range; the echoes play this far apart; the sequence can be heard again after this long. */
	public int urnSenseRange = 12;
	public int urnStepTicks = 20;
	public int urnRetryTicks = 100;
	/** A wrong push: dust damage, its radius, and the radius in which sentinels wake. */
	public float urnWrongDamage = 2f;
	public double urnWrongRadius = 3;
	public int sentinelWakeRadius = 16;

	/** Tomb vents: the random delay between puffs, the tell, and how near a player must be. */
	public int ventMinTicks = 60;
	public int ventMaxTicks = 100;
	public int ventTellTicks = 15;
	public int ventActiveRange = 8;
	public float ventDamage = 4f;
	public int ventSlownessTicks = 60;

	/** How long the owner's Sense leaves an echo cairn revealed. */
	public int cairnRevealTicks = 600;

	/** A site spawner spawns its group once a player is within this range. */
	public int spawnerRange = 32;

	/** A worldgen outpost within this radius is used; else a runtime one within the reuse radius (docs/MILESTONE_6.md 3.2). */
	public int outpostWorldgenRadius = 1024;
	public int outpostReuseRadius = 768;
	/** The distance band (blocks from the player, min and max) a new runtime outpost is placed in, on high ground, by far placement. */
	public int[] outpostRange = {400, 700};
	/** Rings (blocks from the player) of the near search a runtime outpost falls back to when far placement fails, its fallback rings, and the minimum gap to any other outpost. */
	public int[] outpostRings = {112, 128, 144};
	public int[] outpostFallback = {80, 96};
	public int outpostSeparation = 128;
	/** At most this many runtime outposts per world; with {@link #runtimeOutposts} off, only worldgen outposts exist. */
	public int maxRuntimeOutposts = 8;
	public boolean runtimeOutposts = true;

	/** Garrison upkeep at a bounty terminal. */
	public int garrisonRefillTicks = 12000;
	public int garrisonRefillClearRange = 48;
	public int garrisonLeash = 12;
	public int terminalChecksBeforeRespawn = 2;

	/** Structure locator: cache lifetime and the move that invalidates it, cells checked per server tick, ring cap. */
	public int locatorCacheTicks = 600;
	public int locatorCacheMove = 48;
	public int locatorCellsPerTick = 4;
	public int locatorMaxRings = 4;

	/** How often the site tracker looks up the piece each player stands in. */
	public int presenceCheckTicks = 20;

	/** Far placement's tuning (docs/MILESTONE_6.md 3.2), the nested object {@code far}. */
	public Far far = new Far();

	/** The {@code far} object: how far placement chooses, loads, places and lets go. */
	public static final class Far {
		/** Height samples the choosing requests may take in one server tick, all together. */
		public int samplesPerTick = 64;
		/** Candidates on each ring of a distance band, and rings across the band. */
		public int anglesPerRing = 24;
		public int ringsPerBand = 4;
		/** How far from the chosen target the precise placement looks (its clearing search's reach). */
		public int searchSlack = 24;
		/** The largest ticket, in chunks around the target. */
		public int maxTicketRadius = 7;
		/** Ticks the area stays loaded after placing; ticks a target may take to load before the request fails. */
		public int lingerTicks = 1200;
		public int loadTimeoutTicks = 2400;
		/** Blocks a target keeps from any other recorded site, other request and world spawn. */
		public int separation = 96;
		/** Chunks around a target where a worldgen structure that could start refuses it. */
		public int structureRange = 3;
	}
}
