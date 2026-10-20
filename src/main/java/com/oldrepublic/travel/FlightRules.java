package com.oldrepublic.travel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.quest.story.ChapterFiveStage;
import com.oldrepublic.travel.TravelService.Refusal;

import net.minecraft.core.GlobalPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The rules of free flight as pure functions (spec MILESTONE_5.md 4.2–4.10, 5.4, 11.2), so the
 * server tests check them without a world: who may take the helm, the numbers a ship flies by, the
 * landing check, crashes, the bands of a sky and an orbit and the orbit's edge, the autopilot from
 * the air and jumps from orbit, the return to the last landing, the rescue, the Hymn-Tuned Drive,
 * and the shipwright's upgrades and refits.
 */
public final class FlightRules {
	/** The ship is pushed this far back inside the orbit's edge (4.7). */
	public static final double EDGE_PUSH_BACK = 16;
	/** How far an arrival's clear place is looked for (4.7): blocks straight up, then blocks sideways. */
	public static final int CLEAR_UP = 16;
	public static final int CLEAR_SIDE = 16;
	/** The sideways search's rings are this many blocks apart, with this many bearings each. */
	private static final int CLEAR_RING = 2;
	private static final int CLEAR_BEARINGS = 16;
	/** Below this (blocks per tick) a velocity component never counts as running into anything (4.4). */
	private static final double STILL = 0.05;

	/** Why a hand landing is refused, or that it may go ahead (4.6). Sent by ordinal in {@code flight_status}; append only. */
	public enum Landing {
		/** The spot below passes: the ship settles straight down. */
		OK,
		/** Inside a hub's footprint: the ship glides onto the first free bay (4.5). */
		HUB,
		TOO_HIGH,
		TOO_FAST,
		/** Water or lava under the footprint. */
		NO_GROUND,
		NOT_LEVEL,
		NO_ROOM,
		/** Inside another player's site. */
		SITE;

		public boolean allowed() {
			return this == OK || this == HUB;
		}

		/** Lang key of the HUD line ("Not level", "No room"...). */
		public String langKey() {
			return "hud.oldrepublic.flight.land." + name().toLowerCase(Locale.ROOT);
		}

		/** The value at {@code ordinal}, or {@link #TOO_HIGH} for one from a newer version. */
		public static Landing of(int ordinal) {
			Landing[] all = values();
			return ordinal >= 0 && ordinal < all.length ? all[ordinal] : TOO_HIGH;
		}
	}

	/**
	 * What the scan of the ground under a ship's footprint found (4.6).
	 *
	 * @param columns the columns scanned
	 * @param solid   the columns with solid ground within reach
	 * @param liquid  water or lava came first in some column
	 * @param lowest  the lowest ground top found
	 * @param highest the highest ground top found
	 */
	public record Ground(int columns, int solid, boolean liquid, double lowest, double highest) {
	}

	/** A sky or orbit transition due now (4.7). */
	public enum Transition {
		NONE,
		/** From a planet's sky up into its orbit. */
		UP,
		/** From an orbit down into its planet's sky. */
		DOWN
	}

	/** How the return to the last landing is paid (4.10): the free daily recall first, else a cell from the tank, else one carried. */
	public enum ReturnCost {
		FREE,
		TANK,
		CARRIED,
		NONE
	}

	/** Why the shipwright will not fit an upgrade (4.8, 11.2), or that he can. */
	public enum Fit {
		OK,
		NO_SHIP,
		UNKNOWN,
		ALREADY,
		/** The ship is not landed here (or flies, or is still the hulk). */
		NOT_HERE,
		/** The Spindle Capacitor feeds a shield generator. */
		NEEDS_SHIELD,
		/** The legendary part is not in the player's pack. */
		NO_PART,
		CREDITS;

		public String langKey() {
			return "ship.oldrepublic.fit." + name().toLowerCase(Locale.ROOT);
		}
	}

