package com.oldrepublic.world;

import com.oldrepublic.world.trial.TrialManager;

/**
 * Server and common initialisation for the world feature: the crystal cave blocks and structure
 * support (including the camp's terrain-adapting pool element), cave tracking for the Calling, the unstable cluster hazard and the Trial of
 * Reflection. Called from OldRepublicMod.
 */
public final class WorldFeature {
	public static void init() {
		WorldBlocks.init();
		WorldParticles.init();
		WorldEntities.init();
		CampPoolElement.init();
		CaveLocator.init();
		CaveTracker.init();
		TrialManager.init();
		WorldCommands.init();
	}

	private WorldFeature() {
	}
}
