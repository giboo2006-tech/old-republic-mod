package com.oldrepublic.saber;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.resources.Identifier;

/**
 * Effect event ids of the saber feature (see {@code docs/design/saber.md} sections 6.2 and M2). For
 * the Milestone 1 events the variant is the blade colour index; the Milestone 2 events use the
 * variant for their phase or step and take the colour from the entity's held saber. Clients build
 * sparks, sounds and poses from these.
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

	/** A light combo step landed. Entity: attacker. Variant: step 1 to 3 (3 = finisher). */
	public static final Identifier COMBO = OldRepublicMod.id("saber_combo");
	/**
	 * Heavy attack. Entity: attacker. Variant {@link #HEAVY_WINDUP} (intensity = wind-up ticks),
	 * {@link #HEAVY_STRIKE} (pos = centre of the arc) or {@link #HEAVY_CANCEL}.
	 */
	public static final Identifier HEAVY = OldRepublicMod.id("saber_heavy");
	public static final int HEAVY_WINDUP = 0;
	public static final int HEAVY_STRIKE = 1;
	public static final int HEAVY_CANCEL = 2;
	/** Lunge. Entity: attacker. Variant {@link #LUNGE_START}, {@link #LUNGE_HIT} (pos = hit) or {@link #LUNGE_MISS}. */
	public static final Identifier LUNGE = OldRepublicMod.id("saber_lunge");
	public static final int LUNGE_START = 0;
	public static final int LUNGE_HIT = 1;
	public static final int LUNGE_MISS = 2;
	/** Form change began. Entity: the player. Variant: {@link SaberForm} ordinal. Intensity: change ticks. */
	public static final Identifier FORM = OldRepublicMod.id("saber_form");
	/** Saber Throw. Entity: thrower. Variant {@link #THROW_START} (pos = hand) or {@link #THROW_SCORCH} (pos = block hit). */
	public static final Identifier THROW = OldRepublicMod.id("saber_throw");
	public static final int THROW_START = 0;
	public static final int THROW_SCORCH = 1;
	/** The thrown saber came back to the hand. Entity: thrower. */
	public static final Identifier THROW_CATCH = OldRepublicMod.id("saber_throw_catch");
	/** A saber NPC's guard broke. Entity: the NPC. */
	public static final Identifier NPC_GUARD_BREAK = OldRepublicMod.id("npc_guard_break");
	/**
	 * A saber NPC's attack tell. Entity: the NPC. Variant {@link #WINDUP_FIRST} (sound and glint) or
	 * {@link #WINDUP_NEXT} (glint only). Intensity: wind-up ticks. Never reduced by presets.
	 */
	public static final Identifier NPC_WINDUP = OldRepublicMod.id("npc_windup");
	public static final int WINDUP_FIRST = 0;
	public static final int WINDUP_NEXT = 1;
	/** Sparring. Entity: the partner. Variant {@link #SPAR_BEGIN}, {@link #SPAR_WON}, {@link #SPAR_LOST} or {@link #SPAR_OVER}. */
	public static final Identifier SPAR = OldRepublicMod.id("spar");
	public static final int SPAR_BEGIN = 0;
	public static final int SPAR_WON = 1;
	public static final int SPAR_LOST = 2;
	/** Interrupted or ended without a result: only the closing sound. */
	public static final int SPAR_OVER = 3;

	/**
	 * A guarding saber NPC sent a blaster bolt back (spec 10.7). Entity: the NPC. Pos: where the blade
	 * met the bolt. Dir: the bolt's new direction. Variant: the blade colour index.
	 */
	public static final Identifier NPC_DEFLECT = OldRepublicMod.id("npc_deflect");

	/**
	 * The saberstaff (spec MILESTONE_4.md 5.6, 13.3), toTracking. Entity: the wielder. Variant
	 * {@link #STAFF_SECOND_BLADE} (intensity 1 ignites the second blade, 0 retracts it) or
	 * {@link #STAFF_WHIRL} (pos = the wielder, intensity = the whirl's radius).
	 */
	public static final Identifier SABER_STAFF = OldRepublicMod.id("saber_staff");
	public static final int STAFF_SECOND_BLADE = 0;
	public static final int STAFF_WHIRL = 1;
	/** A lens went into the Lightsaber Workbench's lens slot (5.7), toPlayer. Variant: the lens ordinal. */
	public static final Identifier LENS_FIT = OldRepublicMod.id("lens_fit");

	private SaberEffects() {
	}
}
