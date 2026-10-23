package com.oldrepublic.saber.forge;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.resources.Identifier;

/**
 * Effect event ids of the Forge (MILESTONE_7.md 5.2), sent around the bench with {@code EffectEvents};
 * the client's handlers are {@code ForgeClient}'s.
 */
public final class ForgeEffects {
	/**
	 * A part was forged or re-forged. Pos: the bench top. Entity: the player. Variant: the material's
	 * ordinal. Intensity: the bench socket the part went into, or -1 when it went to the inventory.
	 */
	public static final Identifier FORGE = OldRepublicMod.id("workbench_forge");
	/** A design was studied. Pos: the bench top. Entity: the player. Variant: the bench socket studied. */
	public static final Identifier STUDY = OldRepublicMod.id("workbench_study");

	private ForgeEffects() {
	}
}
