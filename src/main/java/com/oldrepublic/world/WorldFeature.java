package com.oldrepublic.world;

import com.oldrepublic.world.puzzle.PuzzleFeature;
import com.oldrepublic.world.site.SiteFeature;
import com.oldrepublic.world.trial.TrialManager;

/**
 * Server and common initialisation for the world feature: the crystal cave blocks and structure
 * support (including the camp's terrain-adapting pool element), cave tracking for the Calling, the unstable cluster hazard and the Trial of
 * Reflection; from M3 the puzzle kit ({@link PuzzleFeature}) and the sites, outposts and site
 * runtime ({@link SiteFeature}). Called from OldRepublicMod.
 */
public final class WorldFeature {
	public static void init() {
		WorldBlocks.init();
		WorldParticles.init();
		PuzzleFeature.init();
		SiteFeature.init();
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
