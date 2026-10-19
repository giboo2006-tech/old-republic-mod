package com.oldrepublic.travel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.quest.QuestHooks;
import com.oldrepublic.travel.TravelService.Refusal;
import com.oldrepublic.travel.network.FlightStatusPayload;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The helm on the server (spec MILESTONE_5.md 4.2, 4.3): the owner in the pilot seat of a repaired
 * ship at rest asks for it (the Helm key, or the ship screen's button); the ship runs the Milestone 4
 * Spool (the ramp closes, the struts come up), rises {@code flight.helmRiseBlocks} under server
 * control and is then handed to the pilot: state {@code FLYING} and the synced piloted flag, so the
 * pilot's client flies it ({@link ShipBody}, 4.1). Meanwhile the server keeps the boost meter and
 * tells the pilot the numbers their ship flies by ({@code flight_status}).
 *
 * <p>What runs is kept in memory only. The record's position stays the ship's last landing spot, so
 * a run that ends in the air (the pilot off the ship or off the server, the body lost) settles the
 * ship there and its body goes back to it (4.10); after a restart {@link Flights#recover} does the
 * same for a record left flying with no run.
 */
final class Helm {
	/** The steps of taking and holding the helm. */
	private enum Stage {
		/** The Milestone 4 Spool, on the ground. */
		SPOOL,
		/** Up under server control. */
		RISE,
		/** Flown by hand. */
		PILOTED
	}

	/** One owner at the helm of their ship. */
	private static final class Run {
		final UUID owner;
		final ResourceKey<Level> level;
		final Vec3 start;
		final float yaw;
		Stage stage = Stage.SPOOL;
		int ticks;
		/** The boost meter, 0 to 1, whether Sprint is held, and whether the boost runs (4.3). */
		float boost = 1f;
		boolean boostHeld;
		boolean boosting;

		Run(UUID owner, ResourceKey<Level> level, Vec3 start, float yaw) {
			this.owner = owner;
			this.level = level;
			this.start = start;
			this.yaw = yaw;
		}
	}

	/** Struts go up halfway through the Spool, as on the autopilot. */
	private static final double GEAR_UP_AT = 0.5;
	/** The pilot's {@code flight_status} is refreshed this often while they fly (ticks). */
	private static final int STATUS_INTERVAL = 10;

	private static final Map<UUID, Run> RUNS = new LinkedHashMap<>();

	// ---------------------------------------------------------------- queries

	/** Whether the owner is taking or holding the helm. */
	static boolean holds(UUID owner) {
		return RUNS.containsKey(owner);
	}

	/** Whether the owner flies their ship by hand (the body's piloted flag). */
	static boolean isPiloted(UUID owner) {
		Run run = RUNS.get(owner);
		return run != null && run.stage == Stage.PILOTED;
	}

	/** The phase the owner's body shows: the Spool's, the climb's for the rise, {@link ShipRules#PHASE_HELM} once flown by hand. */
	static int phaseOf(UUID owner) {
		Run run = RUNS.get(owner);

		if (run == null) {
			return ShipRules.PHASE_GROUND;
		}

		return switch (run.stage) {
			case SPOOL -> ShipRules.PHASE_SPOOL;
			case RISE -> ShipRules.PHASE_CLIMB;
			case PILOTED -> ShipRules.PHASE_HELM;
		};
	}

	/** Whether {@code ship} is the body at the owner's helm (any other copy discards itself). */
	static boolean isBody(UUID owner, ShipBody ship) {
		Run run = RUNS.get(owner);
		return run != null && ship.level().dimension().equals(run.level);
	}

	// ---------------------------------------------------------------- taking the helm

	/** Takes the helm for {@code player} (4.2), or says why not. */
	static Refusal take(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		ShipRecord record = Ships.ridden(player).filter(r -> r.owner().equals(player.getUUID())).orElse(null);
		ShipBody ship = record == null ? null : Ships.ship(server, record.owner());
		boolean busy = record != null && Ships.busy(record);
		boolean inCombat = record != null && CombatLock.inCombat(server, record, ship);
		Refusal refusal = FlightRules.helmRefusal(record, FlightBalance.get().enabled, TravelAttachments.get(player).helm(), busy, inCombat);

		if (refusal != Refusal.NONE) {
			return refusal;
		}

		if (record == null || ship == null) {
			return Refusal.NOT_READY;
		}

		Run run = new Run(record.owner(), ship.level().dimension(), ship.position(), ship.getYRot());
		RUNS.put(run.owner, run);
		ShipRecord launching = Ships.update(server, run.owner, r -> r.withState(ShipState.LAUNCHING)).orElse(record);
		TravelEffects.engine(ship, TravelEffects.ENGINE_SPOOL);
		status(player, run, launching);
		OldRepublicMod.LOGGER.info("{} takes the helm at {}", player.getPlainTextName(), ship.blockPosition().toShortString());
		return Refusal.NONE;
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
		ServerPlayer pilot = server.getPlayerList().getPlayer(run.owner);

		if (record == null || ship == null || !isBody(run.owner, ship) || pilot == null || pilot.getVehicle() != ship) {
			// The pilot left the seat or the server, or the body is gone (4.10).
			release(server, run, true);
			return;
		}

		run.ticks++;
		FlightBalance b = FlightBalance.get();

		switch (run.stage) {
			case SPOOL -> spool(run, ship, record, pilot, b);
			case RISE -> rise(server, run, ship, pilot, b);
			case PILOTED -> fly(run, record, pilot, b);
		}
	}

	/** The Milestone 4 Spool: the ramp closes and the struts come up halfway through. */
	private static void spool(Run run, ShipBody ship, ShipRecord record, ServerPlayer pilot, FlightBalance b) {
		int length = Math.max(1, b.helmSpoolTicks);

		if (run.ticks >= length * GEAR_UP_AT) {
			ship.setGear(false);
		}

		if (run.ticks >= length) {
			run.stage = Stage.RISE;
			run.ticks = 0;
			Ships.show(ship, record);
			TravelEffects.engine(ship, TravelEffects.ENGINE_CLIMB);
			status(pilot, run, record);
		}
	}

	/** Up {@code flight.helmRiseBlocks} blocks, easing in and out, then the hand-over. */
	private static void rise(MinecraftServer server, Run run, ShipBody ship, ServerPlayer pilot, FlightBalance b) {
		int length = Math.max(1, b.helmRiseTicks);
		double u = Math.min(1.0, run.ticks / (double) length);
		ship.flyTo(run.start.add(0, b.helmRiseBlocks * u * u * (3 - 2 * u), 0), run.yaw, 0f);

		if (run.ticks >= length) {
			// The hand-over (4.2): the status (with the flight numbers) goes out before the piloted flag syncs.
			run.stage = Stage.PILOTED;
			run.ticks = 0;
			ShipRecord flying = Ships.update(server, run.owner, r -> r.withState(ShipState.FLYING)).orElseThrow();
			status(pilot, run, flying);
			QuestHooks.onHelmTaken(pilot);
		}
	}

	/** Flown by hand: the boost meter drains while the boost runs and refills while it rests (4.3). */
	private static void fly(Run run, ShipRecord record, ServerPlayer pilot, FlightBalance b) {
		boolean was = run.boosting;

		if (run.boosting) {
			run.boost = Math.max(0f, run.boost - 1f / FlightRules.boostTicks(b, record.upgrades()));
			run.boosting = run.boostHeld && run.boost > 0f;
		} else {
			run.boost = Math.min(1f, run.boost + 1f / Math.max(1, b.boostRechargeTicks));
		}

		if (run.boosting != was || run.ticks % STATUS_INTERVAL == 0) {
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

		boolean was = run.boosting;
		run.boostHeld = pressed;
		run.boosting = pressed && run.boost > 0f;

		if (run.boosting != was) {
			ShipRegistry.of(player.level().getServer(), run.owner).ifPresent(r -> status(player, run, r));
		}
	}

	// ---------------------------------------------------------------- endings

	/**
	 * Ends a run with nobody landing the ship (4.10): the record is settled where the ship last
	 * landed and, with {@code restore}, the body goes back there (anyone still aboard with it). The
	 * owner, if online, leaves the helm.
	 */
	private static void release(MinecraftServer server, Run run, boolean restore) {
		RUNS.remove(run.owner);
		ShipRecord record = Ships.update(server, run.owner, r -> ShipRules.recovered(r, ShipBalance.get())).orElse(null);

		if (record != null && restore) {
			Ships.restore(server, run.owner, true);
		}

		ServerPlayer owner = server.getPlayerList().getPlayer(run.owner);

		if (owner != null) {
			TravelNetwork.send(owner, new FlightStatusPayload(FlightStatusPayload.HELM_NONE, run.boost, false,
					profile(owner, record == null ? 0 : record.upgrades())));
		}

		OldRepublicMod.LOGGER.info("The helm of {}'s ship is released", run.owner);
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
	 * the pilot, the run ends at the next tick.
	 */
	static void riderLeaving(ServerPlayer player, ShipRecord record) {
		player.stopRiding();
		Ships.placeBesidePad(player, record, true);
	}

	static void clear() {
		RUNS.clear();
	}

	// ---------------------------------------------------------------- the pilot's status

	private static void status(ServerPlayer pilot, Run run, ShipRecord record) {
		int helm = switch (run.stage) {
			case SPOOL -> FlightStatusPayload.HELM_SPOOL;
			case RISE -> FlightStatusPayload.HELM_RISE;
			case PILOTED -> FlightStatusPayload.HELM_PILOTED;
		};
		TravelNetwork.send(pilot, new FlightStatusPayload(helm, run.boost, run.boosting, profile(pilot, record.upgrades())));
	}

	/** The numbers the pilot's ship flies by where they are (4.4). */
	private static FlightModel.Profile profile(ServerPlayer pilot, int upgrades) {
		return FlightRules.profile(FlightBalance.get(), upgrades, Planets.isOrbit(pilot.level()));
	}

	private Helm() {
	}
}
