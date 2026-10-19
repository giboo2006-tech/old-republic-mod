package com.oldrepublic.travel;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * The courier shuttle (spec MILESTONE_4.md 4.1, MILESTONE_5.md 4.9): the ship every owner starts
 * with. Everything a ship's body does lives in {@link ShipBody}; the courier adds its seats.
 */
public class CourierShuttle extends ShipBody {
	/**
	 * Seats in ship space (right, up, forward), in blocks from the body's position: the pilot in the
	 * cockpit, then the passenger bench behind it and a jump seat.
	 */
	private static final double[][] SEATS = {
			{0.0, 1.05, 3.1},
			{-0.85, 0.95, 1.3},
			{0.85, 0.95, 1.3},
			{0.0, 0.95, -0.6}
	};

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
}
