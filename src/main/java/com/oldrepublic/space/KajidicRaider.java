package com.oldrepublic.space;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * The Vessba kajidic's squat, patched heavy fighter, flown by its cartel and by freelance pirates
 * (spec MILESTONE_5.md 5.5): slow and tough, in groups of two or three. It circles level with its
 * target and strafes from the side, and every {@code space.raider.missileInterval} ticks it locks on
 * and fires a missile (the pilot hears the lock first).
 */
public class KajidicRaider extends Starfighter {
	/** Circles 34 blocks out at its target's height, so its runs come from the side; breaks at 18 blocks. */
	private static final Flight FLIGHT = new Flight(34, 2, 40, 80, 18, 5f, false);

	public KajidicRaider(EntityType<? extends KajidicRaider> type, Level level) {
		super(type, level);
	}

	@Override
	public SpaceRules.Kind kind() {
		return SpaceRules.Kind.RAIDER;
	}

	@Override
	protected ShipBolt.Style boltStyle() {
		return ShipBolt.Style.RAIDER;
	}

	@Override
	protected Flight flight() {
		return FLIGHT;
	}
}
