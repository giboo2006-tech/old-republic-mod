package com.oldrepublic.world;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.resources.Identifier;

/**
 * Effect event ids of the world feature. The server sends them through
 * {@link com.oldrepublic.network.EffectEvents}; the client handlers live in
 * {@code client.world.WorldEffectHandlers}.
 */
public final class WorldEffects {
	/** Unstable cluster starts its tell. pos = cluster centre, dir = facing, intensity = charge seconds. */
	public static final Identifier CRYSTAL_UNSTABLE_CHARGE = OldRepublicMod.id("crystal_unstable_charge");
	/** Unstable cluster discharges. pos = cluster centre, intensity = radius. */
	public static final Identifier CRYSTAL_DISCHARGE = OldRepublicMod.id("crystal_discharge");
	/** Resonant seal opens. pos = the clicked seal block centre, intensity = number of blocks. */
	public static final Identifier SEAL_OPEN = OldRepublicMod.id("seal_open");
	/** Resonant seal (or meditation stone) refuses a player. pos = block centre. */
	public static final Identifier SEAL_REFUSE = OldRepublicMod.id("seal_refuse");
	/** Trial starts. pos = meditation stone top, intensity = chamber radius, entityId = the player. */
	public static final Identifier TRIAL_BEGIN = OldRepublicMod.id("trial_begin");
	/**
	 * The echo materialises. variant 0 = wisps gather at pos (intensity = seconds until it forms),
	 * variant 1 = formed (entityId = echo).
	 */
	public static final Identifier TRIAL_ECHO_FORM = OldRepublicMod.id("trial_echo_form");
	/** Lunge tell (variant 0, intensity = windup seconds) and dash (variant 1). dir = lunge direction. */
	public static final Identifier TRIAL_ECHO_LUNGE = OldRepublicMod.id("trial_echo_lunge");
	/** Mirrored push: variant 0 = charge tell (intensity = windup seconds), 1 = release. dir = push direction. */
	public static final Identifier TRIAL_ECHO_PUSH = OldRepublicMod.id("trial_echo_push");
	/** The echo dissolves. variant 0 = defeated, 1 = faded (trial failed). */
	public static final Identifier TRIAL_ECHO_DISSOLVE = OldRepublicMod.id("trial_echo_dissolve");
	/** Trial ends. pos = stone top. variant 0 = completed, 1 = failed. */
	public static final Identifier TRIAL_COMPLETE = OldRepublicMod.id("trial_complete");
	/** The attuned crystal forms on the stone. pos = crystal, intensity unused, variant = crystal RGB. */
	public static final Identifier CRYSTAL_FORM = OldRepublicMod.id("crystal_form");
	/** Personal cave flag. variant 1 = the player is in the cave's underground pieces, 0 = not. */
	public static final Identifier CAVE_AMBIENCE = OldRepublicMod.id("cave_ambience");

	public static final int DISSOLVE_DEFEATED = 0;
	public static final int DISSOLVE_FADED = 1;
	public static final int COMPLETE_SUCCESS = 0;
	public static final int COMPLETE_FAILED = 1;

	private WorldEffects() {
	}
}