	/** Why the shipwright will not refit the ship (4.9), or that he can. */
	public enum Refit {
		OK,
		NO_SHIP,
		ALREADY,
		NOT_HERE,
		RIDERS,
		/** More stacks in the hold than the interceptor carries. */
		HOLD,
		CREDITS,
		ALLOY;

		public String langKey() {
			return "ship.oldrepublic.refit." + name().toLowerCase(Locale.ROOT);
		}
	}

	/** The M5 upgrades the Status tab lists and the shipwright fits, in that order (4.8, 11.2). */
	public static final int[] M5_UPGRADES = {ShipRecord.UPGRADE_CANNONS, ShipRecord.UPGRADE_SHIELD, ShipRecord.UPGRADE_THRUSTERS,
			ShipRecord.UPGRADE_MISSILES, ShipRecord.PART_SPINDLE_CAPACITOR, ShipRecord.PART_HYMN_DRIVE};

	// ---------------------------------------------------------------- the helm (4.2)

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
	 * Taking the helm from the autopilot during its Descent (4.5): the owner aboard, the helm
	 * unlocked and flight on; the landing is abandoned.
	 */
	public static Refusal takeOverRefusal(@Nullable ShipRecord record, boolean enabled, boolean unlocked, boolean descending) {
		if (record == null) {
			return Refusal.NOT_OWNER;
		}

		if (!enabled) {
			return Refusal.DISABLED_BY_CONFIG;
		}

		if (!unlocked) {
			return Refusal.NO_HELM;
		}

		return descending ? Refusal.NONE : Refusal.BUSY;
	}

	// ---------------------------------------------------------------- the flight model (4.4)

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

	/**
	 * The boost meter as the server keeps it (4.3): full at 1. A press of Sprint starts the boost
	 * unless the meter is empty, and letting go stops it; while the boost runs the meter drains over
	 * {@link #boostTicks} and the boost stops when it is empty; at rest it refills over
	 * {@code boostRechargeTicks}.
	 *
	 * @param meter   0 to 1
	 * @param held    Sprint is held
	 * @param running the boost runs
	 */
	public record Boost(float meter, boolean held, boolean running) {
		public static final Boost FULL = new Boost(1f, false, false);

		/** Sprint pressed ({@code pressed}) or let go. */
		public Boost press(boolean pressed) {
			return new Boost(meter, pressed, pressed && meter > 0f);
		}

		/** One server tick, the boost lasting {@code boostTicks} from full and the meter refilling over {@code rechargeTicks}. */
		public Boost tick(int boostTicks, int rechargeTicks) {
			if (running) {
				float left = Math.max(0f, meter - 1f / Math.max(1, boostTicks));
				return new Boost(left, held, held && left > 0f);
			}

			return new Boost(Math.min(1f, meter + 1f / Math.max(1, rechargeTicks)), held, false);
		}
	}

	/**
	 * The speed a crash took off (4.4): the part of the ship's measured velocity that ran into
	 * something, on each blocked axis; a vertical bump counts only while descending.
	 */
	public static double speedLost(Vec3 velocity, boolean blockedX, boolean blockedY, boolean blockedZ) {
		double x = blockedX && Math.abs(velocity.x) > STILL ? velocity.x : 0;
		double y = blockedY && velocity.y < -STILL ? velocity.y : 0;
		double z = blockedZ && Math.abs(velocity.z) > STILL ? velocity.z : 0;
		return Math.sqrt(x * x + y * y + z * z);
	}

	/**
	 * The hull a crash costs (4.4): {@code impactHull} for each {@code impactStep} (or part of one)
	 * by which the speed lost exceeds {@code impactFloor}; nothing at or below the floor.
	 */
	public static int impactDamage(double speedLost, FlightBalance b) {
		double over = speedLost - b.impactFloor;

		if (over <= 1.0e-9 || b.impactStep <= 0f) {
			return 0;
		}

		return Math.max(0, b.impactHull) * (int) Math.ceil(over / b.impactStep - 1.0e-9);
	}

	// ---------------------------------------------------------------- landing (4.6)

