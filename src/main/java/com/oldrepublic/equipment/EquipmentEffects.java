package com.oldrepublic.equipment;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.resources.Identifier;

/**
 * Effect event ids of the equipment feature (spec sections 13.3 and 16). Sent through
 * {@code EffectEvents}; the client builds the particles and plays the sound.
 */
public final class EquipmentEffects {
	/**
	 * A set was completed: {@code toPlayer}, pos and entity the player, intensity 1, variant the
	 * {@link ArmourSet} ordinal. The client plays {@code armour.set_bonus}, a short flourish and the
	 * set-bonus toast.
	 */
	public static final Identifier ARMOUR_SET = OldRepublicMod.id("armour_set");

	private EquipmentEffects() {
	}
}
