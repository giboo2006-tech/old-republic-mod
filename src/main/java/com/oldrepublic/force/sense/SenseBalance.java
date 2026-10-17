package com.oldrepublic.force.sense;

/**
 * Balance section {@code force_sense} of {@code config/oldrepublic/balance.json} (spec 10.1 and 14).
 * Durations in ticks, distances in blocks.
 */
public final class SenseBalance {
	/** Force Energy per cast, before mastery and the cost multipliers. */
	public float cost = 15f;
	public int cooldownTicks = 240;
	/** How long the caster sees the revealed points. */
	public int durationTicks = 160;
	/** Creatures within this distance are revealed (plus {@link #masteryRangePerRank} per mastery rank). */
	public int creatureRange = 24;
	/** Hidden blocks (veiled stone, cairns, anchors, urns) within this distance are revealed. */
	public int blockRange = 16;
	/** At most this many points per cast, nearest first. */
	public int maxPoints = 32;
	/** How far the holocron search reaches. */
	public int holocronRadius = 256;
	/** The holocron search with Seeker's Sight ({@code sense_seeker}). */
	public int seekerRadius = 512;
	/** Seeker's Sight adds this to the reveal. */
	public int seekerBonusTicks = 80;
	/** Seeker's Sight: pedestals and site caches glint within this distance (W1's provider). */
	public int glintRange = 24;
	/** Danger Sense: tells within this distance behind the player prickle. */
	public int dangerRange = 12;
	/** Danger Sense: tells inside this front cone (full angle) are seen, so they do not prickle. */
	public int dangerConeDegrees = 100;
	/** Danger Sense: at most one prickle per this many ticks. */
	public int dangerRepeatTicks = 20;
	/** Each mastery rank makes a cast this much cheaper. */
	public float masteryCostPerRank = 1f;
	/** Each mastery rank reaches this much further for creatures. */
	public int masteryRangePerRank = 4;
	/** Whether Sense reveals other players (who then feel watched). */
	public boolean pvpEffects = false;
}
