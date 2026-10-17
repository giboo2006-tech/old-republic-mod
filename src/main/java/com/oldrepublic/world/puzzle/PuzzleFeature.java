package com.oldrepublic.world.puzzle;

import com.oldrepublic.force.sense.SenseTargets;

/**
 * The puzzle kit of the M3 sites (spec 3.3): puzzle, resource and decor blocks, their block
 * entities, POI types, Force handlers and the Force Sense provider. Called from
 * {@code WorldFeature.init()} after the world particles.
 */
public final class PuzzleFeature {
	public static void init() {
		PuzzleBlocks.init();
		PuzzlePois.init();
		PuzzleHandlers.init();
		SenseTargets.register(new PuzzleSense());
	}

	private PuzzleFeature() {
	}
}
