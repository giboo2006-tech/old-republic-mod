package com.oldrepublic.travel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.planet.Clocks;
import com.oldrepublic.planet.PlanetHubs;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.quest.QuestHooks;
import com.oldrepublic.quest.story.ChapterFive;
import com.oldrepublic.space.ShipCombat;
import com.oldrepublic.space.SpaceEncounters;
import com.oldrepublic.travel.TravelService.Refusal;
import com.oldrepublic.travel.network.FlightMotionPayload;
import com.oldrepublic.travel.network.FlightStatusPayload;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The helm on the server (spec MILESTONE_5.md 4.2–4.7, 4.10, 5.4): everything a ship does while its
 * owner holds it, from taking the helm to the touchdown of a landing by hand.
 *
 * <p>The owner in the pilot seat of a repaired ship at rest asks for it (the Helm key, or the ship
 * screen's button); with room above, the ship runs the Milestone 4 Spool, rises
 * {@code flight.helmRiseBlocks} under server control and is handed to the pilot: state
 * {@code FLYING} and the synced piloted flag, so the pilot's client flies it ({@link ShipBody},
 * 4.1). While it is flown the server keeps the boost meter, checks the spot below for a landing,
 * moves the ship between a planet's sky and its orbit, turns it back at the orbit's edge, charges a
 * crash to the hull and tells the pilot the numbers their ship flies by ({@code flight_status}).
 * The Helm key in the air lands it: straight down onto a field spot, or across a hub to a bay
 * first, under server control. The Nav tab hands it to the autopilot ({@link Flights}), jumps it to
 * another orbit, or sends it back to its last landing. At 0 hull it tumbles and the rescue takes
 * it home.
 *
 * <p>What runs is kept in memory only. The record's position stays the ship's last landing spot
 * until a landing by hand moves it, so a run that ends in the air (the pilot off the ship or off
 * the server, the body lost) goes back there (4.10); after a restart {@link Flights#recover} does
 * the same for a record left flying with no run. No reference to a body is kept across ticks.
 */
final class Helm {
	/** The steps of a run at the helm. */
	private enum Stage {
		/** The Milestone 4 Spool, on the ground. */
		SPOOL,
		/** Up {@code flight.helmRiseBlocks} under server control. */
		RISE,
		/** Flown by hand. */
		PILOTED,
		/** Held still by the server with nobody flying it: after a jump to an orbit while flight is switched off or the helm locked. */
		HOVER,
		/** Guided across a hub to its bay before the landing (4.5). */
		GLIDE,
		/** Settling straight down onto the landing spot (4.6). */
		LAND,
		/** On the ground: the Milestone 4 Settle, the riders still locked in. */
		SETTLE,
		/** Disabled: tumbling before the rescue (5.4). */
		TUMBLE
	}

	/**
	 * A sky or orbit transition waiting for its arrival's chunks (4.7).
	 *
	 * @param up      into the orbit (else down into the sky)
	 * @param target  the level it arrives in
	 * @param arrival where it arrives, decided when the ship crossed the line
	 * @param since   the server tick it was asked for
	 */
	private record Crossing(boolean up, ResourceKey<Level> target, Vec3 arrival, int since) {
		GlobalPos ticket() {
			return GlobalPos.of(target, BlockPos.containing(arrival));
		}
	}

	/** One owner at the helm of their ship. */
	private static final class Run {
		final UUID owner;
		/** The level the body is in (a transition moves it). */
		ResourceKey<Level> level;
		Stage stage;
		int ticks;
		/** Where the rise, the glide or the landing starts, and the heading and pitch there. */
		Vec3 from;
		float fromYaw;
		float fromPitch;
		/** Where the glide or the landing ends, and the heading there; the glide's last leg comes down onto {@link #pad}. */
		Vec3 to = Vec3.ZERO;
		float toYaw;
		Vec3 pad = Vec3.ZERO;
		int length = 1;
		/** The hub bay a landing goes to (other ships keep off it), or null for a field landing. */
		@Nullable BlockPos bay;
		/** The boost meter, whether Sprint is held and whether the boost runs (4.3). */
		FlightRules.Boost boost = FlightRules.Boost.FULL;
		/** The server tick of the last sky or orbit transition. */
		int lastTransition = Integer.MIN_VALUE / 2;
		@Nullable Crossing crossing;
		/** The landing check of the spot below, refreshed every few ticks while flown. */
		FlightRules.Landing landing = FlightRules.Landing.TOO_HIGH;
		/** The thruster keys held at the last tick ({@link TravelEffects#THRUST_LEFT} and the rest). */
		int thrusters;

		Run(UUID owner, ResourceKey<Level> level, Stage stage, Vec3 from, float yaw) {
			this.owner = owner;
			this.level = level;
			this.stage = stage;
			this.from = from;
			this.fromYaw = yaw;
		}
	}

	/** Struts go up halfway through the Spool, as on the autopilot. */
	private static final double GEAR_UP_AT = 0.5;
	/** The pilot's {@code flight_status} is refreshed this often while they fly (ticks). */
	private static final int STATUS_INTERVAL = 10;
	/** The landing check runs this often while the ship is flown (ticks). */
	private static final int LANDING_INTERVAL = 5;
	/** A transition waits at most this long for its arrival's chunks, then goes anyway (ticks). */
	private static final int CROSSING_WAIT = 60;
	/** The glide over a hub: blocks per tick, its shortest and longest length (ticks), and its height over the bay. */
	private static final double GLIDE_SPEED = 0.6;
	private static final int GLIDE_MIN = 20;
	private static final int GLIDE_MAX = 100;
	private static final double GLIDE_CLEARANCE = 12;
	/** A landing settles at this many ticks per block, at least the Milestone 4 Settle's length and at most {@link #LAND_MAX}. */
	private static final int LAND_TICKS_PER_BLOCK = 4;
	private static final int LAND_MAX = 80;
	/** The tumble: degrees of yaw per tick, the pitch's swing (degrees) and how fast the hull sinks (blocks per tick). */
	private static final float TUMBLE_SPIN = 4.5f;
	private static final float TUMBLE_PITCH = 12f;
	private static final float TUMBLE_PITCH_RATE = 0.3f;
	private static final double TUMBLE_SINK = 0.05;
	/** The room above a ship taking the helm: its class's landing box, from just above the ground. */
	private static final double GROUND_MARGIN = 0.05;

	private static final Map<UUID, Run> RUNS = new LinkedHashMap<>();

	// ---------------------------------------------------------------- queries

	/** Whether the owner is taking, holding or landing the helm. */
	static boolean holds(UUID owner) {
		return RUNS.containsKey(owner);
	}

	/** Whether the owner flies their ship by hand (the body's piloted flag). */
	static boolean isPiloted(UUID owner) {
		Run run = RUNS.get(owner);
		return run != null && run.stage == Stage.PILOTED;
	}

	/** Whether the ship is in the air at its owner's helm: flown by hand or held by the server (the Nav's landings and jumps, the return). */
	static boolean atHelm(UUID owner) {
		Run run = RUNS.get(owner);
		return run != null && (run.stage == Stage.PILOTED || run.stage == Stage.HOVER);
	}

	/**
	 * The phase the owner's body shows: the Spool's, the climb's for the rise,
	 * {@link ShipRules#PHASE_HELM} in the air, the descent's and the Settle's for a landing.
	 */
	static int phaseOf(UUID owner) {
		Run run = RUNS.get(owner);

		if (run == null) {
			return ShipRules.PHASE_GROUND;
		}

		return switch (run.stage) {
			case SPOOL -> ShipRules.PHASE_SPOOL;
			case RISE -> ShipRules.PHASE_CLIMB;
			case PILOTED, HOVER, GLIDE, TUMBLE -> ShipRules.PHASE_HELM;
			case LAND -> ShipRules.PHASE_DESCENT;
			case SETTLE -> ShipRules.PHASE_SETTLE;
		};
	}

	/** Whether {@code ship} is the body at the owner's helm (any other copy discards itself). */
	static boolean isBody(UUID owner, ShipBody ship) {
		Run run = RUNS.get(owner);
		return run != null && ship.level().dimension().equals(run.level);
	}

	/** The bays ships landing by hand are on their way to in {@code dimension} (they hold those bays, 4.5). */
	static List<BlockPos> targets(ResourceKey<Level> dimension, UUID except) {
		List<BlockPos> targets = new ArrayList<>();

		for (Run run : RUNS.values()) {
			if (run.bay != null && !run.owner.equals(except) && run.level.equals(dimension) && (run.stage == Stage.GLIDE || run.stage == Stage.LAND)) {
				targets.add(run.bay);
			}
		}

		return targets;
	}

	// ---------------------------------------------------------------- taking the helm (4.2, 4.5)

	/**
	 * The Helm key's {@code TAKE_HELM} (and the ship screen's button): takes the helm of the ship at
	 * rest (4.2), takes it back from the server's hold, or takes it from the autopilot during a
	 * Descent (4.5). Returns why not otherwise.
	 */
	static Refusal take(ServerPlayer player) {
		Run held = RUNS.get(player.getUUID());

		if (held != null) {
			return held.stage == Stage.HOVER ? takeBack(player, held) : Refusal.BUSY;
		}

		if (Flights.isDescending(player.getUUID())) {
			return takeOver(player);
		}

		Refusal refusal = restRefusal(player);
		MinecraftServer server = player.level().getServer();
		ShipRecord record = ShipRegistry.of(server, player.getUUID()).orElse(null);
		ShipBody ship = record == null ? null : Ships.ship(server, record.owner());

		if (refusal != Refusal.NONE || record == null || ship == null) {
			return refusal != Refusal.NONE ? refusal : Refusal.NOT_READY;
		}

		Run run = new Run(record.owner(), ship.level().dimension(), Stage.SPOOL, ship.position(), ship.getYRot());
		RUNS.put(run.owner, run);
		ShipRecord launching = Ships.update(server, run.owner, r -> r.withState(ShipState.LAUNCHING)).orElse(record);
		TravelEffects.engine(ship, TravelEffects.ENGINE_SPOOL);
		TravelEffects.flight(ship, TravelEffects.FLIGHT_HELM, 1f);
		status(player, run, launching);
		OldRepublicMod.LOGGER.info("{} takes the helm at {}", player.getPlainTextName(), ship.blockPosition().toShortString());
		return Refusal.NONE;
	}

	/**
	 * Why the owner may not take the helm now (the ship screen's Helm button, 4.2, 4.5, 13.2), or
	 * {@code NONE}: what {@link #take} would answer, without taking it.
	 */
	static Refusal helmRefusal(ServerPlayer player) {
		Run held = RUNS.get(player.getUUID());

		if (held != null) {
			return held.stage == Stage.HOVER ? takeBackRefusal(player, held) : Refusal.BUSY;
		}

		if (Flights.isDescending(player.getUUID())) {
			return takeOverRefusal(player);
		}

		return restRefusal(player);
	}

	/**
	 * Why the owner may not take the helm of their ship at rest (4.2): the rules of
	 * {@link FlightRules#helmRefusal} for the ship they ride, then room above it to rise.
	 */
	private static Refusal restRefusal(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		ShipRecord record = Ships.ridden(player).filter(r -> r.owner().equals(player.getUUID())).orElse(null);
		ShipBody ship = record == null ? null : Ships.ship(server, record.owner());
		boolean busy = record != null && Ships.busy(record);
		boolean inCombat = record != null && CombatLock.inCombat(server, record, ship);
		FlightBalance b = FlightBalance.get();
		Refusal refusal = FlightRules.helmRefusal(record, b.enabled, TravelAttachments.get(player).helm(), busy, inCombat);

		if (refusal != Refusal.NONE) {
			return refusal;
		}

		if (ship == null) {
			return Refusal.NOT_READY;
		}

		return roomToRise(ship, b) ? Refusal.NONE : Refusal.NO_ROOM;
	}

	/**
	 * Whether the ship has room to rise (4.2, 4.6): its class's landing box, from just above the
	 * ground to {@code helmRiseBlocks} over its top, holds no block and no grounded ship.
	 */
	static boolean roomToRise(ShipBody ship, FlightBalance b) {
		double half = ShipBody.footprintHalf(ship.interceptor());
		Vec3 base = ship.position();
		AABB box = new AABB(base.x - half, base.y + GROUND_MARGIN, base.z - half, base.x + half,
				base.y + ShipBody.footprintHeight(ship.interceptor()) + Math.max(0, b.helmRiseBlocks), base.z + half);
		return ship.level().noCollision(ship, box);
	}

	/** The server held the ship (flight switched off, or the helm locked, when it reached an orbit): the owner takes it back now. */
	private static Refusal takeBack(ServerPlayer player, Run run) {
		Refusal refusal = takeBackRefusal(player, run);
		MinecraftServer server = player.level().getServer();
		ShipRecord record = ShipRegistry.of(server, run.owner).orElse(null);
		ShipBody ship = Ships.ship(server, run.owner);

		if (refusal != Refusal.NONE || record == null || ship == null) {
			return refusal != Refusal.NONE ? refusal : Refusal.NOT_READY;
		}

		handOver(player, run, ship, record);
		return Refusal.NONE;
	}

	private static Refusal takeBackRefusal(ServerPlayer player, Run run) {
		MinecraftServer server = player.level().getServer();
		ShipBody ship = Ships.ship(server, run.owner);

		if (!FlightBalance.get().enabled) {
			return Refusal.DISABLED_BY_CONFIG;
		}

		if (!TravelAttachments.get(player).helm()) {
			return Refusal.NO_HELM;
		}

		return ship != null && player.getVehicle() == ship && ShipRegistry.of(server, run.owner).isPresent() ? Refusal.NONE : Refusal.NOT_READY;
	}

	/**
	 * Takes the helm from the autopilot during its Descent (4.5): the landing is abandoned and the
	 * ship stays in the air, flown by hand. Its record keeps the pad the run was going to as its last
	 * landing spot (the jump already moved it there).
	 */
	private static Refusal takeOver(ServerPlayer player) {
		Refusal refusal = takeOverRefusal(player);
		MinecraftServer server = player.level().getServer();
		ShipRecord record = ShipRegistry.of(server, player.getUUID()).orElse(null);
		ShipBody ship = record == null ? null : Ships.ship(server, record.owner());

		if (refusal != Refusal.NONE || record == null || ship == null || !Flights.abandon(server, record.owner())) {
			return refusal != Refusal.NONE ? refusal : Refusal.NOT_READY;
		}

		Run run = new Run(record.owner(), ship.level().dimension(), Stage.PILOTED, ship.position(), ship.getYRot());
		RUNS.put(run.owner, run);
		ShipRecord flying = Ships.update(server, run.owner, r -> r.withState(ShipState.FLYING).withFlight(Optional.empty())).orElse(record);
		ship.setGear(false);
		handOver(player, run, ship, flying);
		QuestHooks.onHelmTaken(player);
		OldRepublicMod.LOGGER.info("{} takes the helm from the autopilot", player.getPlainTextName());
		return Refusal.NONE;
	}

	private static Refusal takeOverRefusal(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		ShipRecord record = ShipRegistry.of(server, player.getUUID()).orElse(null);
		ShipBody ship = record == null ? null : Ships.ship(server, record.owner());
		Refusal refusal = FlightRules.takeOverRefusal(record, FlightBalance.get().enabled, TravelAttachments.get(player).helm(), true);

		if (refusal != Refusal.NONE) {
			return refusal;
		}

		return ship != null && player.getVehicle() == ship ? Refusal.NONE : Refusal.NOT_READY;
	}

	/**
	 * The server's run that ended in an orbit hands the ship back (4.5 the blockade, 4.7 a jump to an
	 * orbit): flown by hand again, or held still when flight is switched off or the helm locked. The
	 * record is {@code FLYING} with no flight; its position is still the last landing spot.
	 */
	static void resume(MinecraftServer server, UUID owner, ShipBody ship) {
		ServerPlayer pilot = server.getPlayerList().getPlayer(owner);
		boolean canFly = FlightBalance.get().enabled && pilot != null && TravelAttachments.get(pilot).helm();
		Run run = new Run(owner, ship.level().dimension(), Stage.HOVER, ship.position(), ship.getYRot());
		RUNS.put(owner, run);
		ShipRecord record = Ships.update(server, owner, r -> r.withState(ShipState.FLYING).withFlight(Optional.empty())).orElse(null);
		ship.setGear(false);

		if (record == null) {
			return;
		}

		if (canFly) {
			handOver(pilot, run, ship, record);
		} else {
			status(pilot, run, record);
		}
	}

	/** Control passes to the pilot (4.2): the status (with the flight numbers) goes out before the piloted flag syncs. */
	private static void handOver(ServerPlayer pilot, Run run, ShipBody ship, ShipRecord record) {
		enter(run, Stage.PILOTED);
		ship.resetMeasure();
		Ships.show(ship, record);
		TravelEffects.flight(ship, TravelEffects.FLIGHT_HELM, 1f);
		status(pilot, run, record);
	}

	// ---------------------------------------------------------------- ticking

	/** Every server tick, after the autopilot: each run one step. */
	static void tick(MinecraftServer server) {
		for (Run run : new ArrayList<>(RUNS.values())) {
			if (RUNS.get(run.owner) != run) {
				continue;
			}

			try {
				step(server, run);
			} catch (RuntimeException e) {
				OldRepublicMod.LOGGER.error("The helm of {}'s ship failed; settling it", run.owner, e);
				release(server, run, true);
			}
		}
	}

	private static void step(MinecraftServer server, Run run) {
		ShipRecord record = ShipRegistry.of(server, run.owner).orElse(null);
		ShipBody ship = record == null ? null : Ships.ship(server, run.owner);

		if (record == null || ship == null || !isBody(run.owner, ship)) {
			lost(server, run, record);
			return;
		}

		ServerPlayer pilot = server.getPlayerList().getPlayer(run.owner);
		boolean aboard = pilot != null && pilot.getVehicle() == ship;

		if (!aboard && needsPilot(run.stage)) {
			// The pilot left the seat or the server, or died (4.10): before the rise nothing has happened; in the air the ship goes home.
			if (run.stage == Stage.SPOOL || run.stage == Stage.RISE) {
				release(server, run, true);
			} else {
				rescue(server, run, record, ship, false);
			}

			return;
		}

		run.ticks++;
		FlightBalance b = FlightBalance.get();

		switch (run.stage) {
			case SPOOL -> spool(server, run, ship, record, pilot, b);
			case RISE -> rise(server, run, ship, pilot, b);
			case PILOTED -> fly(server, run, ship, record, pilot, b);
			case HOVER -> hover(run, record, pilot);
			case GLIDE -> glide(server, run, ship, record, pilot);
			case LAND -> descend(server, run, ship, pilot);
			case SETTLE -> settle(server, run, ship, pilot);
			case TUMBLE -> tumble(server, run, ship, record, b);
		}
	}

	/** The stages that end when the pilot is gone; a landing, a Settle and a tumble carry on without them. */
	private static boolean needsPilot(Stage stage) {
		return stage == Stage.SPOOL || stage == Stage.RISE || stage == Stage.PILOTED || stage == Stage.HOVER;
	}

	/** The Milestone 4 Spool: the ramp closes and the struts come up halfway through; then the room check and the rise. */
	private static void spool(MinecraftServer server, Run run, ShipBody ship, ShipRecord record, @Nullable ServerPlayer pilot, FlightBalance b) {
		int length = Math.max(1, b.helmSpoolTicks);

		if (run.ticks >= length * GEAR_UP_AT) {
			ship.setGear(false);
		}

		if (run.ticks < length) {
			return;
		}

		if (!roomToRise(ship, b)) {
			// Something came into the way during the Spool: the ship stays down.
			if (pilot != null) {
				pilot.sendOverlayMessage(Component.translatable(Refusal.NO_ROOM.langKey()));
			}

			release(server, run, true);
			return;
		}

		enter(run, Stage.RISE);
		Ships.show(ship, record);
		TravelEffects.engine(ship, TravelEffects.ENGINE_CLIMB);
		status(pilot, run, record);
	}

	/** Up {@code flight.helmRiseBlocks} blocks, easing in and out, then the hand-over. */
	private static void rise(MinecraftServer server, Run run, ShipBody ship, @Nullable ServerPlayer pilot, FlightBalance b) {
		int length = Math.max(1, b.helmRiseTicks);
		double u = Math.min(1.0, run.ticks / (double) length);
		ship.flyTo(run.from.add(0, b.helmRiseBlocks * smooth(u), 0), run.fromYaw, 0f);

		if (run.ticks < length || pilot == null) {
			return;
		}

		ShipRecord flying = Ships.update(server, run.owner, r -> r.withState(ShipState.FLYING)).orElseThrow();
		handOver(pilot, run, ship, flying);
		QuestHooks.onHelmTaken(pilot);
	}

	/**
	 * Flown by hand: the boost meter drains while the boost runs and refills while it rests (4.3);
	 * thruster puffs on new strafe and climb keys; the sky and orbit transitions and the orbit's
	 * edge (4.7); the landing check every {@link #LANDING_INTERVAL} ticks (4.6).
	 */
	private static void fly(MinecraftServer server, Run run, ShipBody ship, ShipRecord record, @Nullable ServerPlayer pilot, FlightBalance b) {
		if (pilot == null) {
			return;
		}

		boolean wasBoosting = run.boost.running();
		run.boost = run.boost.tick(FlightRules.boostTicks(b, record.upgrades()), b.boostRechargeTicks);
		boolean changed = wasBoosting && !run.boost.running();

		thrusters(run, ship, pilot);

		if (transition(server, run, ship, record, pilot, b) || edge(run, ship, pilot, b)) {
			return;
		}

		if (run.ticks % LANDING_INTERVAL == 0) {
			FlightRules.Landing before = run.landing;
			run.landing = Landings.check(pilot.level(), ship, run.owner, ship.measuredVelocity().length(), b).result();
			changed |= before != run.landing;
		}

		if (changed || run.ticks % STATUS_INTERVAL == 0) {
			status(pilot, run, record);
		}
	}

	/** Puffs from the hull's thrusters when a strafe, climb or descent key goes down (4.11). */
	private static void thrusters(Run run, ShipBody ship, ServerPlayer pilot) {
		Input input = pilot.getLastClientInput();
		int keys = (input.left() ? TravelEffects.THRUST_LEFT : 0) | (input.right() ? TravelEffects.THRUST_RIGHT : 0)
				| (input.jump() ? TravelEffects.THRUST_UP : 0) | (input.shift() ? TravelEffects.THRUST_DOWN : 0);
		int pressed = keys & ~run.thrusters;
		run.thrusters = keys;

		if (pressed != 0) {
			TravelEffects.flight(ship, TravelEffects.FLIGHT_THRUSTER, pressed);
		}
	}

	/** Held still by the server: the status now and then, so the pilot's HUD says why the controls do nothing. */
	private static void hover(Run run, ShipRecord record, @Nullable ServerPlayer pilot) {
		if (run.ticks % STATUS_INTERVAL == 0) {
			status(pilot, run, record);
		}
	}

	/**
	 * {@code flight_boost} (4.3): the pilot pressed or let go of Sprint. A press starts the boost
	 * unless the meter is empty; letting go or emptying it stops the boost.
	 */
	static void boost(ServerPlayer player, boolean pressed) {
		Run run = RUNS.get(player.getUUID());

		if (run == null || run.stage != Stage.PILOTED) {
			return;
		}

		boolean was = run.boost.running();
		run.boost = run.boost.press(pressed);

		if (run.boost.running() == was) {
			return;
		}

		MinecraftServer server = player.level().getServer();
		ShipBody ship = Ships.ship(server, run.owner);

		if (run.boost.running() && ship != null) {
			TravelEffects.flight(ship, TravelEffects.FLIGHT_BOOST, 1f);
		}

		ShipRegistry.of(server, run.owner).ifPresent(r -> status(player, run, r));
	}

	// ---------------------------------------------------------------- sky, orbit and the edge (4.7)

	/**
	 * Sky to orbit and back (4.7): above {@code orbitCeiling} in a planet's sky, or below
	 * {@code atmosphereLine} in an orbit, the arrival's chunks are held under a ticket and, once they
	 * are loaded (or after {@link #CROSSING_WAIT}), the ship and its riders move across. A crossing
	 * that no longer applies (the pilot turned back) is dropped; a world that does not exist here
	 * (the game-test server has no orbits) is never crossed into. True when the ship moved.
	 */
	private static boolean transition(MinecraftServer server, Run run, ShipBody ship, ShipRecord record, ServerPlayer pilot, FlightBalance b) {
		int now = server.getTickCount();
		ResourceKey<Level> here = ship.level().dimension();
		Planets.Planet sky = Planets.Planet.of(here);
		Planets.Planet orbit = Planets.orbitOf(here);
		FlightRules.Transition due = FlightRules.transition(sky != null, orbit != null, ship.getY(), now - run.lastTransition, b);
		Crossing crossing = run.crossing;

		if (crossing != null && (due == FlightRules.Transition.NONE || crossing.up() != (due == FlightRules.Transition.UP))) {
			dropCrossing(server, run);
			crossing = null;
		}

		if (due == FlightRules.Transition.NONE) {
			return false;
		}

		boolean up = due == FlightRules.Transition.UP;
		Planets.Planet planet = up ? sky : orbit;

		if (planet == null) {
			return false;
		}

		ServerLevel to = server.getLevel(up ? Planets.orbit(planet) : planet.level());

		if (to == null) {
			return false;
		}

		if (crossing == null) {
			Vec3 arrival = up ? FlightRules.toOrbit(ship.getX(), ship.getZ(), b) : FlightRules.toSky(ship.getX(), ship.getZ(), b);
			crossing = new Crossing(up, to.dimension(), arrival, now);
			run.crossing = crossing;
			TravelTickets.hold(server, crossing.ticket(), ShipBalance.travel().chunkTicketRadius);
			atmosphere(ship, up, TravelEffects.ATMOSPHERE_START);

			if (!up && planet != Planets.Planet.HOME && PlanetHubs.hub(server, planet) == null) {
				// A ship's first dive into a planet's sky places its hub (the M4 rule for the first course set for it).
				PlanetHubs.ensure(server, planet);
			}
		}

		if (!TravelTickets.ready(to, BlockPos.containing(crossing.arrival()), ShipBalance.travel().chunkTicketRadius) && now - crossing.since() < CROSSING_WAIT) {
			return false;
		}

		cross(server, run, ship, record, pilot, crossing, to);
		return true;
	}

	/**
	 * Moves the ship and its riders across (4.7) with its heading, tells the pilot's client the speed
	 * the server measured ({@code flight_motion}), ends every rider's atmosphere overlay (it began as
	 * the ship reached the line) and, going up, tells the story the ship entered the orbit.
	 */
	private static void cross(MinecraftServer server, Run run, ShipBody ship, ShipRecord record, ServerPlayer pilot, Crossing crossing, ServerLevel to) {
		ShipBalance.Travel t = ShipBalance.travel();
		FlightBalance b = FlightBalance.get();
		Vec3 velocity = ship.measuredVelocity();
		float yaw = ship.getYRot();
		run.crossing = null;
		run.lastTransition = server.getTickCount();
		// Into an orbit: inside the arrivals' band; into a sky: below the line, so it does not cross straight back up.
		Vec3 arrival = clearArrival(to, ship, run.owner, crossing.arrival(), crossing.up() ? Double.POSITIVE_INFINITY : b.orbitCeiling - 1,
				crossing.up() ? FlightRules.arrivalRadius(b) : 0);
		Entity moved = TravelService.moveAcross(ship, to, arrival, yaw);
		TravelTickets.release(server, crossing.ticket(), t.chunkTicketRadius, t.ticketLingerTicks);

		if (moved.level() != to || !(moved instanceof ShipBody body)) {
			OldRepublicMod.LOGGER.warn("The ship of {} did not cross into {}", run.owner, crossing.target().identifier());
			return;
		}

		run.level = to.dimension();
		Ships.show(body, record);
		body.resetMeasure();
		TravelNetwork.send(pilot, new FlightMotionPayload((float) velocity.x, (float) velocity.y, (float) velocity.z, yaw, false));

		atmosphere(body, crossing.up(), TravelEffects.ATMOSPHERE_END);

		Planets.Planet system = Planets.orbitOf(to.dimension());

		if (crossing.up() && system != null) {
			QuestHooks.onOrbitEntered(pilot, system, false);
		}

		status(pilot, run, record);
		OldRepublicMod.LOGGER.info("The ship of {} crossed into {}", pilot.getPlainTextName(), to.dimension().identifier());
	}

	/** Gives up a crossing not made yet: its ticket goes, and the riders' atmosphere overlay ends where they are. */
	private static void dropCrossing(MinecraftServer server, Run run) {
		Crossing crossing = run.crossing;

		if (crossing == null) {
			return;
		}

		TravelTickets.release(server, crossing.ticket(), ShipBalance.travel().chunkTicketRadius, 0);
		run.crossing = null;
		ShipBody ship = Ships.ship(server, run.owner);

		if (ship != null) {
			atmosphere(ship, crossing.up(), TravelEffects.ATMOSPHERE_END);
		}
	}

	/** The atmosphere overlay for everyone aboard: its start as the ship reaches the line, its end once across or turned back. */
	private static void atmosphere(ShipBody ship, boolean up, float stage) {
		for (ServerPlayer rider : riders(ship)) {
			TravelEffects.atmosphere(rider, ship, up ? TravelEffects.ATMOSPHERE_UP : TravelEffects.ATMOSPHERE_DOWN, stage);
		}
	}

	/**
	 * Where {@code owner}'s ship arriving at {@code at} in {@code level} goes (4.7): {@code at} when its
	 * hull fits there, else the nearest clear place {@link FlightRules#clearArrival} finds, never
	 * above {@code ceiling} nor farther than {@code radius} from (0, 0). {@code at} itself when its
	 * chunk is not loaded, or when nothing near it fits (logged). The crossings, the orbit's edge and
	 * the jumps into an orbit ({@link Flights}) place their ships through it.
	 */
	static Vec3 clearArrival(ServerLevel level, ShipBody ship, UUID owner, Vec3 at, double ceiling, double radius) {
		if (!level.isLoaded(BlockPos.containing(at))) {
			OldRepublicMod.LOGGER.info("The arrival of the ship of {} at {} in {} is not loaded yet: not checked for blocks", owner, at,
					level.dimension().identifier());
			return at;
		}

		AABB hull = ship.getBoundingBox().move(-ship.getX(), -ship.getY(), -ship.getZ());
		Optional<Vec3> found = FlightRules.clearArrival(at, p -> Ships.hullFits(level, hull.move(p), ship), FlightRules.CLEAR_UP, FlightRules.CLEAR_SIDE,
				ceiling, radius);

		if (found.isEmpty()) {
			OldRepublicMod.LOGGER.warn("No clear place for the ship of {} near {} in {}: it arrives there anyway", owner, at, level.dimension().identifier());
			return at;
		}

		if (!found.get().equals(at)) {
			OldRepublicMod.LOGGER.info("The ship of {} arrives in {} at {}, clear of the blocks at {}", owner, level.dimension().identifier(), found.get(), at);
		}

		return found.get();
	}

	/**
	 * The orbit's edge (4.7): a ship that reaches it is moved {@link FlightRules#EDGE_PUSH_BACK}
	 * blocks back inside, turned toward the system's centre with its pilot's look, stopped, and told
	 * to use the nav to leave. True when the ship was turned back.
	 */
	private static boolean edge(Run run, ShipBody ship, ServerPlayer pilot, FlightBalance b) {
		if (!Planets.isOrbit(ship.level()) || FlightRules.edgeDistance(ship.getX(), ship.getZ(), b) > 0) {
			return false;
		}

		dropCrossing(pilot.level().getServer(), run);
		Vec3 back = clearArrival(pilot.level(), ship, run.owner, FlightRules.turnBack(ship.getX(), ship.getY(), ship.getZ(), b), Double.POSITIVE_INFINITY,
				b.orbitRadius - FlightRules.EDGE_PUSH_BACK);
		float yaw = FlightRules.yawToCentre(back.x, back.z);
		ship.absSnapTo(back.x, back.y, back.z, yaw, ship.getXRot());
		ship.resetMeasure();
		// The position first, then the motion: the client counts the move as the server's, not as a correction.
		pilot.connection.send(ClientboundMoveVehiclePacket.fromEntity(ship));
		TravelNetwork.send(pilot, new FlightMotionPayload(0f, 0f, 0f, yaw, true));
		TravelEffects.flight(ship, TravelEffects.FLIGHT_EDGE, 1f);
		pilot.sendOverlayMessage(Component.translatable("hud.oldrepublic.flight.edge_turned"));
		return true;
	}

	// ---------------------------------------------------------------- crashes and the disabled ship (4.4, 5.4)

	/**
	 * A crash the server found after one of the pilot's moves (4.4, {@link ShipBody}): the hull pays
	 * {@code impactHull} for each {@code impactStep} of the speed lost beyond {@code impactFloor},
	 * sparks fly at the struck point, and at 0 hull the ship is disabled. A crash that disables the
	 * ship ends its owner's space encounter or blockade as enemy fire does (5.4, 6.5): W2's
	 * {@code ShipCombat} tells the encounters after its own {@link Ships#disable}, and this is the
	 * crash's one call. Only a disable that happens counts; a tumbling ship flies no moves, so one
	 * disable calls it once.
	 */
	static void impact(ServerLevel level, ShipBody ship, double lost, Vec3 point) {
		MinecraftServer server = level.getServer();
		ShipRecord record = ShipRegistry.byBody(server, ship.getUUID()).orElse(null);
		Run run = record == null ? null : RUNS.get(record.owner());

		if (run == null || run.stage != Stage.PILOTED) {
			return;
		}

		int damage = FlightRules.impactDamage(lost, FlightBalance.get());
		TravelEffects.flight(ship, TravelEffects.FLIGHT_IMPACT, point, (float) lost);

		if (damage <= 0) {
			return;
		}

		ShipRecord after = Ships.update(server, run.owner, r -> r.withHull(r.hull() - damage)).orElse(record);

		if (after.hull() <= 0 && disable(server, run.owner)) {
			ServerPlayer owner = server.getPlayerList().getPlayer(run.owner);

			if (owner != null) {
				SpaceEncounters.onDisabled(owner);
			}
		}
	}

	/**
	 * The ship is disabled (5.4, {@link Ships#disable}): the controls lock and it tumbles for
	 * {@code flight.tumbleTicks}, then the rescue takes it home. Only a ship in the air at its owner's
	 * helm (flown, held, or landing) can be disabled; returns whether this call disabled it (false
	 * when it already tumbles, or is spooling, rising or settling).
	 */
	static boolean disable(MinecraftServer server, UUID owner) {
		Run run = RUNS.get(owner);

		if (run == null || run.stage == Stage.SPOOL || run.stage == Stage.RISE || run.stage == Stage.SETTLE || run.stage == Stage.TUMBLE) {
			return false;
		}

		ShipRecord record = ShipRegistry.of(server, owner).orElse(null);
		ShipBody ship = Ships.ship(server, owner);

		if (record == null || ship == null) {
			return false;
		}

		dropCrossing(server, run);
		run.boost = run.boost.press(false);
		run.bay = null;
		enter(run, Stage.TUMBLE);
		Ships.show(ship, record);
		TravelEffects.disabled(ship, TravelEffects.DISABLED_TUMBLE);
		status(server.getPlayerList().getPlayer(owner), run, record);
		OldRepublicMod.LOGGER.info("The ship of {} is disabled", owner);
		return true;
	}

	/** The tumble: a slow spin and wobble while the hull sinks, then the free rescue at {@code flight.rescueHull}. */
	private static void tumble(MinecraftServer server, Run run, ShipBody ship, ShipRecord record, FlightBalance b) {
		ship.setYRot(Mth.wrapDegrees(ship.getYRot() + TUMBLE_SPIN));
		ship.setXRot(TUMBLE_PITCH * Mth.sin(run.ticks * TUMBLE_PITCH_RATE));
		ship.move(MoverType.SELF, new Vec3(0, -TUMBLE_SINK, 0));

		if (run.ticks >= Math.max(1, b.tumbleTicks)) {
			rescue(server, run, record, ship, true);
		}
	}

	/** A body below the world at the helm (4.10): the rescue takes it home at once. */
	static void belowWorld(MinecraftServer server, UUID owner) {
		Run run = RUNS.get(owner);
		ShipRecord record = ShipRegistry.of(server, owner).orElse(null);
		ShipBody ship = Ships.ship(server, owner);

		if (run == null || record == null || ship == null) {
			return;
		}

		if (run.stage == Stage.SPOOL || run.stage == Stage.RISE || run.stage == Stage.SETTLE) {
			release(server, run, true);
		} else {
			rescue(server, run, record, ship, run.stage == Stage.TUMBLE);
		}
	}

	/**
	 * The rescue run (5.4, 4.10): the server flies the ship home to its last landing spot with
	 * everyone aboard ({@link Flights#rescue}); with {@code disabled} it is the disabled ship's, which
	 * arrives with {@code flight.rescueHull}.
	 */
	private static void rescue(MinecraftServer server, Run run, ShipRecord record, ShipBody ship, boolean disabled) {
		RUNS.remove(run.owner);
		dropCrossing(server, run);
		ServerPlayer owner = server.getPlayerList().getPlayer(run.owner);

		if (owner != null) {
			TravelNetwork.send(owner, none(run, record, owner.level()));
		}

		Flights.rescue(server, record, ship, disabled);
		OldRepublicMod.LOGGER.info("The rescue takes the ship of {} home{}", run.owner, disabled ? " (disabled)" : "");
	}

	// ---------------------------------------------------------------- landing by hand (4.5, 4.6)

	/**
	 * The Helm key in the air (4.6): the landing check of the spot below, made now. A refusal says
	 * why above the hotbar, with a warning tone; a spot that passes settles the ship straight down
	 * under server control; inside a hub's footprint the ship first glides onto the first free bay
	 * (4.5).
	 */
	static void land(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		Run run = RUNS.get(player.getUUID());
		ShipRecord record = ShipRegistry.of(server, player.getUUID()).orElse(null);
		ShipBody ship = run == null ? null : Ships.ship(server, run.owner);

		if (run == null || run.stage != Stage.PILOTED || record == null || ship == null || player.getVehicle() != ship) {
			return;
		}

		ServerLevel level = player.level();
		Landings.Check check = Landings.check(level, ship, run.owner, ship.measuredVelocity().length(), FlightBalance.get());
		FlightRules.Landing result = check.result();

		if (result == FlightRules.Landing.HUB) {
			Pads.Pad bay = Landings.hubBay(level, ship, run.owner);

			if (bay != null) {
				glideTo(run, ship, record, player, bay);
				return;
			}

			result = FlightRules.Landing.NO_ROOM;
		}

		if (!result.allowed()) {
			run.landing = result;
			TravelEffects.flight(ship, TravelEffects.FLIGHT_LAND_REFUSED, 1f);
			player.sendOverlayMessage(Component.translatable(result.langKey()));
			status(player, run, record);
			return;
		}

		landAt(run, ship, record, player, check.spot(), ship.getYRot(), null);
	}

	/** The glide across a hub (4.5): level with or above the bay by {@link #GLIDE_CLEARANCE}, then down onto it. */
	private static void glideTo(Run run, ShipBody ship, ShipRecord record, ServerPlayer pilot, Pads.Pad bay) {
		Vec3 pad = Vec3.atBottomCenterOf(bay.pos().pos());
		startPath(run, ship);
		run.to = new Vec3(pad.x, Math.max(run.from.y, pad.y + GLIDE_CLEARANCE), pad.z);
		run.toYaw = bay.yaw();
		run.pad = pad;
		run.bay = bay.pos().pos();
		run.length = Mth.clamp((int) Math.ceil(run.from.subtract(run.to).horizontalDistance() / GLIDE_SPEED), GLIDE_MIN, GLIDE_MAX);
		enter(run, Stage.GLIDE);
		Ships.show(ship, record);
		status(pilot, run, record);
	}

	private static void glide(MinecraftServer server, Run run, ShipBody ship, ShipRecord record, @Nullable ServerPlayer pilot) {
		double u = Math.min(1.0, run.ticks / (double) run.length);
		double across = smooth(u);
		double climb = smooth(Math.min(1.0, 2 * u));
		Vec3 at = new Vec3(Mth.lerp(across, run.from.x, run.to.x), Mth.lerp(climb, run.from.y, run.to.y), Mth.lerp(across, run.from.z, run.to.z));
		ship.flyTo(at, turn(run.fromYaw, run.toYaw, across), run.fromPitch * (float) (1 - across));

		if (run.ticks >= run.length) {
			landAt(run, ship, record, pilot, run.pad, run.toYaw, run.bay);
		}
	}

	/** Settles straight down onto {@code spot}, facing {@code yaw}: the landing's descent, under server control. */
	private static void landAt(Run run, ShipBody ship, ShipRecord record, @Nullable ServerPlayer pilot, Vec3 spot, float yaw, @Nullable BlockPos bay) {
		startPath(run, ship);
		run.to = spot;
		run.toYaw = yaw;
		run.bay = bay;
		int least = Math.max(1, ShipBalance.travel().settleTicks);
		run.length = Mth.clamp((int) Math.ceil(Math.max(0, run.from.y - spot.y) * LAND_TICKS_PER_BLOCK), least, Math.max(least, LAND_MAX));
		enter(run, Stage.LAND);
		Ships.show(ship, record);
		TravelEffects.engine(ship, TravelEffects.ENGINE_DESCENT);
		status(pilot, run, record);
	}

	/** A path under server control starts here: the boost stops and a crossing not made yet is given up. */
	private static void startPath(Run run, ShipBody ship) {
		dropCrossing(ship.level().getServer(), run);
		run.from = ship.position();
		run.fromYaw = ship.getYRot();
		run.fromPitch = ship.getXRot();
		run.boost = run.boost.press(false);
	}

	/** The landing's descent, the struts down for its last blocks. */
	private static void descend(MinecraftServer server, Run run, ShipBody ship, @Nullable ServerPlayer pilot) {
		double u = smooth(Math.min(1.0, run.ticks / (double) run.length));
		Vec3 at = run.from.lerp(run.to, u);
		ship.flyTo(at, turn(run.fromYaw, run.toYaw, u), run.fromPitch * (float) (1 - u));

		if (at.y - run.to.y <= ShipRules.STRUT_HEIGHT) {
			ship.setGear(true);
		}

		if (run.ticks >= run.length) {
			touchdown(server, run, ship, pilot);
		}
	}

	/**
	 * Touchdown (4.6): the record's position becomes the landing spot and its state is set as at the
	 * Milestone 4 Settle; the spot is remembered as the owner's last pad; every rider arrives on the
	 * planet. The riders stay locked in until the Settle ends.
	 */
	private static void touchdown(MinecraftServer server, Run run, ShipBody ship, @Nullable ServerPlayer pilot) {
		GlobalPos spot = GlobalPos.of(run.level, BlockPos.containing(run.to));
		ship.flyTo(run.to, run.toYaw, 0f);
		ship.setGear(true);
		enter(run, Stage.SETTLE);
		ShipRecord landed = Ships.update(server, run.owner, r -> FlightRules.landedAt(r, spot, run.toYaw, ShipBalance.get())).orElse(null);
		Pads.landed(server, run.owner);

		if (pilot != null) {
			TravelAttachments.set(pilot, TravelAttachments.get(pilot).withLastPad(Optional.of(spot)));
		}

		TravelEffects.engine(ship, TravelEffects.ENGINE_SETTLE);
		Planets.Planet planet = Planets.Planet.of(run.level);

		if (planet != null) {
			for (ServerPlayer rider : riders(ship)) {
				QuestHooks.onPlanetArrived(rider, planet, TravelVisits.visit(rider, planet));
			}
		}

		if (landed != null) {
			status(pilot, run, landed);
		}

		OldRepublicMod.LOGGER.info("The ship of {} landed at {}", run.owner, spot.pos().toShortString());
	}

	/** The Milestone 4 Settle on the landing spot; then the riders step off beside the ramp and the helm is released. */
	private static void settle(MinecraftServer server, Run run, ShipBody ship, @Nullable ServerPlayer pilot) {
		if (run.ticks < Math.max(1, ShipBalance.travel().settleTicks)) {
			return;
		}

		RUNS.remove(run.owner);

		for (Entity rider : List.copyOf(ship.getPassengers())) {
			rider.stopRiding();
		}

		ShipRecord record = ShipRegistry.of(server, run.owner).orElse(null);

		if (record != null) {
			Ships.show(ship, record);
		}

		if (pilot != null) {
			TravelNetwork.send(pilot, none(run, record, pilot.level()));
		}
	}

	// ---------------------------------------------------------------- the Nav and Status tabs in flight (4.5, 4.7, 4.10)

	/**
	 * Why the owner's ship in the air may not go to {@code to}: by the autopilot ({@code jump} false,
	 * 4.5: a destination on the planet below is a hop that costs nothing) or by a jump to its orbit
	 * ({@code jump} true, 4.7: only from an orbit, to another system).
	 */
	static Refusal airRefusal(ServerPlayer owner, Destination to, boolean jump) {
		MinecraftServer server = owner.level().getServer();
		ShipRecord record = ShipRegistry.of(server, owner.getUUID()).orElse(null);
		ShipBody ship = record == null ? null : Ships.ship(server, record.owner());

		if (record == null || ship == null) {
			return Refusal.NOT_OWNER;
		}

		Planets.Planet system = systemOf(ship.level().dimension());
		ResourceKey<Level> level = TravelService.levelOf(owner, to);
		Planets.Planet target = level == null ? null : Planets.Planet.of(level);
		boolean hop = !jump && target != null && target == system;
		Refusal refusal = FlightRules.airRefusal(record, ShipBalance.travel().enabled, atHelm(record.owner()) && owner.getVehicle() == ship,
				TravelService.isUnlocked(owner, to), FlightRules.blockaded(to, ChapterFive.stage(owner)), !hop, ShipCombat.underFire(owner),
				CombatLock.inCombat(server, record, ship), ShipBalance.get());

		if (refusal != Refusal.NONE) {
			return refusal;
		}

		if (!jump) {
			Refusal destination = TravelService.destinationRefusal(owner, record, to);
			return destination == Refusal.UNAVAILABLE ? destination : Refusal.NONE;
		}

		if (system == null || !Planets.isOrbit(ship.level())) {
			return Refusal.NOT_READY;
		}

		if (target == null || Planets.orbitLevel(server, target) == null) {
			return Refusal.UNAVAILABLE;
		}

		return target == system ? Refusal.HERE : Refusal.NONE;
	}

	/**
	 * The Nav tab's "Land at …" in the air (4.5): the server takes the ship and runs the Milestone 4
	 * sequence from its Jump to the destination's pad.
	 */
	static Refusal handToAutopilot(ServerPlayer owner, Destination to) {
		Refusal refusal = airRefusal(owner, to, false);
		MinecraftServer server = owner.level().getServer();
		ShipRecord record = ShipRegistry.of(server, owner.getUUID()).orElse(null);
		ShipBody ship = record == null ? null : Ships.ship(server, record.owner());
		ResourceKey<Level> level = TravelService.levelOf(owner, to);
		Run run = RUNS.get(owner.getUUID());

		if (refusal != Refusal.NONE || record == null || ship == null || level == null || run == null) {
			return refusal != Refusal.NONE ? refusal : Refusal.UNAVAILABLE;
		}

		boolean hop = Planets.Planet.of(level) != null && Planets.Planet.of(level) == systemOf(ship.level().dimension());
		RUNS.remove(run.owner);
		dropCrossing(server, run);
		TravelNetwork.send(owner, none(run, record, owner.level()));
		TravelEffects.flight(ship, TravelEffects.FLIGHT_AUTOPILOT, 1f);
		Flights.launchFromAir(server, owner, record, ship, to, Pads.of(owner, to), level, hop);
		OldRepublicMod.LOGGER.info("{} hands the helm to the autopilot for {}", owner.getPlainTextName(), to.id());
		return Refusal.NONE;
	}

	/** The Nav tab's "Jump to … orbit" in an orbit (4.7): the Jump and Hyperspace phases, then the helm again in the other orbit. */
	static Refusal jumpToOrbit(ServerPlayer owner, Destination to) {
		Refusal refusal = airRefusal(owner, to, true);
		MinecraftServer server = owner.level().getServer();
		ShipRecord record = ShipRegistry.of(server, owner.getUUID()).orElse(null);
		ShipBody ship = record == null ? null : Ships.ship(server, record.owner());
		ResourceKey<Level> level = TravelService.levelOf(owner, to);
		Planets.Planet planet = level == null ? null : Planets.Planet.of(level);
		Run run = RUNS.get(owner.getUUID());

		if (refusal != Refusal.NONE || record == null || ship == null || planet == null || run == null) {
			return refusal != Refusal.NONE ? refusal : Refusal.UNAVAILABLE;
		}

		RUNS.remove(run.owner);
		dropCrossing(server, run);
		TravelNetwork.send(owner, none(run, record, owner.level()));
		TravelEffects.flight(ship, TravelEffects.FLIGHT_AUTOPILOT, 1f);
		Flights.jumpToOrbit(server, owner, record, ship, to, planet);
		OldRepublicMod.LOGGER.info("{} jumps to the orbit of {}", owner.getPlainTextName(), planet.id());
		return Refusal.NONE;
	}

	/** How the return to the last landing would be paid now (4.10). */
	static FlightRules.ReturnCost returnCost(ServerPlayer owner, ShipRecord record) {
		return FlightRules.returnCost(Clocks.day(owner.level().getServer()), TravelAttachments.get(owner).recallDay(), record.fuel(),
				TravelService.findCell(owner) != null, ShipBalance.travel());
	}

	/** Why the return to the last landing is refused now (4.10), or {@code NONE}. */
	static Refusal returnRefusal(ServerPlayer owner) {
		MinecraftServer server = owner.level().getServer();
		ShipRecord record = ShipRegistry.of(server, owner.getUUID()).orElse(null);
		ShipBody ship = record == null ? null : Ships.ship(server, record.owner());

		if (record == null || ship == null) {
			return Refusal.NOT_OWNER;
		}

		return FlightRules.returnRefusal(record, atHelm(record.owner()) && owner.getVehicle() == ship, returnCost(owner, record), ShipCombat.underFire(owner),
				CombatLock.inCombat(server, record, ship));
	}

	/**
	 * The Status tab's "Return to last landing" (4.10): the rescue run with everyone aboard, paid by
	 * the free daily recall when it is unused, else a cell from the tank, else one carried.
	 */
	static Refusal returnToLanding(ServerPlayer owner) {
		Refusal refusal = returnRefusal(owner);
		MinecraftServer server = owner.level().getServer();
		ShipRecord record = ShipRegistry.of(server, owner.getUUID()).orElse(null);
		ShipBody ship = record == null ? null : Ships.ship(server, record.owner());
		Run run = RUNS.get(owner.getUUID());

		if (refusal != Refusal.NONE || record == null || ship == null || run == null) {
			return refusal != Refusal.NONE ? refusal : Refusal.NOT_READY;
		}

		ShipRecord paid = switch (returnCost(owner, record)) {
			case FREE -> {
				TravelAttachments.set(owner, TravelAttachments.get(owner).withRecallDay(Clocks.day(server)));
				yield record;
			}
			case TANK -> Ships.update(server, record.owner(), r -> r.withFuel(Math.max(0, r.fuel() - 1))).orElse(record);
			case CARRIED -> {
				TravelService.takeCell(owner);
				yield record;
			}
			default -> null;
		};

		if (paid == null) {
			return Refusal.NO_FUEL;
		}

		rescue(server, run, paid, ship, false);
		return Refusal.NONE;
	}

	/** The planet a level belongs to: its own sky, or the system whose orbit it is; null elsewhere. */
	static Planets.@Nullable Planet systemOf(ResourceKey<Level> level) {
		Planets.Planet planet = Planets.Planet.of(level);
		return planet != null ? planet : Planets.orbitOf(level);
	}

	// ---------------------------------------------------------------- endings

	/**
	 * Ends a run with nobody landing the ship (4.10): the record is settled where the ship last
	 * landed and, with {@code restore}, the body goes back there (anyone still aboard with it). The
	 * owner, if online, leaves the helm.
	 */
	private static void release(MinecraftServer server, Run run, boolean restore) {
		RUNS.remove(run.owner);
		dropCrossing(server, run);
		ShipRecord record = Ships.update(server, run.owner, r -> ShipRules.recovered(r, ShipBalance.get())).orElse(null);

		if (record != null && restore) {
			Ships.restore(server, run.owner, true);
		}

		ServerPlayer owner = server.getPlayerList().getPlayer(run.owner);

		if (owner != null) {
			TravelNetwork.send(owner, none(run, record, owner.level()));
		}

		OldRepublicMod.LOGGER.info("The helm of {}'s ship is released", run.owner);
	}

	/**
	 * The body at the helm is gone (removed, or in another world than the run's): the ship goes back
	 * to its last landing spot, and its owner and crew who were put off in the air fall slowly.
	 */
	private static void lost(MinecraftServer server, Run run, @Nullable ShipRecord record) {
		release(server, run, true);

		if (record == null) {
			return;
		}

		List<UUID> aboard = new ArrayList<>(record.crew());
		aboard.add(record.owner());

		for (UUID id : aboard) {
			ServerPlayer player = server.getPlayerList().getPlayer(id);

			if (player != null && player.getVehicle() == null && !player.onGround() && player.level().dimension().equals(run.level)) {
				Ships.safeFall(player);
			}
		}
	}

	/** Stops a run without placing anything (a command replaces the ship): the record is settled where it last landed. */
	static void cancel(MinecraftServer server, UUID owner) {
		Run run = RUNS.get(owner);

		if (run != null) {
			release(server, run, false);
		}
	}

	/**
	 * A rider is leaving the server while their ship is at the helm (4.10): put off and set down
	 * beside the ship's last landing spot, so they are never saved aboard or in the air. When it is
	 * the pilot, the ship goes home at the next tick.
	 */
	static void riderLeaving(ServerPlayer player, ShipRecord record) {
		player.stopRiding();
		Ships.placeBesidePad(player, record, true);
	}

	static void clear() {
		RUNS.clear();
	}

	// ---------------------------------------------------------------- the pilot's status

	private static void enter(Run run, Stage stage) {
		run.stage = stage;
		run.ticks = 0;
	}

	private static void status(@Nullable ServerPlayer pilot, Run run, ShipRecord record) {
		if (pilot == null) {
			return;
		}

		FlightBalance b = FlightBalance.get();
		int helm = switch (run.stage) {
			case SPOOL -> FlightStatusPayload.HELM_SPOOL;
			case RISE -> FlightStatusPayload.HELM_RISE;
			case PILOTED -> FlightStatusPayload.HELM_PILOTED;
			case HOVER -> FlightStatusPayload.HELM_HOVER;
			case GLIDE, LAND, SETTLE -> FlightStatusPayload.HELM_LANDING;
			case TUMBLE -> FlightStatusPayload.HELM_TUMBLE;
		};
		MinecraftServer server = pilot.level().getServer();
		ShipBody ship = Ships.ship(server, run.owner);
		boolean orbit = ship != null && Planets.isOrbit(ship.level());
		double toEdge = ship != null && orbit ? FlightRules.edgeDistance(ship.getX(), ship.getZ(), b) : Double.MAX_VALUE;
		int edge = toEdge <= b.edgeWarning ? (int) Math.floor(Math.max(0, toEdge)) : -1;
		Planets.Planet sky = ship == null ? null : Planets.Planet.of(ship.level().dimension());
		int ceiling = sky != null && server.getLevel(Planets.orbit(sky)) != null ? b.orbitCeiling : -1;
		int cooldown = FlightRules.cooldownLeft(server.getTickCount() - run.lastTransition, b);
		TravelNetwork.send(pilot, new FlightStatusPayload(helm, run.boost.meter(), run.boost.running(), FlightRules.profile(b, record.upgrades(), orbit),
				run.landing.ordinal(), cooldown, edge, ceiling));
	}

	/** The status that tells the pilot's client the helm is released. */
	private static FlightStatusPayload none(Run run, @Nullable ShipRecord record, Level level) {
		FlightBalance b = FlightBalance.get();
		return new FlightStatusPayload(FlightStatusPayload.HELM_NONE, run.boost.meter(), false,
				FlightRules.profile(b, record == null ? 0 : record.upgrades(), Planets.isOrbit(level)), FlightRules.Landing.TOO_HIGH.ordinal(), 0, -1, -1);
	}

	/** The players riding {@code ship}. */
	static List<ServerPlayer> riders(ShipBody ship) {
		List<ServerPlayer> riders = new ArrayList<>();

		for (Entity passenger : ship.getPassengers()) {
			if (passenger instanceof ServerPlayer player) {
				riders.add(player);
			}
		}

		return riders;
	}

	/** Smoothstep: eases in and out over 0 to 1. */
	private static double smooth(double u) {
		return u * u * (3 - 2 * u);
	}

	/** The heading {@code u} of the way from {@code from} to {@code to}, the short way round. */
	private static float turn(float from, float to, double u) {
		return from + Mth.wrapDegrees(to - from) * (float) u;
	}

	private Helm() {
	}
}
