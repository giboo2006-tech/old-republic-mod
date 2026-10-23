package com.oldrepublic.force.stasis;

/**
 * Balance section {@code force_stasis} of {@code config/oldrepublic/balance.json} (MILESTONE_5.md 9.3 and 16.4).
 * Durations in ticks, distances in blocks.
 */
public final class StasisBalance {
	/** Force Energy of a cast, and the cooldown. */
	public float cost = 30f;
	public int cooldownTicks = 400;
	/** Reach of the crosshair ray, and the half-angle of the aim-assist cone when the ray finds no one (the M2 Pull aim). */
	public float range = 16f;
	public float aimAssistHalfAngle = 8f;
	/** How long the target is held. */
	public int durationTicks = 60;
	/** One hit of at least this much damage ends the stasis. */
	public float breakDamage = 8f;
	/** The stasis ends this long after the first hit that counts. */
	public int breakDelayTicks = 20;
	/** Stasis Field ({@code stasis_field}): every hostile within this radius of the target, the target too, is held this long. */
	public float fieldRadius = 4f;
	public int fieldTicks = 40;
	/** Stasis Lock ({@code stasis_lock}): one target held this long, and its first hits do not count. */
	public int lockTicks = 100;
	public int lockIgnoredHits = 2;
	/** Duration multipliers: a boss ({@code #c:bosses}, multiplied with the boss's own) and {@code #oldrepublic:force_resistant}. */
	public float bossMultiplier = 0.25f;
	public float resistantMultiplier = 0.5f;
	/** Mastery: extra ticks held at mastery ranks 1, 2 and 3 (one point per target held). */
	public int[] masteryTicks = {5, 10, 20};
	/** Whether Stasis may hold other players (server PvP must be on as well; through the M3 held state). */
	public boolean pvpEffects = false;
}
