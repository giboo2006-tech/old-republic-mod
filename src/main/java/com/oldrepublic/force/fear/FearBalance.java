package com.oldrepublic.force.fear;

/**
 * Balance section {@code force_fear} of {@code config/oldrepublic/balance.json} (MILESTONE_5.md 9.2, 9.6 and 16.4).
 * Durations in ticks, distances in blocks. The flee numbers are the {@code fear} control's, whatever frightened the
 * creature (Force Fear, Choke's Dread, the saber's Dread Presence).
 */
public final class FearBalance {
	/** Force Energy of a cast, and the cooldown. */
	public float cost = 30f;
	public int cooldownTicks = 600;
	/** The cone: reach and full angle. */
	public float range = 10f;
	public float coneDegrees = 60f;
	/** How long every hostile in the cone is afraid. */
	public int durationTicks = 80;
	/** Terror ({@code fear_terror}): all around the caster within this radius, for this long. */
	public float terrorRadius = 8f;
	public int terrorTicks = 60;
	/** Nightmare ({@code fear_dread}): one target (the crosshair pick) for this long, taking this much more damage while afraid. */
	public int nightmareTicks = 160;
	public float nightmareDamagePct = 15f;
	public float aimAssistHalfAngle = 8f;
	/** The flee: how far a frightened creature looks for a spot (horizontal, vertical), and its speed multiplier. */
	public int fleeHorizontal = 16;
	public int fleeVertical = 7;
	public double fleeSpeed = 1.3;
	/** A frightened player or a creature without goals: slowness at this amplifier (1 = Slowness II). */
	public int slownessAmplifier = 1;
	/** Mastery: extra ticks per mastery rank (one point per enemy frightened). */
	public int masteryTicksPerRank = 10;
	/** Whether Fear may frighten other players (server PvP must be on as well). */
	public boolean pvpEffects = false;
}
