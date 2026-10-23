package com.oldrepublic.equipment;

import com.oldrepublic.combat.DamageModifiers;
import com.oldrepublic.equipment.blaster.Blasters;
import com.oldrepublic.equipment.jet.JetPack;
import com.oldrepublic.equipment.melee.MeleeCombat;
import com.oldrepublic.force.ControlEffects;
import com.oldrepublic.progression.ForceModifiers;

/**
 * Server and common initialisation for the equipment feature: the era armour sets and their traits
 * (spec section 9), and the Milestone 4 gear (MILESTONE_4.md 5 and 6): blasters, vibroweapons, the
 * electrostaff, the four new sets, the mods' effects and the jet pack. Called from OldRepublicMod.
 * Reads no balance values: items use the registration defaults, everything else reads the live
 * sections when it runs.
 */
public final class EquipmentFeature {
	/** Registers the items, particles, trait modifiers, set bonuses, gear handlers, the equipment listener and the commands. */
	public static void init() {
		EquipmentItems.init();
		GearItems.init();
		EquipmentParticles.init();

		EquipmentModifiers modifiers = new EquipmentModifiers();
		ForceModifiers.register(modifiers);
		DamageModifiers.register(modifiers);
		ControlEffects.registerDurationModifier(modifiers);

		SetBonuses.init();
		EquipmentEvents.init();
		Blasters.init();
		MeleeCombat.init();
		JetPack.init();
		EquipmentCommands.init();
	}

	private EquipmentFeature() {
	}
}
