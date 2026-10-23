package com.oldrepublic.force.choke;

/**
 * Balance section {@code force_choke} of {@code config/oldrepublic/balance.json} (spec
 * MILESTONE_4.md 9.3, 9.5 and 14). Durations in ticks, distances in blocks.
 */
public final class ChokeBalance {
	/** Force Energy to start the channel, and per second while it holds. */
	public float startCost = 25f;
	public float channelCost = 6f;
	/** The longest hold. */
	public int maxTicks = 60;
	/** Cooldown, counted from the release. */
	public int cooldownTicks = 360;
	/** Reach of the crosshair ray, and the cone around it (full angle) when the ray finds no one. */
	public float range = 12f;
	public float coneDegrees = 6f;
	/** How high the target is lifted. */
	public float liftHeight = 0.6f;
	/** Damage every {@code damageInterval} ticks of the hold. */
	public float damage = 2f;
	public int damageInterval = 10;
	/** The stagger when the target drops. */
	public int releaseStaggerTicks = 15;
	/** One hit of at least this much damage on the caster ends the channel. */
	public float interruptDamage = 6f;
	/** The hold a boss allows at most, whatever its own rule says. */
	public int bossMaxTicks = 20;
	/** Alignment shift for choking a non-hostile or yielded creature (minor, once per target per day). */
	public int nonHostileShift = -3;
	/** Crush ({@code choke_crush}): a hold of at least this long ends with extra damage. */
	public int crushMinTicks = 40;
	public float crushDamage = 6f;
	/** Dread ({@code choke_dread}): non-boss enemies this near the target are frightened this long. */
	public float dreadRadius = 6f;
	public int dreadTicks = 40;
	/** {@code cruel_focus}: Lightning on a target released from the caster's Choke within the window deals this much more. */
	public float cruelFocusPct = 20f;
	public int cruelFocusWindow = 40;
	/** Mastery: one point per target held at least this long. */
	public int masteryHoldTicks = 20;
	/** Each mastery rank: this much cheaper to start and this many blocks further. */
	public float masteryCostPerRank = 2f;
	public float masteryRangePerRank = 1f;
	/** Whether Choke may hold other players (server PvP must be on as well; through the M3 held state). */
	public boolean pvpEffects = false;
}
