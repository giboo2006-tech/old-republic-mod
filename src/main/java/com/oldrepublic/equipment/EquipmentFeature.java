package com.oldrepublic.equipment;

import com.oldrepublic.combat.DamageModifiers;
import com.oldrepublic.force.ControlEffects;
import com.oldrepublic.progression.ForceModifiers;

/**
 * Server and common initialisation for the equipment feature: the four era armour sets and their
 * traits (spec section 9). Called from OldRepublicMod. Reads no balance values: items use the
 * {@code new EquipmentBalance()} defaults, everything else reads the live section when it runs.
 */
public final class EquipmentFeature {
	/** Registers the 18 armour items, the trait modifiers, the set bonuses, the equipment listener and the command. */
	public static void init() {
		EquipmentItems.init();

		EquipmentModifiers modifiers = new EquipmentModifiers();
		ForceModifiers.register(modifiers);
		DamageModifiers.register(modifiers);
		ControlEffects.registerDurationModifier(modifiers);

		SetBonuses.init();
		EquipmentEvents.init();
		EquipmentCommands.init();
	}

	private EquipmentFeature() {
	}
}
