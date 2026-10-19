package com.oldrepublic.travel;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.travel.TravelService.Refusal;

/**
 * The rules of free flight as pure functions (spec MILESTONE_5.md 4.2, 4.4), so the server tests
 * check them without a world: who may take the helm, and the numbers a ship flies by.
 */
public final class FlightRules {
	/**
	 * Whether the owner may take the helm (4.2), in order: an owner with a ship, the config switch,
	 * the helm unlocked, nothing already flying it, a repaired ship at rest ({@code READY},
	 * {@code GROUNDED} or {@code DISABLED}: sublight flight needs no fuel and no hull minimum),
	 * then the M4 combat lock.
	 *
	 * @param record the owner's ship, or null when they have none
	 */
	public static Refusal helmRefusal(@Nullable ShipRecord record, boolean enabled, boolean unlocked, boolean busy, boolean inCombat) {
		if (record == null) {
			return Refusal.NOT_OWNER;
		}

		if (!enabled) {
			return Refusal.DISABLED_BY_CONFIG;
		}

		if (!unlocked) {
			return Refusal.NO_HELM;
		}

		if (busy || record.state().flying() || record.flight().isPresent()) {
			return Refusal.BUSY;
		}

		if (!ShipRules.boardable(record)) {
			return Refusal.NOT_READY;
		}

		return inCombat ? Refusal.COMBAT : Refusal.NONE;
	}

	/**
	 * The numbers a ship flies by (4.4): its class's speeds for a sky or an orbit, {@code THRUSTERS}'
	 * extra top speed, and under fallback B ({@code serverSteering}) every speed, thrust, brake and
	 * climb scaled by {@code serverSteeringSpeedPct}.
	 */
	public static FlightModel.Profile profile(FlightBalance b, int upgrades, boolean orbit) {
		boolean interceptor = (upgrades & ShipRecord.CLASS_INTERCEPTOR) != 0;
		float top = interceptor ? (orbit ? b.interceptorOrbit : b.interceptorSky) : (orbit ? b.courierOrbit : b.courierSky);
		float thrust = interceptor ? b.interceptorThrust : b.courierThrust;
		float brake = interceptor ? b.interceptorBrake : b.courierBrake;
		float climb = interceptor ? b.interceptorClimb : b.courierClimb;
		float turn = interceptor ? b.interceptorTurn : b.courierTurn;

		if ((upgrades & ShipRecord.UPGRADE_THRUSTERS) != 0) {
			top *= 1f + b.thrustersSpeedPct / 100f;
		}

		float scale = b.serverSteering ? b.serverSteeringSpeedPct / 100f : 1f;
		return new FlightModel.Profile(top * scale, thrust * scale, brake * scale, climb * scale, turn, orbit ? b.decayOrbit : b.decaySky,
				b.reverseSpeed * scale, b.strafePct / 100f, b.pitchLimit, b.boostMultiplier, b.maxSpeedCap);
	}

	/** How many ticks a full boost meter lasts: {@code THRUSTERS} makes it longer (4.4). */
	public static int boostTicks(FlightBalance b, int upgrades) {
		float ticks = b.boostTicks * ((upgrades & ShipRecord.UPGRADE_THRUSTERS) != 0 ? 1f + b.thrustersBoostPct / 100f : 1f);
		return Math.max(1, Math.round(ticks));
	}

	private FlightRules() {
	}
}
