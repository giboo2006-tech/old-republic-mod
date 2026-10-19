package com.oldrepublic.force.mindtrick;

/**
 * Balance section {@code force_mind_trick} of {@code config/oldrepublic/balance.json} (MILESTONE_5.md 9.8 and 16.4).
 * Durations in ticks, distances in blocks.
 */
public final class MindTrickBalance {
	/** Force Energy of a cast (or a dialogue use), and the cooldown. */
	public float cost = 25f;
	public int cooldownTicks = 600;
	/** Reach of the crosshair ray, and the half-angle of the aim-assist cone when the ray finds no one. */
	public float range = 8f;
	public float aimAssistHalfAngle = 8f;
	/** How long the target is pacified. */
	public int durationTicks = 120;
	/** Confusion ({@code mind_trick_confuse}): how long the target turns on its own side, and how far it looks for one of them. */
	public int confuseTicks = 80;
	public float confuseSearchRadius = 12f;
	/** Many Minds ({@code mind_trick_mass}): up to this many targets in all, within this radius of the first, for this long. */
	public int massTargets = 3;
	public float massRadius = 6f;
	public int massTicks = 80;
	/** Mastery: extra ticks per mastery rank (one point per successful trick; dialogue uses count). */
	public int masteryTicksPerRank = 20;
	/** Whether Mind Trick may affect other players (server PvP must be on as well); no player is mind-trickable, so it changes nothing. */
	public boolean pvpEffects = false;
}
