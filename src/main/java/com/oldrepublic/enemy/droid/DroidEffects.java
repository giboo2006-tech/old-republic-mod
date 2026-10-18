package com.oldrepublic.enemy.droid;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.resources.Identifier;

/**
 * The security droid's effect event (spec MILESTONE_4.md 13.3), to everyone tracking the droid,
 * entity = the droid. Variant 0 is an attack tell in {@code DangerTellTable}.
 */
public final class DroidEffects {
	/** {@code droid_tell}. */
	public static final Identifier TELL = OldRepublicMod.id("droid_tell");
	/** The barrels spin up before a volley; intensity = the tell's ticks. */
	public static final int SPIN = 0;
	/** A bolt struck the frontal shield; position = the strike (intensity 1), or the shield came up (intensity = its ticks, at the chest). */
	public static final int SHIELD = 1;
	/** Dying: sparks and a crackle; intensity = ticks to the burst. A second event with intensity 0 is the burst itself. */
	public static final int DEATH = 2;

	private DroidEffects() {
	}
}