	/**
	 * The landing check of 4.6, in the order the HUD names a reason: within {@code landHeight} of the
	 * ground and slower than {@code landMaxSpeed}; then a spot inside a hub's footprint goes to a bay
	 * (4.5) whatever lies right below; elsewhere solid ground (no water or lava), level within
	 * {@code landLevelTolerance} across the footprint (every column found ground), room for the hull,
	 * and not inside another player's site.
	 *
	 * @param speed  the server's measured speed, blocks per tick
	 * @param bottom the ship's lowest point (its position's height)
	 */
	public static Landing landing(double speed, double bottom, Ground ground, boolean room, boolean ownedSite, boolean hub, FlightBalance b) {
		if (ground.solid() == 0 && !ground.liquid() || ground.solid() > 0 && bottom - ground.highest() > b.landHeight) {
			return Landing.TOO_HIGH;
		}

		if (speed > b.landMaxSpeed) {
			return Landing.TOO_FAST;
		}

		if (hub) {
			return Landing.HUB;
		}

		if (ground.liquid()) {
			return Landing.NO_GROUND;
		}

		if (ground.solid() < ground.columns() || ground.highest() - ground.lowest() > b.landLevelTolerance) {
			return Landing.NOT_LEVEL;
		}

		if (!room) {
			return Landing.NO_ROOM;
		}

		return ownedSite ? Landing.SITE : Landing.OK;
	}

	/** The record once landed by hand at {@code pos}: no flight, and the state as at the M4 Settle (4.6). */
	public static ShipRecord landedAt(ShipRecord r, GlobalPos pos, float yaw, ShipBalance b) {
		return ShipRules.recovered(r.withPos(pos, yaw), b);
	}

	// ---------------------------------------------------------------- sky and orbit (4.7)

	/**
	 * The transition due for a piloted ship at height {@code y} (4.7): up above {@code orbitCeiling}
	 * in a planet's sky, down below {@code atmosphereLine} in an orbit, neither within
	 * {@code transitionCooldown} ticks of the last one.
	 *
	 * @param sinceLast ticks since the last transition
	 */
	public static Transition transition(boolean sky, boolean orbit, double y, long sinceLast, FlightBalance b) {
		if (sinceLast < b.transitionCooldown) {
			return Transition.NONE;
		}

		if (sky && y > b.orbitCeiling) {
			return Transition.UP;
		}

		return orbit && y < b.atmosphereLine ? Transition.DOWN : Transition.NONE;
	}

	/** Ticks left before another transition may happen (the HUD's cue, 0 when none is pending). */
	public static int cooldownLeft(long sinceLast, FlightBalance b) {
		return (int) Math.max(0, Math.min(b.transitionCooldown, b.transitionCooldown - sinceLast));
	}

	/** Where a ship climbing out of a sky at {@code (x, z)} arrives in its orbit: {@code (x / 8, 100, z / 8)}, kept inside the edge's warning band. */
	public static Vec3 toOrbit(double x, double z, FlightBalance b) {
		double scale = Math.max(1, b.orbitScale);
		return clampInside(x / scale, b.orbitArrivalY, z / scale, arrivalRadius(b));
	}

	/** Where a ship diving out of an orbit at {@code (x, z)} arrives in its planet's sky: {@code (x × 8, 290, z × 8)}. */
	public static Vec3 toSky(double x, double z, FlightBalance b) {
		double scale = Math.max(1, b.orbitScale);
		return new Vec3(x * scale, b.skyArrivalY, z * scale);
	}

	/**
	 * Where a jump to an orbit arrives (4.7): above the destination's pad divided by 8, at
	 * {@code jumpArrivalY}; above (0, 0) when the planet has no pad yet. Kept inside the edge's band.
	 */
	public static Vec3 orbitArrival(@Nullable Vec3 pad, FlightBalance b) {
		if (pad == null) {
			return new Vec3(0, b.jumpArrivalY, 0);
		}

		double scale = Math.max(1, b.orbitScale);
		return clampInside(pad.x / scale, b.jumpArrivalY, pad.z / scale, arrivalRadius(b));
	}

