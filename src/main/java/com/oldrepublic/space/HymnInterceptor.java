package com.oldrepublic.space;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * Ulvaine's blade-winged interceptor (spec MILESTONE_5.md 5.5): the fastest of the fighters, in
 * pairs, diving on its target from high above and breaking away at 24 blocks. Its violet bolts come
 * in bursts of three.
 */
public class HymnInterceptor extends Starfighter {
	/** Circles 44 blocks out and 22 above its target, so every run is a dive; breaks at 24 blocks. */
	private static final Flight FLIGHT = new Flight(44, 22, 30, 60, 24, 8f, false);

	public HymnInterceptor(EntityType<? extends HymnInterceptor> type, Level level) {
		super(type, level);
	}

	@Override
	public SpaceRules.Kind kind() {
		return SpaceRules.Kind.HYMN_INTERCEPTOR;
	}

	@Override
	protected ShipBolt.Style boltStyle() {
		return ShipBolt.Style.HYMN;
	}

	@Override
	protected Flight flight() {
		return FLIGHT;
	}
}
