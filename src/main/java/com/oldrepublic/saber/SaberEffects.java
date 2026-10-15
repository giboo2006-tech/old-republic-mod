package com.oldrepublic.saber;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.resources.Identifier;

/**
 * Effect event ids of the saber feature (see {@code docs/design/saber.md} section 6.2). The
 * variant is always the blade colour index; clients build sparks and sounds from these.
 */
public final class SaberEffects {
	/** Melee hit absorbed by the guard. Intensity: stamina drained. */
	public static final Identifier CLASH = OldRepublicMod.id("saber_clash");
	/** Bolt knocked away. Dir: the bolt's new direction. */
	public static final Identifier DEFLECT = OldRepublicMod.id("saber_deflect");
	/** Perfectly timed block of a melee hit or a bolt. */
	public static final Identifier PARRY = OldRepublicMod.id("saber_parry");
	/** Guard stamina ran out. */
	public static final Identifier GUARD_BREAK = OldRepublicMod.id("saber_guard_break");
	/** An ignited blade struck a target. Intensity: damage dealt. */
	public static final Identifier HIT = OldRepublicMod.id("saber_hit");
	/** A saber was constructed at a workbench. Pos: the bench top. */
	public static final Identifier WORKBENCH_ASSEMBLE = OldRepublicMod.id("workbench_assemble");

	private SaberEffects() {
	}
}
