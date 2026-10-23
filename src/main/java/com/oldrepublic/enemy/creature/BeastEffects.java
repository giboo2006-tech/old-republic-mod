package com.oldrepublic.enemy.creature;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.resources.Identifier;

/**
 * The beasts' effect event (spec MILESTONE_4.md 13.3), to everyone tracking the beast, entity = the
 * beast. Variants 0 and 1 are attack tells in {@code DangerTellTable}; the rest are their follow-ups.
 */
public final class BeastEffects {
	/** {@code beast_tell}. */
	public static final Identifier TELL = OldRepublicMod.id("beast_tell");
	/** The hollowhorn lowers its head, snorts and scrapes a hoof; intensity = the tell's ticks. */
	public static final int HOLLOWHORN_CHARGE = 0;
	/** The ash hound crouches and growls; intensity = the tell's ticks. */
	public static final int HOUND_LUNGE = 1;
	/** The ash hound's howl that brings the pack. */
	public static final int HOUND_HOWL = 2;
	/** The charge starts: the pounding run (dir = the charge). */
	public static final int HOLLOWHORN_RUN = 3;
	/** The charge ends against a wall or a body: the impact. */
	public static final int HOLLOWHORN_IMPACT = 4;
	/** The hound leaps (dir = the leap). */
	public static final int HOUND_LEAP = 5;

	private BeastEffects() {
	}
}
