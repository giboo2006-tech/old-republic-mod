package com.oldrepublic.travel;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * The courier shuttle (spec MILESTONE_4.md 4.1, MILESTONE_5.md 4.9): the ship every owner starts
 * with, 7 × 4 × 7. Everything a ship's body does lives in {@link ShipBody}; the courier adds its
 * seats and where its engines, guns and ramp are.
 */
public class CourierShuttle extends ShipBody {
	/** The landing box (MILESTONE_4.md 4.5): the 7 × 4 × 7 hull, a little smaller. */
	static final double FOOTPRINT_HALF = 3.3;
	static final double FOOTPRINT_HEIGHT = 3.9;
	/** Where riders step off (right, forward): beside the ramp, then the sides and the nose. */
	static final double[][] DISMOUNT_SPOTS = {
			{0, -6.5}, {-1.6, -6.5}, {1.6, -6.5}, {0, -8}, {-5.5, -2}, {5.5, -2}, {-5.5, 2}, {5.5, 2}, {0, 7.5}
	};

	/**
	 * Seats in ship space (right, up, forward), in blocks from the body's position: the pilot in the
	 * cockpit, then the passenger bench behind it (its left seat is the gunner's, MILESTONE_5.md 5.2)
	 * and a jump seat.
	 */
	private static final double[][] SEATS = {
			{0.0, 1.05, 3.1},
			{-0.85, 0.95, 1.3},
			{0.85, 0.95, 1.3},
			{0.0, 0.95, -0.6}
	};
	/** The two nacelles' mouths. */
	private static final double[][] NOZZLES = {{2.625, 1.875, -5.6}, {-2.625, 1.875, -5.6}};
	/** The cannons beside the nose, the dorsal turret on the spine and the chin rack (MILESTONE_5.md 5.2, 5.3): the model hangs its parts here. */
	private static final double[][] CANNONS = {{1.7, 1.1, 4.2}, {-1.7, 1.1, 4.2}};
	private static final double[] TURRET = {0, 3.375, 0.6};
	private static final double[] RACK = {0, 0.625, 4.5};
	/** The foot of the tail ramp. */
	private static final double[] HATCH = {0, 0.5, -4.5};

	public CourierShuttle(EntityType<? extends CourierShuttle> type, Level level) {
		super(type, level);
	}

	@Override
	protected double[][] seats() {
		return SEATS;
	}

	/** The courier is not an interceptor (MILESTONE_5.md 4.9). */
	@Override
	public boolean interceptor() {
		return false;
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
