package com.oldrepublic.travel;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.network.EffectEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * The ship's effect events (spec MILESTONE_4.md 13.3, MILESTONE_5.md 15.3). Every look and sound is
 * played by the client handlers in {@code client.travel.TravelFx}, {@code FlightFx} and
 * {@code HyperspaceOverlay}; the server only says what happened.
 */
public final class TravelEffects {
	/** toTracking, entity: variant {@link #ENGINE_IDLE} to {@link #ENGINE_SETTLE}. */
	public static final Identifier SHIP_ENGINE = OldRepublicMod.id("ship_engine");
	public static final int ENGINE_IDLE = 0;
	public static final int ENGINE_SPOOL = 1;
	public static final int ENGINE_CLIMB = 2;
	public static final int ENGINE_DESCENT = 3;
	public static final int ENGINE_SETTLE = 4;

	/** toTracking, entity: 0 jump out, 1 arrive. */
	public static final Identifier SHIP_JUMP = OldRepublicMod.id("ship_jump");
	public static final int JUMP_OUT = 0;
	public static final int JUMP_IN = 1;

	/** toPlayer: the overlay; intensity = progress (0 starts it, 1 ends it). */
	public static final Identifier HYPERSPACE = OldRepublicMod.id("hyperspace");

	/**
	 * around: variant = socket (0 hull, 1 core, 2 motivator, 3 nav), 4 plate weld, 5 stand up; beyond the
	 * spec's 0–5, 6 a cell going into the tank and 7 an upgrade fitted or taken out.
	 */
	public static final Identifier SHIP_REPAIR = OldRepublicMod.id("ship_repair");
	public static final int REPAIR_WELD = 4;
	public static final int REPAIR_STAND_UP = 5;
	public static final int REPAIR_FUEL = 6;
	public static final int REPAIR_UPGRADE = 7;

	/** toTracking, entity: intensity = 1 − hull / the class's most hull (a fresh hit of wear, sabotage or a crash). */
	public static final Identifier SHIP_DAMAGE = OldRepublicMod.id("ship_damage");

	/** around: 0 set, 1 refuse, 2 recall. */
	public static final Identifier LANDING_BEACON = OldRepublicMod.id("landing_beacon");
	public static final int BEACON_SET = 0;
	public static final int BEACON_REFUSE = 1;
	public static final int BEACON_RECALL = 2;

	/** toTracking, entity = the ship (MILESTONE_5.md 15.3): variant {@link #FLIGHT_HELM} to {@link #FLIGHT_LAND_REFUSED}. */
	public static final Identifier SHIP_FLIGHT = OldRepublicMod.id("ship_flight");
	public static final int FLIGHT_HELM = 0;
	public static final int FLIGHT_BOOST = 1;
	/** intensity = the thruster keys newly held ({@link #THRUST_LEFT} to {@link #THRUST_DOWN}). */
	public static final int FLIGHT_THRUSTER = 2;
	/** pos = the struck point; intensity = the speed lost (blocks per tick). */
	public static final int FLIGHT_IMPACT = 3;
	public static final int FLIGHT_AUTOPILOT = 4;
	public static final int FLIGHT_EDGE = 5;
	public static final int FLIGHT_LAND_REFUSED = 6;
	/** The thruster keys of {@link #FLIGHT_THRUSTER}: strafe left and right, climb, descend. */
	public static final int THRUST_LEFT = 1;
	public static final int THRUST_RIGHT = 2;
	public static final int THRUST_UP = 4;
	public static final int THRUST_DOWN = 8;

	/**
	 * toPlayer, the pilot and every rider (4.7): {@link #ATMOSPHERE_UP} into an orbit, {@link #ATMOSPHERE_DOWN}
	 * into a sky; intensity {@link #ATMOSPHERE_START} as the ship reaches the line, {@link #ATMOSPHERE_END} once
	 * it is across (or turned back short of it).
	 */
	public static final Identifier ATMOSPHERE = OldRepublicMod.id("atmosphere");
	public static final int ATMOSPHERE_UP = 0;
	public static final int ATMOSPHERE_DOWN = 1;
	public static final float ATMOSPHERE_START = 0f;
	public static final float ATMOSPHERE_END = 1f;

	/** around the ship (4.8, 4.9): 0 refitted to the interceptor, 1 back to the courier, 2 an upgrade fitted. */
	public static final Identifier SHIP_REFIT = OldRepublicMod.id("ship_refit");
	public static final int REFIT_INTERCEPTOR = 0;
	public static final int REFIT_COURIER = 1;
	public static final int REFIT_UPGRADE = 2;

	/** toTracking, entity = the ship (5.4): 0 disabled (the tumble and the pilot's alarm start), 1 the rescue brings it home. */
	public static final Identifier SHIP_DISABLED = OldRepublicMod.id("ship_disabled");
	public static final int DISABLED_TUMBLE = 0;
	public static final int DISABLED_RESCUED = 1;

	static void engine(ShipBody ship, int variant) {
		EffectEvents.toTracking(ship, SHIP_ENGINE, ship.position(), null, ship.getId(), 1f, variant);
	}

	static void jump(ShipBody ship, int variant) {
		EffectEvents.toTracking(ship, SHIP_JUMP, ship.position(), ship.getLookAngle(), ship.getId(), 1f, variant);
	}

	static void hyperspace(ServerPlayer rider, float progress) {
		EffectEvents.toPlayer(rider, HYPERSPACE, rider.position(), null, -1, progress, 0);
	}

	static void repair(ServerLevel level, Vec3 at, @Nullable Entity ship, int variant) {
		EffectEvents.around(level, SHIP_REPAIR, at, null, ship == null ? -1 : ship.getId(), 1f, variant);
	}

	static void damage(ShipBody ship, int hull) {
		EffectEvents.toTracking(ship, SHIP_DAMAGE, ship.position(), null, ship.getId(), 1f - hull / (float) Math.max(1, ship.maxHull()), 0);
	}

	/** {@code ship_flight} at the ship's centre. */
	static void flight(ShipBody ship, int variant, float intensity) {
		flight(ship, variant, ship.getBoundingBox().getCenter(), intensity);
	}

	/** {@code ship_flight} at {@code at} (a crash's struck point). */
	static void flight(ShipBody ship, int variant, Vec3 at, float intensity) {
		EffectEvents.toTracking(ship, SHIP_FLIGHT, at, ship.getLookAngle(), ship.getId(), intensity, variant);
	}

	/** {@code atmosphere} to one of the ship's riders: its start or its end ({@link #ATMOSPHERE_START}, {@link #ATMOSPHERE_END}). */
	static void atmosphere(ServerPlayer rider, ShipBody ship, int variant, float stage) {
		EffectEvents.toPlayer(rider, ATMOSPHERE, ship.position(), null, ship.getId(), stage, variant);
	}

	/** {@code ship_refit} around the ship. */
	static void refit(ShipBody ship, int variant) {
		EffectEvents.around((ServerLevel) ship.level(), SHIP_REFIT, ship.getBoundingBox().getCenter(), null, ship.getId(), 1f, variant);
	}

	/** {@code ship_disabled} to everyone who sees the ship. */
	static void disabled(ShipBody ship, int variant) {
		EffectEvents.toTracking(ship, SHIP_DISABLED, ship.getBoundingBox().getCenter(), null, ship.getId(), 1f, variant);
	}

	static void beacon(ServerLevel level, BlockPos pos, int variant) {
		EffectEvents.around(level, LANDING_BEACON, Vec3.atCenterOf(pos), null, -1, 1f, variant);
	}

	private TravelEffects() {
	}
}