	/** Arrivals stay this far from (0, 0): the orbit's radius less its warning band (1,920 by default). */
	public static double arrivalRadius(FlightBalance b) {
		return Math.max(0, b.orbitRadius - b.edgeWarning);
	}

	/** {@code (x, y, z)} pulled toward (0, 0) along its radius until it is at most {@code radius} out. */
	public static Vec3 clampInside(double x, double y, double z, double radius) {
		double r = Math.sqrt(x * x + z * z);

		if (r <= radius || r < 1.0e-9) {
			return new Vec3(x, y, z);
		}

		double k = radius / r;
		return new Vec3(x * k, y, z * k);
	}

	/**
	 * Where a ship arriving at {@code at} goes so that its hull meets no rock (4.7): {@code at} when
	 * the hull fits there ({@code fits}); else the nearest place that fits, straight up first (a block
	 * at a time, at most {@code up} blocks, never above {@code ceiling}), then sideways at
	 * {@code at}'s height (rings 2 blocks apart out to {@code side} blocks, 16 bearings each, east
	 * first), never farther than {@code radius} from (0, 0) ({@code radius} 0 or less: no limit).
	 * Empty when nothing there fits: the caller keeps {@code at} and logs it.
	 */
	public static Optional<Vec3> clearArrival(Vec3 at, Predicate<Vec3> fits, int up, int side, double ceiling, double radius) {
		if (fits.test(at)) {
			return Optional.of(at);
		}

		for (int dy = 1; dy <= up && at.y + dy <= ceiling; dy++) {
			Vec3 above = at.add(0, dy, 0);

			if (fits.test(above)) {
				return Optional.of(above);
			}
		}

		for (int r = CLEAR_RING; r <= side; r += CLEAR_RING) {
			for (int i = 0; i < CLEAR_BEARINGS; i++) {
				double angle = 2 * Math.PI * i / CLEAR_BEARINGS;
				Vec3 beside = new Vec3(at.x + r * Math.cos(angle), at.y, at.z + r * Math.sin(angle));

				if ((radius <= 0 || beside.x * beside.x + beside.z * beside.z <= radius * radius) && fits.test(beside)) {
					return Optional.of(beside);
				}
			}
		}

		return Optional.empty();
	}

	/** How far inside the orbit's edge {@code (x, z)} is, in blocks; negative beyond it. */
	public static double edgeDistance(double x, double z, FlightBalance b) {
		return b.orbitRadius - Math.sqrt(x * x + z * z);
	}

	/** Whether the HUD warns of the edge (within {@code edgeWarning} of it). */
	public static boolean nearEdge(double x, double z, FlightBalance b) {
		return edgeDistance(x, z, b) < b.edgeWarning;
	}

	/** Where the server puts a ship that reached the orbit's edge: {@link #EDGE_PUSH_BACK} blocks back inside it, at the same height. */
	public static Vec3 turnBack(double x, double y, double z, FlightBalance b) {
		double r = Math.sqrt(x * x + z * z);

		if (r < 1.0e-9) {
			return new Vec3(x, y, z);
		}

		double k = Math.max(0, b.orbitRadius - EDGE_PUSH_BACK) / r;
		return new Vec3(x * k, y, z * k);
	}

	/** The heading from {@code (x, z)} toward (0, 0) (the edge's turn back). */
	public static float yawToCentre(double x, double z) {
		return Ships.yawAlong(-x, -z);
	}

	// ---------------------------------------------------------------- the autopilot from the air and jumps (4.5, 4.7, 6.5)

	/** Whether Tyrvann is closed to {@code to} at this stage: the holdfast before {@code BLOCKADE} (6.1, 6.5). */
	public static boolean blockaded(Destination to, ChapterFiveStage stage) {
		return to == Destination.HOLDFAST && stage.ordinal() < ChapterFiveStage.BLOCKADE.ordinal();
	}

