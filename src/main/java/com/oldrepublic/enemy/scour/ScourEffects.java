package com.oldrepublic.enemy.scour;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.resources.Identifier;

/**
 * The sand scour's effect event (spec MILESTONE_5.md 15.3), to everyone tracking the scour, entity =
 * the scour. Variants 1 and 4 are attack tells in {@code DangerTellTable} (never thinned).
 */
public final class ScourEffects {
	/** {@code scour}. */
	public static final Identifier EVENT = OldRepublicMod.id("scour");
	/** A faint ripple over the buried scour; sent every few seconds while it lies burrowed (the hiss loop follows the synced state). */
	public static final int RIPPLE = 0;
	/** The sand boils over it; intensity = the tell's ticks. A tell. */
	public static final int BURST_TELL = 1;
	/** It erupts in a spray of sand. */
	public static final int BURST = 2;
	/** It sinks and the sand slumps in over it. */
	public static final int BURROW = 3;
	/** The matriarch's tail rises and coils; intensity = the tell's ticks, dir = her facing. A tell. */
	public static final int SWEEP_TELL = 4;
	/** The matriarch calls her brood up out of the sand. */
	public static final int CALL = 5;
	/** The matriarch's sweep lands (dir = her facing); the impact dust. Not in the 15.3 list: an aftermath variant. */
	public static final int SWEEP = 6;

	private ScourEffects() {
	}
}
