package com.oldrepublic.travel;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.planet.PlanetHubs;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.quest.QuestHooks;
import com.oldrepublic.quest.story.ChapterFive;
import com.oldrepublic.travel.network.FlightPhasePayload;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The flight sequence (spec MILESTONE_4.md 4.6, 4.8, 4.9): Spool, Climb, Jump, Hyperspace, Descent
 * and Settle, run by the server one tick at a time for every ship in flight. Nobody steers: the
 * body is moved along a fixed path and its clients interpolate.
 *
 * <p>Milestone 5 adds runs that start in the air, at the Jump (MILESTONE_5.md 4.5, 4.7, 5.4): the
 * autopilot taking over from the helm (a hop to the planet below costs nothing), a jump to another
 * orbit, which ends after its Hyperspace with the helm handed back, and the rescue, which takes a
 * ship home to its last landing spot with everyone aboard. During {@code BLOCKADE} a run to the
 * holdfast ends in Tyrvann's orbit the same way (6.5). The Hymn-Tuned Drive halves the Hyperspace
 * phase (11.2), and the owner may take the helm during a Descent (4.5).
 *
 * <p>What runs is kept in memory only. The record carries the trace a restart needs (its state and
 * {@code flight} phase), and {@link #recover} settles any record whose flight is not running: back
 * at the origin with nothing taken before the jump, at the destination after it (for a run from the
 * air the origin is the last landing spot, which the record's position still is). The jump takes
 * the fuel and wear and moves the ship with {@link TravelService#moveAcross}, under a chunk ticket
 * held on the destination since the run began. No reference to a body is kept across ticks: each
 * tick finds the current one by its UUID.
 */
final class Flights {
	/** What started a flight. */
	enum Kind {
		/** The owner launched from the Nav tab or a command; the riders fly. */
		LAUNCH,
		/** A recall to a beacon or pad terminal: nobody aboard, one cell paid, the normal wear. */
		RECALL,
		/** A recall of a DISABLED ship: no cost, it arrives with the jump minimum hull. */
		TOW,
		/**
		 * MILESTONE_5.md 4.10, 5.4: the ship goes home to its last landing spot from the air, everyone
		 * aboard, at no cost (the pilot left, the return to the last landing, or the disabled ship).
		 */
		RESCUE
	}

	/** One flight in progress. */
	static final class Active {
		final UUID owner;
		final Kind kind;
		final Destination from;
		final Destination to;
		final boolean instant;
		/** Where the run began: the pad of a take-off, the ship's spot in the air for a run from the air. */
		final GlobalPos origin;
		final ResourceKey<Level> destination;
		final List<UUID> riders = new ArrayList<>();
		final @Nullable CompletableFuture<BlockPos> hub;
		Pads.@Nullable Pad pad;
		Pads.@Nullable Pad landing;
		@Nullable GlobalPos ticket;
		boolean originTicket;
		boolean hasBody;
		int phase = ShipRules.PHASE_SPOOL;
		int ticks;
		Vec3 start = Vec3.ZERO;
		float yaw;
		/** Started in the air, at the Jump (MILESTONE_5.md 4.5, 4.7, 5.4). */
		boolean fromAir;
		/** A hop to the planet below the ship: no fuel and no wear (4.5). */
		boolean hop;
		/**
		 * Ends after its Hyperspace in {@link #destination}, an orbit, at {@link #arrival} facing
		 * {@link #arrivalYaw}, with the helm handed back (4.7; the blockade, 6.5).
		 */
		boolean toOrbit;
		Vec3 arrival = Vec3.ZERO;
		float arrivalYaw;
		/** The arrival is above (0, 0) because the planet's hub was not placed when the run began. */
		boolean provisionalArrival;
		/** The rescue of a disabled ship (5.4): it was patched to {@code flight.rescueHull} and its arrival ends the alarm. */
		boolean disabled;

		Active(UUID owner, Kind kind, Destination from, Destination to, boolean instant, GlobalPos origin, ResourceKey<Level> destination,
				Pads.@Nullable Pad pad, @Nullable CompletableFuture<BlockPos> hub, boolean hasBody) {
			this.owner = owner;
			this.kind = kind;
			this.from = from;
			this.to = to;
			this.instant = instant;
			this.origin = origin;
			this.destination = destination;
			this.pad = pad;
			this.hub = hub;
			this.hasBody = hasBody;
		}
	}

	/** Struts go up halfway through the Spool. */
	private static final double GEAR_UP_AT = 0.5;
	/** Nose-up pitch at the middle of the climb, in degrees. */
	private static final float CLIMB_PITCH = -6f;
	/** The origin's ticket radius while a recalled body takes off with nobody near. */
	private static final int ORIGIN_TICKET_RADIUS = 1;
	/** Records with a flight that is not running are settled this often. */
	private static final int RECOVER_INTERVAL = 200;

	private static final Map<UUID, Active> ACTIVE = new LinkedHashMap<>();

	// ---------------------------------------------------------------- queries

	static boolean isFlying(UUID owner) {
		return ACTIVE.containsKey(owner);
	}

	/** The phase to show on the owner's body: the running flight's, else {@link ShipRules#PHASE_GROUND}. */
	static int phaseOf(UUID owner) {
		Active flight = ACTIVE.get(owner);
		return flight == null ? ShipRules.PHASE_GROUND : flight.phase;
	}

	/** Whether {@code ship} is the body this flight moves (any other copy discards itself). */
	static boolean isBody(UUID owner, ShipBody ship) {
		Active flight = ACTIVE.get(owner);

		if (flight == null || !flight.hasBody) {
			return false;
		}

		ResourceKey<Level> expected = flight.phase >= ShipRules.PHASE_HYPERSPACE ? flight.destination : flight.origin.dimension();
		return ship.level().dimension().equals(expected);
	}

	/** Pads other flights are on their way to in {@code dimension} (they hold those bays). */
	static List<BlockPos> targets(ResourceKey<Level> dimension, UUID except) {
		List<BlockPos> targets = new ArrayList<>();

		for (Active flight : ACTIVE.values()) {
			Pads.Pad pad = flight.landing != null ? flight.landing : flight.pad;

			if (!flight.toOrbit && !flight.owner.equals(except) && pad != null && pad.pos().dimension().equals(dimension)) {
				targets.add(pad.pos().pos());
			}
		}

		return targets;
	}

	// ---------------------------------------------------------------- starting

	/**
	 * Starts a launch: the owner's current passengers fly. The caller checked everything
	 * ({@link TravelService#canLaunch}). With {@code blockade} (a run to the holdfast during
	 * {@code BLOCKADE}, MILESTONE_5.md 6.5) it ends in Tyrvann's orbit with the helm handed back.
	 */
	static void launch(MinecraftServer server, ShipRecord record, ShipBody ship, Destination to, Pads.@Nullable Pad pad,
			ResourceKey<Level> destination, boolean instant, boolean blockade) {
		ResourceKey<Level> target = blockade ? Planets.orbit(Planets.Planet.TYRVANN) : destination;
		Active flight = new Active(record.owner(), Kind.LAUNCH, fromOf(record), to, instant, record.pos(), target, pad, ensureHub(server, to), true);

		if (blockade) {
			toOrbit(server, flight, pad, ship.getYRot());
		}

		board(flight, ship);
		begin(server, flight, ship, ShipRules.PHASE_SPOOL);
	}

	/**
	 * The autopilot takes over in the air (MILESTONE_5.md 4.5): the run starts at the Jump from where
	 * the ship is, with its riders as at a launch; a {@code hop} to the planet below pays nothing.
	 * During {@code BLOCKADE} a run to the holdfast from another system ends in Tyrvann's orbit (6.5).
	 */
	static void launchFromAir(MinecraftServer server, ServerPlayer owner, ShipRecord record, ShipBody ship, Destination to, Pads.@Nullable Pad pad,
			ResourceKey<Level> destination, boolean hop) {
		if (!hop && FlightRules.blockadeArrival(to, ChapterFive.stage(owner)) && Planets.orbitLevel(server, Planets.Planet.TYRVANN) != null) {
			jumpToOrbit(server, owner, record, ship, to, Planets.Planet.TYRVANN);
			return;
		}

		Active flight = new Active(record.owner(), Kind.LAUNCH, destinationOf(ship.level().dimension()), to, false, here(ship), destination, pad,
				ensureHub(server, to), true);
		flight.fromAir = true;
		flight.hop = hop;
		board(flight, ship);
		begin(server, flight, ship, ShipRules.PHASE_JUMP);
	}

	/**
	 * A jump to {@code planet}'s orbit from an orbit (MILESTONE_5.md 4.7): the Jump and Hyperspace
	 * phases, then the ship arrives above the destination's pad divided by 8, facing the hub, with the
	 * helm handed back.
	 */
	static void jumpToOrbit(MinecraftServer server, ServerPlayer owner, ShipRecord record, ShipBody ship, Destination to, Planets.Planet planet) {
		Pads.Pad pad = Pads.of(owner, to);
		Active flight = new Active(record.owner(), Kind.LAUNCH, destinationOf(ship.level().dimension()), to, false, here(ship), Planets.orbit(planet), pad,
				ensureHub(server, to), true);
		flight.fromAir = true;
		toOrbit(server, flight, pad, ship.getYRot());
		board(flight, ship);
		begin(server, flight, ship, ShipRules.PHASE_JUMP);
	}

	/**
	 * The rescue (MILESTONE_5.md 4.10, 5.4): from the air, at the Jump, home to the ship's last
	 * landing spot (or home, when that world is gone) with everyone aboard, at no cost. A disabled
	 * ship is patched to {@code flight.rescueHull} now, so even a restart mid-run delivers it so.
	 */
	static void rescue(MinecraftServer server, ShipRecord record, ShipBody ship, boolean disabled) {
		GlobalPos home = server.getLevel(record.pos().dimension()) != null ? record.pos() : Pads.homeFallback(server, record.owner());
		Pads.Pad pad = new Pads.Pad(home, record.yaw(), false, null, -1, null);
		Active flight = new Active(record.owner(), Kind.RESCUE, destinationOf(ship.level().dimension()), destinationOf(home.dimension()), false, here(ship),
				home.dimension(), pad, null, true);
		flight.fromAir = true;
		flight.disabled = disabled;
		board(flight, ship);
		begin(server, flight, ship, ShipRules.PHASE_JUMP);
	}

	/** The ship's riders fly with it. */
	private static void board(Active flight, ShipBody ship) {
		for (Entity rider : ship.getPassengers()) {
			flight.riders.add(rider.getUUID());
		}
	}

	/** Where a ship in the air is: a run from the air begins there. */
	private static GlobalPos here(ShipBody ship) {
		return GlobalPos.of(ship.level().dimension(), ship.blockPosition());
	}

	/**
	 * Makes the run end in its destination orbit (MILESTONE_5.md 4.7): its arrival above the pad
	 * divided by 8 at {@code flight.jumpArrivalY}, facing the planet's hub (with no hub, keeping
	 * {@code yaw}); above (0, 0) while the destination's pad is not known (the arrival is looked at
	 * again at the Jump).
	 */
	private static void toOrbit(MinecraftServer server, Active flight, Pads.@Nullable Pad pad, float yaw) {
		FlightBalance b = FlightBalance.get();
		Planets.Planet planet = Planets.orbitOf(flight.destination);
		BlockPos hub = planet == null || planet == Planets.Planet.HOME ? null : PlanetHubs.hub(server, planet);
		Vec3 arrival = FlightRules.orbitArrival(pad != null ? Vec3.atBottomCenterOf(pad.pos().pos()) : null, b);
		double scale = Math.max(1, b.orbitScale);
		flight.toOrbit = true;
		flight.arrival = arrival;
		flight.provisionalArrival = pad == null;
		flight.arrivalYaw = hub == null ? yaw : Ships.yawAlong(hub.getX() / scale - arrival.x, hub.getZ() / scale - arrival.z);
	}

	/** Whether the owner's ship is on the autopilot's Descent with riders (the owner may take the helm, MILESTONE_5.md 4.5). */
	static boolean isDescending(UUID owner) {
		Active flight = ACTIVE.get(owner);
		return flight != null && flight.kind == Kind.LAUNCH && flight.hasBody && flight.phase == ShipRules.PHASE_DESCENT;
	}

	/**
	 * The owner took the helm during the Descent (MILESTONE_5.md 4.5): the run ends where the ship
	 * is and its riders are told; the caller sets the record. False when there is no Descent.
	 */
	static boolean abandon(MinecraftServer server, UUID owner) {
		Active flight = ACTIVE.get(owner);

		if (flight == null || flight.phase != ShipRules.PHASE_DESCENT) {
			return false;
		}

		end(server, flight);

		for (UUID id : flight.riders) {
			ServerPlayer rider = server.getPlayerList().getPlayer(id);

			if (rider != null) {
				TravelNetwork.send(rider, over(flight));
			}
		}

		return true;
	}

	/**
	 * Starts a recall (or a tow) to {@code pad}. A loaded body takes off with the usual effects; an
	 * unloaded one vanishes where it is and appears over the pad for the descent.
	 */
	static void recall(MinecraftServer server, ShipRecord record, Pads.Pad pad, boolean tow) {
		ShipBody ship = Ships.ship(server, record.owner());
		Destination to = destinationOf(pad.pos().dimension());
		Active flight = new Active(record.owner(), tow ? Kind.TOW : Kind.RECALL, fromOf(record), to, false, record.pos(), pad.pos().dimension(),
				pad, null, ship != null);
		begin(server, flight, ship, ShipRules.PHASE_SPOOL);

		if (ship == null) {
			flight.phase = ShipRules.PHASE_JUMP;
			flight.ticks = jumpTicks(flight);
		}
	}

	/**
	 * Starts a run at {@code phase}: the Spool on the ground, or the Jump for a run from the air
	 * (whose ship stays at its last landing spot in the record, MILESTONE_5.md 4.5).
	 */
	private static void begin(MinecraftServer server, Active flight, @Nullable ShipBody ship, int phase) {
		flight.phase = phase;
		flight.ticks = 0;
		ACTIVE.put(flight.owner, flight);
		ShipBalance.Travel t = ShipBalance.travel();
		holdDestination(server, flight, target(server, flight), t);

		if (ship != null) {
			flight.start = ship.position();
			flight.yaw = ship.getYRot();
			TravelTickets.hold(server, flight.origin, ORIGIN_TICKET_RADIUS);
			flight.originTicket = true;
		}

		Ships.update(server, flight.owner, r -> (flight.disabled ? FlightRules.rescued(r, FlightBalance.get(), ShipBalance.get()) : r)
				.withState(ShipState.LAUNCHING).withFlight(Optional.of(new ShipRecord.Flight(flight.from, flight.to, phase))));

		if (ship != null) {
			if (phase == ShipRules.PHASE_SPOOL) {
				TravelEffects.engine(ship, TravelEffects.ENGINE_SPOOL);
			} else {
				ship.setGear(false);
				TravelEffects.jump(ship, TravelEffects.JUMP_OUT);
			}

			tellRiders(server, flight, ship);
		}
	}

	/** Where the destination's ticket goes: the arrival in an orbit, the pad, or where a hub not placed yet will be. */
	private static GlobalPos target(MinecraftServer server, Active flight) {
		if (flight.toOrbit) {
			return GlobalPos.of(flight.destination, BlockPos.containing(flight.arrival));
		}

		return flight.pad != null ? flight.pad.pos() : provisional(server, flight).pos();
	}

	/** Starts placing the destination planet's hub when it is not there yet (3.5); it runs during the take-off. */
	private static @Nullable CompletableFuture<BlockPos> ensureHub(MinecraftServer server, Destination to) {
		Planets.Planet planet = to.planet();

		if (planet == null || planet == Planets.Planet.HOME || to == Destination.HUNTING_CAMP || PlanetHubs.hub(server, planet) != null) {
			return null;
		}

		return PlanetHubs.ensure(server, planet);
	}

	// ---------------------------------------------------------------- ticking

	/** Every server tick: each flight one step; every 200 ticks, records with a flight that is not running are settled. */
	static void tick(MinecraftServer server) {
		for (Iterator<Active> it = new ArrayList<>(ACTIVE.values()).iterator(); it.hasNext();) {
			Active flight = it.next();

			if (ACTIVE.get(flight.owner) != flight) {
				continue;
			}

			try {
				step(server, flight);
			} catch (RuntimeException e) {
				OldRepublicMod.LOGGER.error("The flight of {}'s ship failed; settling it", flight.owner, e);
				lost(server, flight);
			}
		}

		if (server.getTickCount() % RECOVER_INTERVAL == 0) {
			recover(server);
		}
	}

	private static void step(MinecraftServer server, Active flight) {
		ShipRecord record = ShipRegistry.of(server, flight.owner).orElse(null);

		if (record == null) {
			end(server, flight);
			return;
		}

		ShipBody ship = flight.hasBody ? body(server, flight) : null;

		if (flight.hasBody && ship == null) {
			lost(server, flight);
			return;
		}

		flight.ticks++;
		ShipBalance.Travel t = ShipBalance.travel();

		switch (flight.phase) {
			case ShipRules.PHASE_SPOOL -> spool(server, flight, ship, t);
			case ShipRules.PHASE_CLIMB -> climb(server, flight, ship, t);
			case ShipRules.PHASE_JUMP -> jump(server, flight, record, ship, t);
			case ShipRules.PHASE_HYPERSPACE -> hyperspace(server, flight, record, ship, t);
			case ShipRules.PHASE_DESCENT -> descent(server, flight, ship, t);
			case ShipRules.PHASE_SETTLE -> settle(server, flight, ship, t);
			default -> end(server, flight);
		}
	}

	private static void spool(MinecraftServer server, Active flight, @Nullable ShipBody ship, ShipBalance.Travel t) {
		int length = length(flight, t.spoolTicks);

		if (ship != null && flight.ticks >= length * GEAR_UP_AT) {
			ship.setGear(false);
		}

		if (flight.ticks >= length) {
			enter(server, flight, ship, ShipRules.PHASE_CLIMB);

			if (ship != null) {
				TravelEffects.engine(ship, TravelEffects.ENGINE_CLIMB);
			}
		}
	}

	private static void climb(MinecraftServer server, Active flight, @Nullable ShipBody ship, ShipBalance.Travel t) {
		int length = length(flight, t.climbTicks);
		double progress = Math.min(1.0, flight.ticks / (double) length);

		if (ship != null) {
			double y = flight.start.y + ShipRules.climbHeight(progress, climbHeight(flight, t));
			ship.flyTo(new Vec3(flight.start.x, y, flight.start.z), flight.yaw, CLIMB_PITCH * (float) Math.sin(progress * Math.PI));
		}

		if (flight.ticks >= length) {
			enter(server, flight, ship, ShipRules.PHASE_JUMP);

			if (ship != null) {
				TravelEffects.jump(ship, TravelEffects.JUMP_OUT);
			}
		}
	}

	/**
	 * The top of the climb: once the jump's ticks are done and the destination is loaded (or after
	 * {@code hyperspaceMaxWait} more ticks), the fuel and wear are taken and the ship moves across.
	 */
	private static void jump(MinecraftServer server, Active flight, ShipRecord record, @Nullable ShipBody ship, ShipBalance.Travel t) {
		if (flight.toOrbit) {
			jumpIntoOrbit(server, flight, record, ship, t);
			return;
		}

		resolvePad(server, flight, t);
		ServerLevel destination = server.getLevel(flight.destination);

		if (destination == null) {
			OldRepublicMod.LOGGER.warn("The destination of {}'s ship is gone; it lands back at its origin", flight.owner);
			lost(server, flight);
			return;
		}

		Pads.Pad pad = flight.pad != null ? flight.pad : provisional(server, flight);
		boolean ready = flight.pad != null && TravelTickets.ready(destination, pad.pos().pos(), 1);
		int length = jumpTicks(flight);

		if (flight.ticks < length || !ready && flight.ticks < length + t.hyperspaceMaxWait) {
			return;
		}

		Pads.Pad known = Pads.surfaceOf(server, pad);
		Vec3 above = Vec3.atBottomCenterOf(known.pos().pos()).add(0, climbHeight(flight, t), 0);
		ShipRecord paid = paid(flight, record);
		UUID body = record.body();

		if (ship != null && (flight.kind == Kind.LAUNCH || flight.kind == Kind.RESCUE)) {
			Entity moved = TravelService.moveAcross(ship, destination, above, known.yaw());

			if (moved.level() != destination || !(moved instanceof ShipBody)) {
				OldRepublicMod.LOGGER.warn("The jump of {}'s ship did not happen; it lands back at its origin", flight.owner);
				lost(server, flight);
				return;
			}

			ship = (ShipBody) moved;
		} else {
			if (ship != null) {
				Ships.discard(ship);
				ship = null;
			}

			body = UUID.randomUUID();
			flight.hasBody = false;
		}

		UUID newBody = body;
		Ships.update(server, flight.owner, r -> paid.withBody(newBody).withState(ShipState.IN_TRANSIT).withPos(known.pos(), known.yaw())
				.withFlight(Optional.of(new ShipRecord.Flight(flight.from, flight.to, ShipRules.PHASE_HYPERSPACE))));
		releaseOrigin(server, flight);
		flight.pad = known;
		flight.yaw = known.yaw();
		enter(server, flight, ship, ShipRules.PHASE_HYPERSPACE);

		if (ship != null) {
			flight.start = ship.position();

			for (ServerPlayer rider : players(ship)) {
				TravelEffects.hyperspace(rider, 0f);
			}
		}
	}

	/**
	 * What the jump takes (4.6, MILESTONE_5.md 4.5, 5.4): a cell and the wear for a launch (nothing
	 * for a hop from the air), the normal wear for a recall, nothing for a tow but the jump minimum
	 * hull, and nothing for the rescue.
	 */
	private static ShipRecord paid(Active flight, ShipRecord record) {
		ShipBalance b = ShipBalance.get();

		return switch (flight.kind) {
			case LAUNCH -> flight.fromAir ? FlightRules.afterAirJump(record, flight.hop, b) : ShipRules.afterJump(record, b);
			case RECALL, TOW -> ShipRules.afterRecall(record, flight.kind == Kind.TOW, b);
			case RESCUE -> record;
		};
	}

	/**
	 * The jump of a run that ends in an orbit (MILESTONE_5.md 4.7, 6.5): once its ticks are done and
	 * the arrival is loaded (or after {@code hyperspaceMaxWait} more ticks), the cell and wear are
	 * taken and the ship and its riders move to the arrival. The record keeps its last landing spot.
	 */
	private static void jumpIntoOrbit(MinecraftServer server, Active flight, ShipRecord record, @Nullable ShipBody ship, ShipBalance.Travel t) {
		ServerLevel orbit = server.getLevel(flight.destination);

		if (orbit == null || ship == null) {
			OldRepublicMod.LOGGER.warn("The orbit {}'s ship was jumping to is gone; it goes back to its last landing", flight.owner);
			lost(server, flight);
			return;
		}

		Planets.Planet planet = Planets.orbitOf(flight.destination);

		if (flight.provisionalArrival && planet != null && PlanetHubs.hub(server, planet) != null) {
			// The hub was placed during the take-off: the arrival moves above its pad.
			toOrbit(server, flight, Pads.hubPad(server, planet, flight.owner), flight.arrivalYaw);
			holdDestination(server, flight, target(server, flight), t);
		}

		boolean ready = TravelTickets.ready(orbit, BlockPos.containing(flight.arrival), 1);
		int length = jumpTicks(flight);

		if (flight.ticks < length || !ready && flight.ticks < length + t.hyperspaceMaxWait) {
			return;
		}

		ShipRecord paid = paid(flight, record);
		// Clear of the orbit's rock, inside the arrivals' band (the HUD's height follows it).
		flight.arrival = Helm.clearArrival(orbit, ship, flight.owner, flight.arrival, Double.POSITIVE_INFINITY, FlightRules.arrivalRadius(FlightBalance.get()));
		Entity moved = TravelService.moveAcross(ship, orbit, flight.arrival, flight.arrivalYaw);

		if (moved.level() != orbit || !(moved instanceof ShipBody body)) {
			OldRepublicMod.LOGGER.warn("The jump of {}'s ship into orbit did not happen; it goes back to its last landing", flight.owner);
			lost(server, flight);
			return;
		}

		Ships.update(server, flight.owner, r -> paid.withState(ShipState.IN_TRANSIT)
				.withFlight(Optional.of(new ShipRecord.Flight(flight.from, flight.to, ShipRules.PHASE_HYPERSPACE))));
		releaseOrigin(server, flight);
		flight.yaw = flight.arrivalYaw;
		enter(server, flight, body, ShipRules.PHASE_HYPERSPACE);

		for (ServerPlayer rider : players(body)) {
			TravelEffects.hyperspace(rider, 0f);
		}
	}

	/**
	 * The end of a run to an orbit (MILESTONE_5.md 4.7, 6.5): out of hyperspace above the arrival,
	 * no Descent; the helm goes back to the owner and the story hears the ship arrived by jump.
	 */
	private static void arriveInOrbit(MinecraftServer server, Active flight, ShipBody ship) {
		end(server, flight);

		for (ServerPlayer rider : players(ship)) {
			TravelEffects.hyperspace(rider, 1f);
			TravelNetwork.send(rider, over(flight));
		}

		TravelEffects.jump(ship, TravelEffects.JUMP_IN);
		Helm.resume(server, flight.owner, ship);
		ServerPlayer owner = server.getPlayerList().getPlayer(flight.owner);
		Planets.Planet planet = Planets.orbitOf(flight.destination);

		if (owner != null && planet != null) {
			QuestHooks.onOrbitEntered(owner, planet, true);
		}
	}

	/**
	 * Waits for the destination: the hub's pad once it is placed, and its chunks loaded and
	 * ticking. A recalled ship's new body is placed over the pad when it is ready. After
	 * {@code hyperspaceMaxWait} the ship goes down over the pad's column regardless. A run to an
	 * orbit ends here.
	 */
	private static void hyperspace(MinecraftServer server, Active flight, ShipRecord record, @Nullable ShipBody ship, ShipBalance.Travel t) {
		if (flight.toOrbit) {
			if (ship != null && flight.ticks >= hyperspaceTicks(flight, record, t)) {
				arriveInOrbit(server, flight, ship);
			}

			return;
		}

		ServerLevel destination = server.getLevel(flight.destination);

		if (destination == null) {
			lost(server, flight);
			return;
		}

		Pads.Pad before = flight.pad;
		resolvePad(server, flight, t);
		Pads.Pad pad = flight.pad != null ? flight.pad : provisional(server, flight);
		boolean ready = flight.pad != null && TravelTickets.ready(destination, pad.pos().pos(), 1);
		Vec3 above = Vec3.atBottomCenterOf(Pads.surfaceOf(server, pad).pos().pos()).add(0, climbHeight(flight, t), 0);

		if (before != flight.pad) {
			// The hub was placed during the flight: its pad is the destination a restart would use.
			Ships.update(server, flight.owner, r -> r.withPos(pad.pos(), pad.yaw()));
		}

		if (ship != null && ready && ship.position().subtract(above).horizontalDistanceSqr() > 0.25) {
			// Over the pad's column before the descent, out of sight in the hyperspace overlay.
			ship.flyTo(above, pad.yaw(), 0f);
			flight.yaw = pad.yaw();
		}

		if (!flight.hasBody && ready) {
			ShipRecord placed = record.withPos(pad.pos(), pad.yaw());
			ShipRegistry.put(server, placed);
			ship = Ships.spawn(destination, placed, above);
			flight.hasBody = ship != null;
		}

		int length = hyperspaceTicks(flight, record, t);
		boolean waitedLongest = flight.ticks >= length + t.hyperspaceMaxWait;

		if (flight.ticks < length || !ready && !waitedLongest) {
			return;
		}

		if (ship == null) {
			// Nothing to fly down (the destination never loaded for a recalled body): it is restored at the pad.
			Ships.update(server, flight.owner, r -> ShipRules.recovered(r.withPos(Pads.surfaceOf(server, pad).pos(), pad.yaw()), ShipBalance.get()));
			end(server, flight);
			Ships.restore(server, flight.owner, false);
			return;
		}

		Pads.Pad landing = ready ? Pads.landing(destination, pad, ship) : Pads.surfaceOf(server, pad);
		flight.landing = landing;
		flight.yaw = landing.yaw();
		holdDestination(server, flight, landing.pos(), t);
		ship.flyTo(Vec3.atBottomCenterOf(landing.pos().pos()).add(0, climbHeight(flight, t), 0), landing.yaw(), 0f);
		flight.start = ship.position();
		Ships.update(server, flight.owner, r -> r.withState(ShipState.LANDING).withPos(landing.pos(), landing.yaw())
				.withFlight(Optional.of(new ShipRecord.Flight(flight.from, flight.to, ShipRules.PHASE_DESCENT))));
		enter(server, flight, ship, ShipRules.PHASE_DESCENT);

		for (ServerPlayer rider : players(ship)) {
			TravelEffects.hyperspace(rider, 1f);
		}

		TravelEffects.jump(ship, TravelEffects.JUMP_IN);
		TravelEffects.engine(ship, TravelEffects.ENGINE_DESCENT);
	}

	private static void descent(MinecraftServer server, Active flight, @Nullable ShipBody ship, ShipBalance.Travel t) {
		Pads.Pad landing = flight.landing;

		if (ship == null || landing == null) {
			lost(server, flight);
			return;
		}

		int length = length(flight, t.descentTicks);
		double progress = Math.min(1.0, flight.ticks / (double) length);
		Vec3 pad = Vec3.atBottomCenterOf(landing.pos().pos());
		double height = ShipRules.descentHeight(progress, climbHeight(flight, t));
		ship.flyTo(new Vec3(pad.x, pad.y + height, pad.z), flight.yaw, 0f);

		if (height <= ShipRules.STRUT_HEIGHT) {
			ship.setGear(true);
		}

		if (flight.ticks >= length) {
			ship.flyTo(pad, flight.yaw, 0f);
			ship.setGear(true);
			landed(server, flight, ship, landing);
		}
	}

	/**
	 * Touchdown: the state as at Settle, the pad remembered, the story told; the riders stay locked in
	 * until the Settle ends. A disabled ship's rescue arrives (MILESTONE_5.md 5.4).
	 */
	private static void landed(MinecraftServer server, Active flight, ShipBody ship, Pads.Pad landing) {
		ShipBalance b = ShipBalance.get();
		Ships.update(server, flight.owner, r -> ShipRules.recovered(r.withPos(landing.pos(), landing.yaw()), b));
		Pads.landed(server, flight.owner);

		if (flight.disabled) {
			TravelEffects.disabled(ship, TravelEffects.DISABLED_RESCUED);
		}
		ServerPlayer owner = server.getPlayerList().getPlayer(flight.owner);

		if (owner != null) {
			TravelAttachments.set(owner, TravelAttachments.get(owner).withLastPad(Optional.of(landing.pos())));
		}

		enter(server, flight, ship, ShipRules.PHASE_SETTLE);
		TravelEffects.engine(ship, TravelEffects.ENGINE_SETTLE);
		Planets.Planet planet = Planets.Planet.of(landing.pos().dimension());

		if (planet != null) {
			for (ServerPlayer rider : players(ship)) {
				QuestHooks.onPlanetArrived(rider, planet, TravelVisits.visit(rider, planet));
			}
		}
	}

	private static void settle(MinecraftServer server, Active flight, @Nullable ShipBody ship, ShipBalance.Travel t) {
		if (flight.ticks < length(flight, t.settleTicks)) {
			return;
		}

		end(server, flight);

		if (ship != null) {
			for (ServerPlayer rider : players(ship)) {
				TravelNetwork.send(rider, over(flight));
			}

			for (Entity rider : List.copyOf(ship.getPassengers())) {
				rider.stopRiding();
			}

			Ships.show(ship, ShipRegistry.of(server, flight.owner).orElseThrow());
		}
	}

	// ---------------------------------------------------------------- endings

	/**
	 * The body was lost mid-flight (removed, or a jump that did not happen): the record is settled
	 * at the origin before the jump or at the destination after it, and a new body is placed there.
	 * Riders put off in the air get slow falling.
	 */
	static void lost(MinecraftServer server, Active flight) {
		ShipBalance b = ShipBalance.get();
		Ships.update(server, flight.owner, r -> ShipRules.recovered(r, b));
		ShipBody ship = flight.hasBody ? body(server, flight) : null;
		end(server, flight);

		for (UUID id : flight.riders) {
			ServerPlayer rider = server.getPlayerList().getPlayer(id);

			if (rider != null) {
				TravelNetwork.send(rider, over(flight));

				if (rider.getVehicle() == null) {
					Ships.safeFall(rider);
				}
			}
		}

		if (ship != null) {
			Ships.discard(ship);
		}

		Ships.restore(server, flight.owner, false);
	}

	/** Stops a flight without placing anything (a command replaces the ship): the record is settled where it is. */
	static void cancel(MinecraftServer server, UUID owner) {
		Active flight = ACTIVE.get(owner);

		if (flight != null) {
			Ships.update(server, owner, r -> ShipRules.recovered(r, ShipBalance.get()));
			end(server, flight);
		}
	}

	private static void end(MinecraftServer server, Active flight) {
		ACTIVE.remove(flight.owner);
		releaseOrigin(server, flight);

		if (flight.ticket != null) {
			TravelTickets.release(server, flight.ticket, ShipBalance.travel().chunkTicketRadius, ShipBalance.travel().ticketLingerTicks);
			flight.ticket = null;
		}
	}

	/**
	 * Settles every record whose flight is not running (a restart, 4.9): before the jump it stays at
	 * its origin with nothing taken (the record's position was never moved); after it, at the
	 * destination pad recorded at the jump. A record left in a flying state with nobody at the helm
	 * (MILESTONE_5.md 4.10) belongs at its last landing spot, which its position still is. Its state
	 * is then set as at Settle, and a body is put on that pad when none is there once its chunk loads
	 * (the one caught in the air moves there itself, or discards itself if a new one was placed
	 * first).
	 */
	static void recover(MinecraftServer server) {
		ShipBalance b = ShipBalance.get();

		for (ShipRecord record : List.copyOf(ShipRegistry.snapshot(server).all())) {
			UUID owner = record.owner();

			if (record.flight().isPresent() && !ACTIVE.containsKey(owner)) {
				OldRepublicMod.LOGGER.info("Settling the ship of {} after an interrupted flight ({})", owner, ShipRules.recovery(record));
			} else if (record.flight().isEmpty() && record.state().flying() && !Helm.holds(owner)) {
				OldRepublicMod.LOGGER.info("Settling the ship of {}, {} with nobody at the helm, at its last landing spot", owner, record.state());
			} else {
				continue;
			}

			Ships.update(server, owner, r -> ShipRules.recovered(r, b));
			Ships.restore(server, owner, true);
		}
	}

	static void clear() {
		ACTIVE.clear();
	}

	// ---------------------------------------------------------------- riders leaving

	/**
	 * A rider is leaving the server mid-flight (4.9, 12.2): put off and set down beside the pad the
	 * ship last stood on (the record's position before the jump, the destination's pad after it; a
	 * run to an orbit keeps the last landing spot, MILESTONE_5.md 4.10), so they are never saved aboard
	 * or in the air. Away from that spot's world they are marked to be moved there at their next join.
	 */
	static void riderLeaving(ServerPlayer player, ShipBody ship) {
		MinecraftServer server = player.level().getServer();
		ShipRecord record = ShipRegistry.byBody(server, ship.getUUID()).orElse(null);
		Active flight = record == null ? null : ACTIVE.get(record.owner());
		player.stopRiding();

		if (record == null || flight == null) {
			return;
		}

		Pads.Pad target = flight.landing != null ? flight.landing : flight.pad;
		boolean arrived = flight.phase >= ShipRules.PHASE_HYPERSPACE && !flight.toOrbit && target != null;
		Ships.placeBesidePad(player, arrived ? record.withPos(Pads.surfaceOf(server, target).pos(), flight.yaw) : record, true);
		flight.riders.remove(player.getUUID());
	}

	// ---------------------------------------------------------------- helpers

	/** The body of a running flight, in the world it should be in. */
	private static @Nullable ShipBody body(MinecraftServer server, Active flight) {
		ShipBody ship = Ships.ship(server, flight.owner);
		return ship != null && isBody(flight.owner, ship) ? ship : null;
	}

	/** Enters a phase: resets its clock, records it for a restart, tells the riders. */
	private static void enter(MinecraftServer server, Active flight, @Nullable ShipBody ship, int phase) {
		flight.phase = phase;
		flight.ticks = 0;

		if (ship != null) {
			flight.start = ship.position();
			Ships.show(ship, ShipRegistry.of(server, flight.owner).orElseThrow());
			tellRiders(server, flight, ship);
		}

		if (phase != ShipRules.PHASE_SETTLE) {
			ShipRegistry.of(server, flight.owner).filter(r -> r.flight().isPresent() && r.flight().get().phase() != phase)
					.ifPresent(r -> ShipRegistry.put(server, r.withFlight(Optional.of(new ShipRecord.Flight(flight.from, flight.to, phase)))));
		}
	}

	private static void tellRiders(MinecraftServer server, Active flight, ShipBody ship) {
		ShipRecord record = ShipRegistry.of(server, flight.owner).orElse(null);

		if (record == null) {
			return;
		}

		ShipBalance.Travel t = ShipBalance.travel();
		int length = switch (flight.phase) {
			case ShipRules.PHASE_SPOOL -> length(flight, t.spoolTicks);
			case ShipRules.PHASE_CLIMB -> length(flight, t.climbTicks);
			case ShipRules.PHASE_JUMP -> jumpTicks(flight);
			case ShipRules.PHASE_HYPERSPACE -> hyperspaceTicks(flight, record, t);
			case ShipRules.PHASE_DESCENT -> length(flight, t.descentTicks);
			default -> length(flight, t.settleTicks);
		};
		Pads.Pad target = flight.landing != null ? flight.landing : flight.pad;
		int padY;

		if (flight.toOrbit && flight.phase >= ShipRules.PHASE_HYPERSPACE) {
			padY = (int) Math.floor(flight.arrival.y);
		} else {
			padY = flight.phase >= ShipRules.PHASE_HYPERSPACE && target != null ? target.pos().pos().getY() : flight.origin.pos().getY();
		}

		FlightPhasePayload payload = new FlightPhasePayload(flight.phase, length, flight.to.ordinal(), padY, record.hull(), record.fuel(),
				ShipRules.tankSize(record, ShipBalance.get()));

		for (ServerPlayer rider : players(ship)) {
			TravelNetwork.send(rider, payload);
		}
	}

	private static FlightPhasePayload over(Active flight) {
		return new FlightPhasePayload(FlightPhasePayload.OVER, 0, flight.to.ordinal(), 0, 0, 0, 0);
	}

	private static List<ServerPlayer> players(ShipBody ship) {
		List<ServerPlayer> players = new ArrayList<>();

		for (Entity passenger : ship.getPassengers()) {
			if (passenger instanceof ServerPlayer player) {
				players.add(player);
			}
		}

		return players;
	}

	/** Learns the hub's pad once the hub is placed; a camp or home pad is known from the start. */
	private static void resolvePad(MinecraftServer server, Active flight, ShipBalance.Travel t) {
		Planets.Planet planet = flight.to.planet();

		if (flight.pad != null || planet == null || flight.to == Destination.HUNTING_CAMP) {
			return;
		}

		if (flight.hub == null || flight.hub.isDone()) {
			Pads.Pad pad = Pads.hubPad(server, planet, flight.owner);

			if (pad != null) {
				flight.pad = pad;
				holdDestination(server, flight, pad.pos(), t);
			}
		}
	}

	/** Where a hub that is not placed yet will be: the planet's centre, its height found at landing. */
	private static Pads.Pad provisional(MinecraftServer server, Active flight) {
		ServerLevel level = server.getLevel(flight.destination);
		int y = level == null ? 64 : level.getSeaLevel();
		return new Pads.Pad(GlobalPos.of(flight.destination, new BlockPos(0, y, 0)), 0f, true, flight.to.planet(), -1, BlockPos.ZERO);
	}

	/** Moves the destination ticket to {@code pos} (radius {@code travel.chunkTicketRadius}). */
	private static void holdDestination(MinecraftServer server, Active flight, GlobalPos pos, ShipBalance.Travel t) {
		if (pos.equals(flight.ticket)) {
			return;
		}

		TravelTickets.hold(server, pos, t.chunkTicketRadius);

		if (flight.ticket != null) {
			TravelTickets.release(server, flight.ticket, t.chunkTicketRadius, 0);
		}

		flight.ticket = pos;
	}

	private static void releaseOrigin(MinecraftServer server, Active flight) {
		if (flight.originTicket) {
			TravelTickets.release(server, flight.origin, ORIGIN_TICKET_RADIUS, 0);
			flight.originTicket = false;
		}
	}

	/** A phase's length: one tick for the command's {@code instant} flight. */
	private static int length(Active flight, int ticks) {
		return flight.instant ? 1 : Math.max(1, ticks);
	}

	private static int jumpTicks(Active flight) {
		return length(flight, ShipBalance.travel().jumpTicks);
	}

	/** The Hyperspace phase's length: half as long with the Hymn-Tuned Drive (MILESTONE_5.md 11.2). */
	private static int hyperspaceTicks(Active flight, ShipRecord record, ShipBalance.Travel t) {
		return length(flight, FlightRules.hyperspaceTicks(record, t.hyperspaceTicks, FlightBalance.get()));
	}

	/** The climb's height: none for an instant flight. */
	private static double climbHeight(Active flight, ShipBalance.Travel t) {
		return flight.instant ? 0 : Math.max(0, t.climbHeight);
	}

	/** The destination a ship standing in {@code dimension} counts as leaving from (the Flight record's {@code from}). */
	private static Destination fromOf(ShipRecord record) {
		return destinationOf(record.pos().dimension());
	}

	/** A planet's hub destination ({@link Destination#hubOf}, MILESTONE_5.md 3.1), an orbit's as its planet's; home for anywhere else. */
	private static Destination destinationOf(ResourceKey<Level> dimension) {
		Planets.Planet planet = Helm.systemOf(dimension);
		Destination hub = planet == null ? null : Destination.hubOf(planet);
		return hub != null ? hub : Destination.HOME;
	}

	private Flights() {
	}
}