	/** Whether a run to {@code to} ends in Tyrvann's orbit as a jump to orbit: the holdfast during {@code BLOCKADE} (4.5, 6.5). */
	public static boolean blockadeArrival(Destination to, ChapterFiveStage stage) {
		return to == Destination.HOLDFAST && stage == ChapterFiveStage.BLOCKADE;
	}

	/**
	 * Whether the owner's ship in flight may go to a destination by the autopilot or jump to its
	 * orbit (4.5, 4.7), in order: an owner with a ship, the config switch, a ship flown at the helm,
	 * an unlocked destination, the blockade, then (for a real jump, not a hop on the same planet) the
	 * hull minimum and a cell, then {@code UNDER_FIRE} and the M4 combat lock.
	 *
	 * @param atHelm the ship is flown by hand (or held by the server for its owner)
	 * @param jumps  the trip is a jump that costs a cell and wear
	 */
	public static Refusal airRefusal(@Nullable ShipRecord record, boolean enabled, boolean atHelm, boolean unlocked, boolean blockaded, boolean jumps,
			boolean underFire, boolean inCombat, ShipBalance b) {
		if (record == null) {
			return Refusal.NOT_OWNER;
		}

		if (!enabled) {
			return Refusal.DISABLED_BY_CONFIG;
		}

		if (!atHelm || record.state() != ShipState.FLYING) {
			return Refusal.NOT_READY;
		}

		if (!unlocked) {
			return Refusal.LOCKED;
		}

		if (blockaded) {
			return Refusal.BLOCKADED;
		}

		if (jumps && record.hull() < b.jumpMinHull) {
			return Refusal.HULL;
		}

		if (jumps && record.fuel() < b.cellsPerJump) {
			return Refusal.NO_FUEL;
		}

		if (underFire) {
			return Refusal.UNDER_FIRE;
		}

		return inCombat ? Refusal.COMBAT : Refusal.NONE;
	}

	/**
	 * Whether the return to the last landing (4.10) may start: an owner with a ship flown at the
	 * helm, something to pay with, and not under fire or in combat (it needs no clear path).
	 */
	public static Refusal returnRefusal(@Nullable ShipRecord record, boolean atHelm, ReturnCost cost, boolean underFire, boolean inCombat) {
		if (record == null) {
			return Refusal.NOT_OWNER;
		}

		if (!atHelm || record.state() != ShipState.FLYING) {
			return Refusal.NOT_READY;
		}

		if (cost == ReturnCost.NONE) {
			return Refusal.NO_FUEL;
		}

		if (underFire) {
			return Refusal.UNDER_FIRE;
		}

		return inCombat ? Refusal.COMBAT : Refusal.NONE;
	}

	/** How the return to the last landing is paid: the free daily recall when unused today, else a cell from the tank, else a carried one. */
	public static ReturnCost returnCost(long today, long recallDay, int fuel, boolean carried, ShipBalance.Travel t) {
		if (t.freeRecallPerDay > 0 && recallDay != today) {
			return ReturnCost.FREE;
		}

		if (fuel > 0) {
			return ReturnCost.TANK;
		}

		return carried ? ReturnCost.CARRIED : ReturnCost.NONE;
	}

	/** The jump's cost for a run from the air (4.5, 4.7): the M4 cell and wear, nothing for a hop on the same planet. */
	public static ShipRecord afterAirJump(ShipRecord r, boolean hop, ShipBalance b) {
		return hop ? r : ShipRules.afterJump(r, b);
	}

	/**
	 * The Hyperspace phase's length (11.2): halved ({@code hymnDriveHyperspaceMult}) with the
	 * Hymn-Tuned Drive. The drive's other effect, no hull wear, is in {@link ShipRules#wear}.
	 */
	public static int hyperspaceTicks(ShipRecord r, int ticks, FlightBalance b) {
		if (!r.has(ShipRecord.PART_HYMN_DRIVE)) {
			return ticks;
		}

		return Math.max(1, Math.round(ticks * Math.max(0f, b.hymnDriveHyperspaceMult)));
	}

