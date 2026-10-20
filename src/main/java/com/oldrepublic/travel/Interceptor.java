package com.oldrepublic.travel;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * The interceptor (spec MILESTONE_5.md 4.9): the shipwright's refit of the courier, 5 × 2.5 × 5. A
 * narrow wedge with a bubble canopy over the pilot, two wing-root cannons and an aft gunner's
 * turret: one seat for the pilot and one for the gunner, a small hold and a lighter hull (its
 * numbers are the record's class, {@link ShipRules}). Everything else a ship's body does lives in
 * {@link ShipBody}.
 */
public class Interceptor extends ShipBody {
	/** The landing box: the 5 × 2.5 × 5 hull, a little smaller. */
	static final double FOOTPRINT_HALF = 2.3;
	static final double FOOTPRINT_HEIGHT = 2.4;
	/** Where riders step off (right, forward): behind the canopy's step, then the sides and the nose. */
	static final double[][] DISMOUNT_SPOTS = {
			{0, -4.2}, {-1.4, -4.2}, {1.4, -4.2}, {0, -5.5}, {-3.8, -1}, {3.8, -1}, {-3.8, 1.5}, {3.8, 1.5}, {0, 4.8}
	};

	/** The pilot under the canopy, the gunner in the aft turret's ring. */
	private static final double[][] SEATS = {
			{0.0, 0.85, 0.6},
			{0.0, 1.15, -1.3}
	};
	/** The twin engines at the tail. */
	private static final double[][] NOZZLES = {{0.85, 0.9, -2.8}, {-0.85, 0.9, -2.8}};
	/** The wing-root cannons, the turret over the gunner and the rack under the nose: the model hangs its parts here. */
	private static final double[][] CANNONS = {{1.65, 0.7, 1.4}, {-1.65, 0.7, 1.4}};
	private static final double[] TURRET = {0, 1.75, -1.3};
	private static final double[] RACK = {0, 0.375, 2.1};
	/** The canopy over the pilot, where riders climb in and out. */
	private static final double[] HATCH = {0, 1.6, 0.6};

	public Interceptor(EntityType<? extends Interceptor> type, Level level) {
		super(type, level);
	}

	@Override
	protected double[][] seats() {
		return SEATS;
	}

	@Override
	public boolean interceptor() {
		return true;
	}

	@Override
	public double[][] nozzles() {
		return NOZZLES;
	}

	@Override
	public double[][] cannons() {
		return CANNONS;
	}

	@Override
	public double[] turret() {
		return TURRET;
	}

	@Override
	public double[] rack() {
		return RACK;
	}

	@Override
	public double[] hatch() {
		return HATCH;
	}
}
