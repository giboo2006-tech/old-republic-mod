package com.oldrepublic.crafting;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.resources.Identifier;

/**
 * Effect event ids of the crafting feature (spec MILESTONE_4.md 13.3). Clients build the sparks,
 * mists and sounds from these; see {@code docs/design/crafting.md}.
 */
public final class CraftingEffects {
	/** A mod was fitted or removed at the bench (toPlayer). Variant {@link #MOD_FITTED} or {@link #MOD_REMOVE}. */
	public static final Identifier MOD_FIT = OldRepublicMod.id("mod_fit");
	public static final int MOD_FITTED = 0;
	public static final int MOD_REMOVE = 1;

	/**
	 * A bench craft (around the bench top). Variant {@link #CRAFT_START} (intensity = craft ticks,
	 * starts the weld loop), {@link #CRAFT_DONE}, {@link #CRAFT_MASTERWORK} or {@link #CRAFT_STOP}
	 * (cancelled: the loop stops). Entity: the crafter. {@link #TOOL_CUT} is the fusion cutter
	 * biting into sealed plating (pos: the cut; a spark spray, no loop). {@link #CRAFT_LEGENDARY}
	 * (MILESTONE_5.md 11.1, 15.3; the spec's "variant 3", which {@link #CRAFT_STOP} already holds) is
	 * a legendary craft: a slow rise of bright sparks and a warm glow; intensity 2 when the crafter's
	 * {@code legendary} banner follows (its client leaves the chime to the banner), else 1.
	 */
	public static final Identifier BENCH_CRAFT = OldRepublicMod.id("bench_craft");
	public static final int CRAFT_START = 0;
	public static final int CRAFT_DONE = 1;
	public static final int CRAFT_MASTERWORK = 2;
	public static final int CRAFT_STOP = 3;
	public static final int TOOL_CUT = 4;
	public static final int CRAFT_LEGENDARY = 5;

	/** An item was salvaged (around the bench top). */
	public static final Identifier SALVAGE = OldRepublicMod.id("salvage");

	/**
	 * A consumable was used (toTracking the user). Variant {@link #MEDPAC}, {@link #KOLTO},
	 * {@link #STIM} or {@link #CRASH}. Entity: the user.
	 */
	public static final Identifier CONSUMABLE = OldRepublicMod.id("consumable");
	public static final int MEDPAC = 0;
	public static final int KOLTO = 1;
	public static final int STIM = 2;
	public static final int CRASH = 3;

	/**
	 * A scanner pulse (toPlayer). Dir: the look direction; intensity: range in blocks; event data:
	 * ticks the marks last (variant). The client finds and marks the resources and creatures itself.
	 */
	public static final Identifier SCANNER_PULSE = OldRepublicMod.id("scanner_pulse");

	/** A player grenade (around). Variant {@link #GRENADE_THROW} or {@link #GRENADE_STUN} (intensity = radius). */
	public static final Identifier GRENADE_PLAYER = OldRepublicMod.id("grenade_player");
	public static final int GRENADE_THROW = 0;
	public static final int GRENADE_STUN = 1;

	private CraftingEffects() {
	}
}
