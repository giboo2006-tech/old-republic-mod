package com.oldrepublic.boss.block;

import java.util.Locale;

import net.minecraft.util.StringRepresentable;

/**
 * The crystal on top of a resonance pylon (spec 8.3, block state {@code crystal}). The template places
 * {@link #DORMANT}; the fight drives the rest. Serialised lowercase.
 */
public enum PylonCrystal implements StringRepresentable {
	/** Before phase 2, and after a reset. */
	DORMANT,
	/** Feeding the Resonance Ward. */
	SINGING,
	/** Cracked dark by the players. */
	SILENCED,
	/** Growing back during the re-sing tell. */
	REGROWING,
	/** Ripped out by Pull, or shattered into him at the second transition. */
	EMPTY;

	@Override
	public String getSerializedName() {
		return name().toLowerCase(Locale.ROOT);
	}
}
