package com.oldrepublic.enemy.hymn;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.resources.Identifier;

/**
 * The Hymnsworn's effect event (spec MILESTONE_5.md 15.3). Variant 1 is an attack tell in
 * {@code DangerTellTable} (never thinned).
 */
public final class HymnEffects {
	/** {@code hymnsworn}. */
	public static final Identifier EVENT = OldRepublicMod.id("hymnsworn");
	/** A chanter's chant pulse (toTracking; entity = the chanter): breath fogs and nearby grit trembles. */
	public static final int CHANT = 0;
	/**
	 * A storm-call tell (around; pos = the marked patch, intensity = the tell's ticks, entity = the
	 * caller): static crawls over an uneven patch and grit lifts from it. A tell.
	 */
	public static final int CALL_TELL = 1;
	/** The storm-call strike (around; pos = the patch): a scorch and settling grit under vanilla's bolt. */
	public static final int CALL_STRIKE = 2;
	/**
	 * A pulse of a storm-caller's burst of Force lightning (toTracking; entity = the caller, pos = the
	 * struck target's chest, dir = from the caller toward it, intensity = the burst's ticks). Sent with
	 * each damage pulse, so the client draws the arc where it lands.
	 */
	public static final int LIGHTNING = 3;

	private HymnEffects() {
	}
}
