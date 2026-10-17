package com.oldrepublic.enemy.construct;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.resources.Identifier;

/**
 * Effect event ids of the Ruin Sentinel (spec 13.3), all sent {@code toTracking} the sentinel with
 * its id as {@code entityId}. Drawn and voiced by {@code client.boss.sentinel.SentinelFx}.
 */
public final class SentinelEffects {
	/**
	 * Waking. Variant {@link #WAKE_RISE} (pos = lumen, intensity = tell ticks: the lumen ignites,
	 * stone grinds, dust sheds) or {@link #WAKE_BOW} (pos = head: a dormant Watcher lowers its head
	 * for a player of its side).
	 */
	public static final Identifier WAKE = OldRepublicMod.id("sentinel_wake");
	public static final int WAKE_RISE = 0;
	public static final int WAKE_BOW = 1;

	/**
	 * The slam. Variant {@link #SLAM_TELL} (a tell; intensity = tell ticks, dir = facing) or
	 * {@link #SLAM_IMPACT} (pos = centre of the arc, dir = facing, intensity = arc radius).
	 */
	public static final Identifier SLAM = OldRepublicMod.id("sentinel_slam");
	public static final int SLAM_TELL = 0;
	public static final int SLAM_IMPACT = 1;

	/** The core is exposed after a slam. Pos = lumen, intensity = exposed ticks. */
	public static final Identifier CORE = OldRepublicMod.id("sentinel_core");

	/**
	 * The lance. Variant {@link #LANCE_CHARGE} (a tell; pos = lumen, intensity = tell ticks) or
	 * {@link #LANCE_FIRE} (pos = muzzle, dir = the shot).
	 */
	public static final Identifier LANCE = OldRepublicMod.id("sentinel_lance");
	public static final int LANCE_CHARGE = 0;
	public static final int LANCE_FIRE = 1;

	/** The crumble on death (or a failed binding). Pos = lumen, dir = facing, variant = {@link SentinelVariant} ordinal. */
	public static final Identifier DEATH = OldRepublicMod.id("sentinel_death");

	private SentinelEffects() {
	}
}
