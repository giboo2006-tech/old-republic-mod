package com.oldrepublic.travel;

import com.oldrepublic.config.BalanceConfig;

/**
 * Balance section {@code flight} (spec MILESTONE_5.md 4.4, 16.4): free flight, the helm, the boost,
 * landing, the sky and orbit bands, the M5 upgrades and the refit. Server side; the numbers a ship
 * flies by reach the pilot's client in its {@link FlightModel.Profile}. Rules take instances, so
 * tests can pass {@code new FlightBalance()} without touching the config file.
 */
public final class FlightBalance {
	public static FlightBalance get() {
		return BalanceConfig.section("flight", FlightBalance.class, FlightBalance::new);
	}

	/** False: nobody takes the helm (the M4 autopilot still flies pad to pad). */
	public boolean enabled = true;
	/** Fallback B (4.1): the server steers from the pilot's input, every speed scaled by {@link #serverSteeringSpeedPct}. */
	public boolean serverSteering = false;
	public int serverSteeringSpeedPct = 67;

	/** No ship goes faster (blocks per tick); the server resyncs a move longer than this × 1.1. */
	public float maxSpeedCap = 2.0f;
	/** The nose follows the pilot's look up and down within this many degrees. */
	public float pitchLimit = 60f;
	/** Top reverse speed, and strafe as a share of thrust and top speed (percent). */
	public float reverseSpeed = 0.2f;
	public int strafePct = 40;

	/** The courier: top speed in a sky and in orbit, thrust, brake, climb (blocks per tick) and turn rate (degrees per tick). */
	public float courierSky = 1.0f;
	public float courierOrbit = 1.4f;
	public float courierThrust = 0.04f;
	public float courierBrake = 0.08f;
	public float courierClimb = 0.4f;
	public float courierTurn = 6f;

	/** The interceptor, the same numbers. */
	public float interceptorSky = 1.2f;
	public float interceptorOrbit = 1.45f;
	public float interceptorThrust = 0.07f;
	public float interceptorBrake = 0.12f;
	public float interceptorClimb = 0.5f;
	public float interceptorTurn = 9f;

	/** Speed kept each tick with no input, in a sky and in orbit. */
	public float decaySky = 0.97f;
	public float decayOrbit = 0.99f;

	/** The boost: top speed and thrust multiplier, ticks a full meter lasts, ticks an empty one takes to refill. */
	public float boostMultiplier = 1.25f;
	public int boostTicks = 60;
	public int boostRechargeTicks = 160;
	/** {@code THRUSTERS} (4.8): extra top speed and extra boost length, percent. */
	public int thrustersSpeedPct = 10;
	public int thrustersBoostPct = 25;

	/** Crashes (4.4): every {@link #impactStep} of measured speed above {@link #impactFloor} costs {@link #impactHull} hull. */
	public float impactFloor = 0.6f;
	public float impactStep = 0.25f;
	public int impactHull = 2;

	/** Taking the helm (4.2): the Spool, then a rise of {@link #helmRiseBlocks} blocks over {@link #helmRiseTicks} ticks. */
	public int helmSpoolTicks = 40;
	public int helmRiseTicks = 20;
	public int helmRiseBlocks = 4;

	/** Landing anywhere (4.6): at most this high and this fast; the ground level within this; a hub's bays within this range. */
	public int landHeight = 6;
	public float landMaxSpeed = 0.25f;
	public int landLevelTolerance = 1;
	public int hubBayRange = 88;

	/** Sky and orbit (4.7): the heights of the bands, the scale, the arrival heights and the cooldown between transitions. */
	public int orbitCeiling = 300;
	public int atmosphereLine = 64;
	public int orbitScale = 8;
	public int orbitArrivalY = 100;
	public int skyArrivalY = 290;
	public int jumpArrivalY = 160;
	public int transitionCooldown = 60;
	/** The orbit's radius from (0, 0) and the warning band inside its edge. */
	public int orbitRadius = 2048;
	public int edgeWarning = 128;

	/** The M5 upgrades' prices (4.8). */
	public int cannonsPrice = 800;
	public int shieldPrice = 1000;
	public int thrustersPrice = 900;
	public int missilesPrice = 1200;

	/** The refit (4.9): to the interceptor, its alloy, back to the courier; the interceptor's hold and hull. */
	public int refitCredits = 2500;
	public int refitAlloy = 6;
	public int refitBackCredits = 500;
	public int interceptorHold = 9;
	public int interceptorHull = 80;

	/** Legendary ship parts (11.2): the shipwright's fitting fee; the Hymn-Tuned Drive's hyperspace length multiplier. */
	public int legendaryFitCredits = 500;
	public float hymnDriveHyperspaceMult = 0.5f;

	/** The disabled ship (5.4): the hull the free rescue leaves, and how long it tumbles first. */
	public int rescueHull = 20;
	public int tumbleTicks = 40;
}