	// ---------------------------------------------------------------- the disabled ship (5.4)

	/** The record the rescue delivers (5.4): nothing lost, at least {@code rescueHull} hull, {@code READY} or {@code GROUNDED}. */
	public static ShipRecord rescued(ShipRecord r, FlightBalance fb, ShipBalance b) {
		ShipRecord patched = r.withHull(Math.max(r.hull(), Math.min(fb.rescueHull, ShipRules.maxHull(r, fb))));
		return ShipRules.recovered(patched, b);
	}

	// ---------------------------------------------------------------- the shipwright (4.8, 4.9, 11.2)

	/** The upgrade bit named by {@code id} in {@code ship:fit} and {@code ship:remove} (the dialogue's argument); 0 when unknown. */
	public static int upgradeOf(String id) {
		return switch (id.trim().toLowerCase(Locale.ROOT)) {
			case "cannons" -> ShipRecord.UPGRADE_CANNONS;
			case "shield" -> ShipRecord.UPGRADE_SHIELD;
			case "thrusters" -> ShipRecord.UPGRADE_THRUSTERS;
			case "missiles" -> ShipRecord.UPGRADE_MISSILES;
			case "spindle_capacitor", "capacitor" -> ShipRecord.PART_SPINDLE_CAPACITOR;
			case "hymn_tuned_drive", "hymn_drive", "drive" -> ShipRecord.PART_HYMN_DRIVE;
			default -> 0;
		};
	}

	/** The id of an M5 upgrade bit ({@link #M5_UPGRADES}), for its lang keys; empty for any other bit. */
	public static String upgradeId(int bit) {
		return switch (bit) {
			case ShipRecord.UPGRADE_CANNONS -> "cannons";
			case ShipRecord.UPGRADE_SHIELD -> "shield";
			case ShipRecord.UPGRADE_THRUSTERS -> "thrusters";
			case ShipRecord.UPGRADE_MISSILES -> "missiles";
			case ShipRecord.PART_SPINDLE_CAPACITOR -> "spindle_capacitor";
			case ShipRecord.PART_HYMN_DRIVE -> "hymn_drive";
			default -> "";
		};
	}

	/** Whether {@code bit} is a legendary ship part (fitted with its item, 11.2). */
	public static boolean isPart(int bit) {
		return bit == ShipRecord.PART_SPINDLE_CAPACITOR || bit == ShipRecord.PART_HYMN_DRIVE;
	}

	/** The credits the shipwright asks to fit {@code bit} (4.8): its price, or the legendary fitting fee. */
	public static int fitPrice(int bit, FlightBalance b) {
		return switch (bit) {
			case ShipRecord.UPGRADE_CANNONS -> b.cannonsPrice;
			case ShipRecord.UPGRADE_SHIELD -> b.shieldPrice;
			case ShipRecord.UPGRADE_THRUSTERS -> b.thrustersPrice;
			case ShipRecord.UPGRADE_MISSILES -> b.missilesPrice;
			case ShipRecord.PART_SPINDLE_CAPACITOR, ShipRecord.PART_HYMN_DRIVE -> b.legendaryFitCredits;
			default -> 0;
		};
	}

	/**
	 * Whether the shipwright fits {@code bit} now (4.8, 11.2), in order: a ship, a known upgrade, not
	 * fitted yet, the ship landed at his yard, the capacitor's shield, the part in the pack, the price.
	 */
	public static Fit fitRefusal(@Nullable ShipRecord r, int bit, boolean atYard, boolean hasPart, int credits, FlightBalance b) {
		if (r == null) {
			return Fit.NO_SHIP;
		}

		if (fitPrice(bit, b) <= 0 && !isPart(bit)) {
			return Fit.UNKNOWN;
		}

		if (r.has(bit)) {
			return Fit.ALREADY;
		}

		if (!atYard || r.state() == ShipState.HULK || r.state().flying()) {
			return Fit.NOT_HERE;
		}

		if (bit == ShipRecord.PART_SPINDLE_CAPACITOR && !r.has(ShipRecord.UPGRADE_SHIELD)) {
			return Fit.NEEDS_SHIELD;
		}

		if (isPart(bit) && !hasPart) {
			return Fit.NO_PART;
		}

		return credits < fitPrice(bit, b) ? Fit.CREDITS : Fit.OK;
	}

