package com.oldrepublic.enemy.stormback;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.resources.Identifier;

/**
 * The stormback's effect event (spec MILESTONE_5.md 15.3), to everyone tracking it, entity = the
 * stormback. Variants 1 and 3 are attack tells in {@code DangerTellTable} (never thinned).
 */
public final class StormbackEffects {
	/** {@code stormback}. */
	public static final Identifier EVENT = OldRepublicMod.id("stormback");
	/** Charged by lightning: its plates crackle (intensity = the charge's ticks; the loop follows the synced state). */
	public static final int CHARGED = 0;
	/** The charge tell: head lowered, grit pawed up (intensity = the tell's ticks, dir = the coming charge). A tell. */
	public static final int CHARGE_TELL = 1;
	/** The charge lands on a body or a wall (pos = the impact). */
	public static final int IMPACT = 2;
	/** The roar: it rears and calls a bolt onto itself (intensity = the tell's ticks). A tell. */
	public static final int ROAR_TELL = 3;
	/** The charge is spent: the crackle fades. */
	public static final int SPENT = 4;

	private StormbackEffects() {
	}
}
