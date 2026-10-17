package com.oldrepublic.identity;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.resources.Identifier;

/**
 * Effect event ids owned by the identity feature, and the bit layout of their variants. See
 * {@code docs/design/identity.md} for what each looks and sounds like.
 */
public final class IdentityEffects {
	/**
	 * To the owner. intensity = amount applied; variant = new band ordinal, plus
	 * {@link #FLAG_BAND_CHANGED} when the band changed and {@link #FLAG_MINOR} for a contextual
	 * (minor or severe) shift.
	 */
	public static final Identifier ALIGNMENT_SHIFT = OldRepublicMod.id("alignment_shift");
	/**
	 * To the owner. intensity = amount applied; variant = faction ordinal, plus
	 * {@link #FLAG_TIER_CHANGED} when the tier changed.
	 */
	public static final Identifier REPUTATION_CHANGE = OldRepublicMod.id("reputation_change");
	/** To trackers and self. entity = player; intensity = new rank; variant = path ordinal. */
	public static final Identifier RANK_UP = OldRepublicMod.id("rank_up");
	/** To trackers of the yielder. entity = yielder; pos = its feet. */
	public static final Identifier YIELD = OldRepublicMod.id("yield");
	/** Around a settler cut loose. entity = settler; pos = its wrists; variant 0 freed, 1 made to pay. */
	public static final Identifier SETTLER_FREED = OldRepublicMod.id("settler_freed");

	public static final int FLAG_BAND_CHANGED = 8;
	public static final int FLAG_MINOR = 16;
	public static final int FLAG_TIER_CHANGED = 8;
	/** Low bits of a variant that hold the band or faction ordinal. */
	public static final int ORDINAL_MASK = 7;

	private IdentityEffects() {
	}
}
