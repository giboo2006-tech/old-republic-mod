package com.oldrepublic.space;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * Clan Hask's forward-swept fighter in clan colours (spec MILESTONE_5.md 5.5): trios that hold
 * formation on their leader while circling and run in together. Hostile only to players whose clan
 * standing is Hostile, a scanning patrol on the kajidic's cargo run (5.9), or the player's allies
 * at the blockade when the clan stands with them (6.5).
 */
public class HaskFighter extends Starfighter {
	/** Circles 40 blocks out and 10 above; breaks at 22 blocks; wingmen hold formation. */
	private static final Flight FLIGHT = new Flight(40, 10, 40, 70, 22, 6.5f, true);

	public HaskFighter(EntityType<? extends HaskFighter> type, Level level) {
		super(type, level);
	}

	@Override
	public SpaceRules.Kind kind() {
		return SpaceRules.Kind.HASK_FIGHTER;
	}

	@Override
	protected ShipBolt.Style boltStyle() {
		return ShipBolt.Style.HASK;
	}

	@Override
	protected Flight flight() {
		return FLIGHT;
	}
}
