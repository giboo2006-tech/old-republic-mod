package com.oldrepublic.equipment.blaster;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.resources.Identifier;

/**
 * Effect event ids and variants of the Milestone 7 blasters (spec MILESTONE_7.md 9.1 and 11): the
 * scatter blaster's blast and the bolts of the carbine's burst. Sent through {@code EffectEvents};
 * the client ({@code client.equipment.BlasterM7Client}) plays the sound, builds the flash and kicks
 * the shooter's gun. The hit and deflect markers, the overheat and the vents stay the Milestone 4
 * events of {@code GearEffects}, which every kind shares.
 */
public final class BlasterEffects {
	/**
	 * A scatter blaster or a carbine fired: {@code toTracking}, pos the muzzle, dir the look, entity the
	 * shooter, intensity the heat after the shot (0 to 1). Variants {@link #SCATTER_BLAST},
	 * {@link #CARBINE_BURST}, {@link #CARBINE_BOLT}.
	 */
	public static final Identifier M7_FIRE = OldRepublicMod.id("blaster_m7_fire");
	/** The scatter's blast: all its pellets at once. */
	public static final int SCATTER_BLAST = 0;
	/** The first bolt of a carbine burst. */
	public static final int CARBINE_BURST = 1;
	/** A later bolt of a carbine burst. */
	public static final int CARBINE_BOLT = 2;

	private BlasterEffects() {
	}
}
