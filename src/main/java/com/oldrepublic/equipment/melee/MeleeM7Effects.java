package com.oldrepublic.equipment.melee;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.resources.Identifier;

/**
 * The effect event of the Milestone 7 melee weapons (MILESTONE_7.md 9.1, 9.2 and 11). Sent through
 * {@code EffectEvents}; the client ({@code client.equipment.MeleeM7Client}) plays the sounds
 * ({@code vibro_axe.sunder}, {@code force_pike.charge}, {@code force_pike.stun}) and builds the
 * particles. The vibro-axe's swing whoosh ({@code vibro_axe.swing}) needs no event: the client hears
 * every swing of an axe from the vanilla swing state.
 */
public final class MeleeM7Effects {
	/** One id, told apart by its variant; {@code toTracking} unless a variant says otherwise. */
	public static final Identifier MELEE_M7 = OldRepublicMod.id("melee_m7");

	/** The pike's charge began: pos the wielder's eyes, entity the wielder. The buzz rises while it charges. */
	public static final int PIKE_CHARGE = 0;
	/** The pike's charge loaded: the head crackles while it stays live. */
	public static final int PIKE_CHARGED = 1;
	/**
	 * A stun thrust landed: pos the struck foe's chest, dir the thrust, entity the wielder; intensity 1
	 * for a stun, 0 for a droid (not stunned, struck harder).
	 */
	public static final int PIKE_STUN = 2;
	/** The pike's charge ran out or left the hand unspent: the crackle dies. */
	public static final int PIKE_SPENT = 3;
	/**
	 * A Sunder blow landed on a guard ({@code around}): pos the contact point, dir the swing, entity the
	 * guarding foe; intensity 1 when it broke the guard, 0 when it only drained it.
	 */
	public static final int SUNDER = 4;
	/** A Clan Iron Edge held a saber blow without a notch: pos the contact point, entity the guard's owner. */
	public static final int EDGE_HOLD = 5;

	private MeleeM7Effects() {
	}
}
