package com.oldrepublic.holocron;

import com.oldrepublic.progression.ForceModifiers;

/**
 * Server and common initialisation for the holocron feature (spec section 4). Called from
 * OldRepublicMod.
 */
public final class HolocronFeature {
	/** Registers the attachment, items, particles, the carry modifier, the dialogue hooks, events and commands. */
	public static void init() {
		HolocronAttachments.init();
		HolocronItems.init();
		HolocronParticles.init();
		ForceModifiers.register(HolocronCarry.INSTANCE);
		HolocronCarry.init();
		HolocronAttunement.init();
		HolocronArchive.init();
		HolocronDialogue.init();
		HolocronCommands.init();
	}

	private HolocronFeature() {
	}
}
