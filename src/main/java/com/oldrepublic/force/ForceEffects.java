package com.oldrepublic.force;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Effect event ids of the force feature and the packing of their {@code variant} field. See
 * {@code docs/design/force.md} section 3 for what each field carries.
 */
public final class ForceEffects {
	/** Charge started ({@link #CHARGE_START}) or ended without a cast ({@link #CHARGE_STOP}). */
	public static final Identifier PUSH_CHARGE = OldRepublicMod.id("force_push_charge");
	/** Push released. Intensity = charge 0..1, variant = {@link #packRelease}. */
	public static final Identifier PUSH_RELEASE = OldRepublicMod.id("force_push");
	/** One target hit. Intensity = impulse, variant = hit index in the cast or {@link #IMPACT_PROJECTILE}. */
	public static final Identifier PUSH_IMPACT = OldRepublicMod.id("force_push_impact");
	/** Focused Blast target slammed into a wall. Direction = wall normal, intensity = damage. */
	public static final Identifier PUSH_SLAM = OldRepublicMod.id("force_push_slam");
	/** The player has just learned Force Push. */
	public static final Identifier PUSH_LEARNED = OldRepublicMod.id("force_push_learned");

	public static final int CHARGE_START = 0;
	public static final int CHARGE_STOP = 1;
	/** Impact variant for a projectile that was turned: small visual, no impact sound. */
	public static final int IMPACT_PROJECTILE = 1000;
	/** Impacts with an index below this play the impact sound (spec: max 3 per cast). */
	public static final int IMPACT_SOUND_LIMIT = 3;

	/** Packs branch, range (quarter blocks) and half-angle (degrees) into one varint. */
	public static int packRelease(PushBranch branch, PushShape shape) {
		int range = Mth.clamp(Math.round(shape.range() * 4f), 0, 1023);
		int angle = Mth.clamp(Math.round(shape.halfAngle()), 0, 180);
		return branch.ordinal() | range << 2 | angle << 12;
	}

	public static PushBranch releaseBranch(int variant) {
		return PushBranch.byOrdinal(variant & 3);
	}

	public static float releaseRange(int variant) {
		return (variant >> 2 & 1023) / 4f;
	}

	public static float releaseHalfAngle(int variant) {
		return variant >> 12 & 255;
	}

	private ForceEffects() {
	}
}