	/** Whether the shipwright takes {@code bit} out (4.8: for nothing, no refund): fitted, and the ship landed at his yard. */
	public static boolean canStrip(@Nullable ShipRecord r, int bit, boolean atYard) {
		return r != null && bit != 0 && (bit & ShipRecord.CLASS_INTERCEPTOR) == 0 && r.has(bit) && atYard && r.state() != ShipState.HULK
				&& !r.state().flying();
	}

	/** The record without {@code bit}; the Spindle Capacitor goes with the shield it feeds. */
	public static ShipRecord stripped(ShipRecord r, int bit, ShipBalance b) {
		int gone = bit == ShipRecord.UPGRADE_SHIELD ? bit | ShipRecord.PART_SPINDLE_CAPACITOR : bit;
		return ShipRules.withUpgrade(r, gone, false, b);
	}

	/** How many stacks the hold carries. */
	public static int holdStacks(ShipRecord r) {
		int stacks = 0;

		for (ItemStack stack : r.hold()) {
			if (!stack.isEmpty()) {
				stacks++;
			}
		}

		return stacks;
	}

	/**
	 * Whether the shipwright refits the ship now (4.9), in order: a ship, not that class already, the
	 * ship landed at his yard, nobody aboard, a hold the interceptor can carry, the price (2,500 credits
	 * and 6 alloy plates to the interceptor; 500 credits back to the courier).
	 */
	public static Refit refitRefusal(@Nullable ShipRecord r, boolean toInterceptor, boolean atYard, boolean riders, int credits, int alloy,
			FlightBalance b) {
		if (r == null) {
			return Refit.NO_SHIP;
		}

		if (r.has(ShipRecord.CLASS_INTERCEPTOR) == toInterceptor) {
			return Refit.ALREADY;
		}

		if (!atYard || r.state() == ShipState.HULK || r.state().flying()) {
			return Refit.NOT_HERE;
		}

		if (riders) {
			return Refit.RIDERS;
		}

		if (toInterceptor && holdStacks(r) > b.interceptorHold) {
			return Refit.HOLD;
		}

		if (credits < (toInterceptor ? b.refitCredits : b.refitBackCredits)) {
			return Refit.CREDITS;
		}

		return toInterceptor && alloy < b.refitAlloy ? Refit.ALLOY : Refit.OK;
	}

	/**
	 * The record after a refit (4.9): the class bit set or cleared; the hull code, fuel, upgrades and
	 * paint kept; to the interceptor the hull is cut to its most, the crew to one and the hold packed
	 * into its slots; back to the courier the hull is kept. The state follows the hull.
	 */
	public static ShipRecord refitted(ShipRecord r, boolean toInterceptor, FlightBalance fb, ShipBalance b) {
		if (!toInterceptor) {
			ShipRecord courier = r.withUpgrades(r.upgrades() & ~ShipRecord.CLASS_INTERCEPTOR);
			return courier.withState(ShipRules.restingState(courier, b));
		}

		List<ItemStack> packed = new ArrayList<>();

		for (ItemStack stack : r.hold()) {
			if (!stack.isEmpty()) {
				packed.add(stack.copy());
			}
		}

		ShipRecord interceptor = r.withUpgrades(r.upgrades() | ShipRecord.CLASS_INTERCEPTOR).withHold(packed)
				.withCrew(r.crew().size() > 1 ? List.of(r.crew().getFirst()) : r.crew());
		interceptor = interceptor.withHull(Math.min(interceptor.hull(), ShipRules.maxHull(interceptor, fb)));
		return interceptor.withState(ShipRules.restingState(interceptor, b));
	}

	private FlightRules() {
	}
}
