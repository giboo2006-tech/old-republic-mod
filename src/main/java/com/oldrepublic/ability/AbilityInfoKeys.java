package com.oldrepublic.ability;

import java.util.Map;

/**
 * Common keys in {@link ForceAbility#info()} maps that the HUD and the Force screen read for every
 * power. Units are fixed so all powers read alike: seconds for durations (keys end in {@code _s}),
 * blocks for distances, full angles in degrees, percentages as 0-100. Ability-specific keys (listed
 * per node in the tree JSON {@code info} arrays) follow the same rules.
 *
 * <p>An ability puts in whichever of the cost keys applies: {@link #COST} for a fixed cost,
 * {@link #COST_MIN}/{@link #COST_MAX} for a charged power, {@link #START_COST} plus
 * {@link #COST_PER_S} for a channel.
 */
public final class AbilityInfoKeys {
	/** Fixed Force Energy cost of a tap power. */
	public static final String COST = "cost";
	/** Force Energy of the cheapest (minimum charge) cast of a hold-and-release power. */
	public static final String COST_MIN = "cost_min";
	/** Force Energy of the fully charged cast. */
	public static final String COST_MAX = "cost_max";
	/** Force Energy to start a channel. */
	public static final String START_COST = "start_cost";
	/** Force Energy per second while channelling. */
	public static final String COST_PER_S = "cost_per_s";
	/** Cooldown in seconds. */
	public static final String COOLDOWN = "cooldown_s";
	/** Hold-and-release: seconds of hold before a release fires. */
	public static final String MIN_CHARGE = "min_charge_s";
	/** Hold-and-release: seconds of hold for a full charge (drives the crosshair brackets). */
	public static final String FULL_CHARGE = "full_charge_s";
	/** Channel: wind-up seconds before the channel takes effect. */
	public static final String CHARGE = "charge_s";
	/** Channel: the longest channel in seconds (drives the HUD channel arc). */
	public static final String MAX_CHANNEL = "max_channel_s";
	/** Reach in blocks. */
	public static final String RANGE = "range";

	/** The Force Energy needed to start the power, or 0 if the ability reports no cost. */
	public static float startCost(Map<String, Float> info) {
		for (String key : new String[] {COST, COST_MIN, START_COST}) {
			Float value = info.get(key);

			if (value != null) {
				return value;
			}
		}

		return 0f;
	}

	private AbilityInfoKeys() {
	}
}
