package com.oldrepublic.gametest;

import java.util.List;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.ability.Abilities;
import com.oldrepublic.ability.AbilityAction;
import com.oldrepublic.ability.ForceAbility;
import com.oldrepublic.client.travel.ShipClientState;
import com.oldrepublic.equipment.blaster.Blasters;
import com.oldrepublic.force.barrier.ForceBarrier;
import com.oldrepublic.gametest.m4.GearClientSteps;
import com.oldrepublic.gametest.visuals.Visuals;
import com.oldrepublic.planet.PlanetHubs;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.QuestStage;
import com.oldrepublic.travel.CourierShuttle;
import com.oldrepublic.travel.Destination;
import com.oldrepublic.travel.ShipRecord;
import com.oldrepublic.travel.ShipRegistry;
import com.oldrepublic.travel.ShipRules;
import com.oldrepublic.travel.ShipState;
import com.oldrepublic.travel.Ships;
import com.oldrepublic.travel.TravelService;
import com.oldrepublic.travel.network.FlightPhasePayload;
import com.oldrepublic.vendor.hub.HubNpc;
import com.oldrepublic.vendor.hub.HubRole;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;

/**
 * Milestone 4 travel client test (spec MILESTONE_4.md 17.2; the lead's driver, W3's ship). The
 * whole trip with real ticks: a ship from {@code /oldrepublic ship give}, every destination
 * unlocked, then flights to the Greenwatch (Selvaren), the Proving (Varkhast) and home, each
 * launched through {@link TravelService#launch} with its full checks. Boarding is done on the
 * server: {@code TravelClientSteps} already boards with real use presses.
 *
 * <p>After each landing, on the server: the player is in the destination's world, off the ship and
 * beside it; the ship is READY there with no flight left; on a planet the hub is placed and the ship
 * stands on one of its pad bays or the overflow ring (4.5). At the Greenwatch the player then walks
 * to the hub's keeper with real key presses.
 *
 * <p>Both worlds are set to Peaceful first: Varkhast spawns husks in daylight, and one that strikes
 * the player beside the pad locks the next launch for five seconds (the combat lock, 4.6). The lock
 * itself is covered by the server tests; here it would only make the round trip fail at random.
 *
 * <p>Then the same round trip on a dedicated server with default properties
 * ({@code allow-flight=false}), followed by a three-charge jet pack chain
 * ({@link GearClientSteps#jetChain}), a blaster fired (12.4) and 80 ticks of Force Barrier; the
 * player must still be connected at the end.
 *
 * <p>Screenshots {@code oldrepublic_m4_<nn>_<what>}, numbered after the workers' bands: 70
 * {@code greenwatch_climb}, 71 {@code greenwatch_hyperspace}, 72 {@code greenwatch_landing}, 73
 * {@code proving_landing}, 74 {@code home_landing}, 75 {@code dedicated_greenwatch}.
 */
public class TravelClientTest implements FabricClientGameTest {
	/** The playthrough seed, as {@code M4ClientTest} uses. */
	private static final String SEED = "echoes-of-the-old-republic";
	/** A flight's phases can wait on a hub's first placement and its chunks. */
	private static final int PHASE_TIMEOUT = 1600;
	private static final int LAND_TIMEOUT = 600;
	/** Riders step off within this many blocks of the ship (4.6). */
	private static final double BESIDE = 9;
	/** How near a pad bay the ship must stand, horizontally. */
	private static final double ON_BAY = 3;
	/** The outermost overflow ring (64, 72, 80) and a little slack (4.5; outside the 95-wide hubs, docs/MILESTONE_6.md 5.7). */
	private static final double OVERFLOW = 84;
	private static final double LANDING_ALTITUDE = 5;
	/** The keeper's spawner wakes within 64 blocks; its first check can take a second or so. */
	private static final double KEEPER_RANGE = 64;
	private static final int KEEPER_WAIT = 200;
	/** Close enough to talk to the keeper, and how long the walk may take, re-aimed every few ticks. */
	private static final double AT_KEEPER = 4;
	private static final int WALK_TIMEOUT = 400;
	private static final int WALK_STEP = 5;
	/** A walk that gets this much closer shows the keys move the player, even if a wall stops it short. */
	private static final double WALK_PROGRESS = 3;
	private static final int BLASTER_HOLD = 20;
	private static final int BARRIER_TICKS = 80;
	private static final Function<Options, KeyMapping> FORWARD = o -> o.keyUp;
	private static final Function<Options, KeyMapping> JUMP = o -> o.keyJump;
	private static final Function<Options, KeyMapping> USE = o -> o.keyUse;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (Visuals.only()) {
			return; // A visuals-only run: VisualsClientTest takes its close-up pictures alone.
		}

		try (TestSingleplayerContext world = context.worldBuilder()
				.setUseConsistentSettings(false)
				.adjustSettings(creator -> {
					creator.setSeed(SEED);
					creator.getGameRules().set(GameRules.ADVANCE_WEATHER, false, null);
				})
				.create()) {
			TestServerContext server = world.getServer();
			world.getConnection().waitForChunksRender();
			prepare(context, server);
			roundTrip(context, server, true);
		}

		try (TestDedicatedServerContext server = context.worldBuilder().createServer();
				TestDedicatedServerConnection connection = server.connect()) {
			connection.waitForChunksRender();
			prepare(context, server);
			roundTrip(context, server, false);
			GearClientSteps.jetChain(context, server);
			fireBlaster(context, server);
			barrier(context, server);
			context.waitTicks(20);

			if (!server.computeOnServer(s -> s.getPlayerList().getPlayerCount() == 1)) {
				throw new AssertionError("The player was disconnected from the dedicated server after the trip and the jet chain");
			}
		}
	}

	private static void prepare(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("time set noon");
		server.runCommand("weather clear");
		server.runCommand("difficulty peaceful");
		server.runCommand("gamemode survival @a");
		server.runCommand("effect give @a resistance infinite 4 true");
		server.runCommand("effect give @a saturation infinite 1 true");
		server.runCommand("oldrepublic ship give @p");
		server.runCommand("oldrepublic ship unlock @p all");
		context.waitTicks(20);

		if (server.computeOnServer(s -> Ships.shuttle(s, player(s).getUUID()) == null)) {
			throw new AssertionError("/oldrepublic ship give placed no ship body");
		}
	}

	/** Greenwatch, the Proving, then home; screenshots of every phase on the first trip only. */
	private static void roundTrip(ClientGameTestContext context, TestServerContext server, boolean singleplayer) {
		fly(context, server, Destination.GREENWATCH, singleplayer ? "oldrepublic_m4_70_greenwatch_climb" : null,
				singleplayer ? "oldrepublic_m4_71_greenwatch_hyperspace" : null,
				singleplayer ? "oldrepublic_m4_72_greenwatch_landing" : "oldrepublic_m4_75_dedicated_greenwatch");

		if (singleplayer) {
			walkToKeeper(context, server);
		}

		fly(context, server, Destination.PROVING, null, null, singleplayer ? "oldrepublic_m4_73_proving_landing" : null);
		fly(context, server, Destination.HOME, null, null, singleplayer ? "oldrepublic_m4_74_home_landing" : null);
	}

	private static void fly(ClientGameTestContext context, TestServerContext server, Destination to, @Nullable String climbShot,
			@Nullable String hyperspaceShot, @Nullable String landingShot) {
		server.runCommand("oldrepublic ship fuel @p");
		server.runOnServer(s -> {
			CourierShuttle body = Ships.shuttle(s, player(s).getUUID());

			if (body == null || !player(s).startRiding(body, true, false)) {
				throw new AssertionError("The owner could not board their ship before the flight to " + to.id());
			}
		});
		context.waitTicks(5);
		TravelService.Refusal refusal = server.computeOnServer(s -> TravelService.launch(player(s), to));

		if (refusal != TravelService.Refusal.NONE) {
			throw new AssertionError("The flight to " + to.id() + " is refused: " + refusal);
		}

		context.waitFor(mc -> phase() == ShipRules.PHASE_CLIMB, PHASE_TIMEOUT);

		if (climbShot != null) {
			context.waitTicks(20);
			context.takeScreenshot(climbShot);
		}

		context.waitFor(mc -> phase() == ShipRules.PHASE_HYPERSPACE, PHASE_TIMEOUT);

		if (hyperspaceShot != null) {
			context.waitTicks(30);
			context.takeScreenshot(hyperspaceShot);
		}

		context.waitFor(mc -> {
			FlightPhasePayload f = ShipClientState.flight();
			Entity ship = mc.player.getVehicle();
			return f != null && f.phase() == ShipRules.PHASE_DESCENT && ship != null && ship.getY() - f.padY() < LANDING_ALTITUDE;
		}, PHASE_TIMEOUT);

		if (landingShot != null) {
			context.waitTicks(2);
			context.takeScreenshot(landingShot);
		}

		context.waitFor(mc -> ShipClientState.flight() == null, LAND_TIMEOUT);
		server.waitFor(s -> !Ships.isAboard(player(s)) && ShipRegistry.of(s, player(s).getUUID()).map(r -> r.flight().isEmpty()).orElse(false), LAND_TIMEOUT);
		context.waitTicks(20);
		String problem = server.computeOnServer(s -> landingProblem(s, to));

		if (problem != null) {
			throw new AssertionError("After the flight to " + to.id() + ": " + problem);
		}

		OldRepublicMod.LOGGER.info("Travel client test: landed at {}", to.id());
	}

	/**
	 * Walks to the Greenwatch's keeper (its host) with real key presses: faces the keeper every few
	 * ticks, holds forward and jumps when stuck. The keeper must appear; a walk that a wall stops
	 * short is logged, and only a walk that gets no closer at all fails.
	 */
	private static void walkToKeeper(ClientGameTestContext context, TestServerContext server) {
		server.waitFor(s -> keeper(s) != null, KEEPER_WAIT);
		double start = keeperDistance(context, server);
		double distance = start;
		Vec3 last = context.computeOnClient(mc -> mc.player.position());
		context.getInput().holdKey(FORWARD);

		try {
			for (int t = 0; t < WALK_TIMEOUT && distance > AT_KEEPER; t += WALK_STEP) {
				Vec3 target = server.computeOnServer(s -> {
					HubNpc keeper = keeper(s);
					return keeper == null ? null : keeper.position();
				});

				if (target == null) {
					throw new AssertionError("The Greenwatch keeper vanished during the walk");
				}

				Vec3 at = context.computeOnClient(mc -> mc.player.position());
				float yaw = (float) (Mth.atan2(target.z - at.z, target.x - at.x) * Mth.RAD_TO_DEG) - 90f;
				context.getInput().lookAt(yaw, 0f);

				if (at.distanceToSqr(last) < 0.01) {
					context.getInput().holdKeyFor(JUMP, 2);
				}

				last = at;
				context.waitTicks(WALK_STEP);
				distance = keeperDistance(context, server);
			}
		} finally {
			context.getInput().releaseKey(FORWARD);
		}

		context.setScreen(() -> null);

		if (distance > AT_KEEPER && start - distance < WALK_PROGRESS) {
			throw new AssertionError("Walking toward the Greenwatch keeper got no closer: " + Math.round(start) + " blocks, then " + Math.round(distance));
		}

		if (distance > AT_KEEPER) {
			OldRepublicMod.LOGGER.warn("Travel client test: the walk to the keeper stopped {} blocks short (from {})", Math.round(distance), Math.round(start));
		} else {
			OldRepublicMod.LOGGER.info("Travel client test: walked {} blocks to the Greenwatch keeper", Math.round(start));
		}
	}

	/** The Greenwatch host nearest the player, within the spawner's waking range; null before it spawned. */
	private static @Nullable HubNpc keeper(MinecraftServer server) {
		ServerPlayer p = player(server);
		return p.level().getEntitiesOfClass(HubNpc.class, p.getBoundingBox().inflate(KEEPER_RANGE), npc -> npc.role() == HubRole.HOST).stream()
				.min((a, b) -> Double.compare(a.distanceToSqr(p), b.distanceToSqr(p))).orElse(null);
	}

	private static double keeperDistance(ClientGameTestContext context, TestServerContext server) {
		return server.computeOnServer(s -> {
			HubNpc keeper = keeper(s);
			ServerPlayer p = player(s);
			if (keeper == null) {
				return Double.MAX_VALUE;
			}

			double dx = keeper.getX() - p.getX();
			double dz = keeper.getZ() - p.getZ();
			return Math.sqrt(dx * dx + dz * dz);
		});
	}

	/** A blaster pistol fired into the sky with a real use press (spec 12.4); the gun must heat up. */
	private static void fireBlaster(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("item replace entity @p weapon.mainhand with oldrepublic:blaster_pistol");
		context.getInput().lookAt(0f, -60f);
		// Past the draw delay, so the first pull fires.
		context.waitTicks(15);
		context.getInput().holdKeyFor(USE, BLASTER_HOLD);
		float heat = server.computeOnServer(s -> Blasters.heat(player(s).getMainHandItem()).heat());
		server.runCommand("item replace entity @p weapon.mainhand with minecraft:air");
		context.getInput().lookAt(0f, 0f);

		if (heat <= 0f) {
			throw new AssertionError("Holding use with a blaster pistol on the dedicated server fired nothing");
		}
	}

	/**
	 * 80 ticks of Force Barrier on the dedicated server. Shortcut: the player is awakened if needed and
	 * taught Barrier, with full energy, and the power's key is pressed on the server's side.
	 */
	private static void barrier(ClientGameTestContext context, TestServerContext server) {
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			ForceData data = Progression.get(p);

			if (!data.isAwakened()) {
				data.questStage = QuestStage.FOCUS.ordinal();
			}

			data.abilities.add(ForceBarrier.ID);
			data.energy = data.maxEnergy;
			Progression.commit(p, data);
			Progression.setCooldown(p, ForceBarrier.ID, 0);
			press(p, AbilityAction.START);
		});
		context.waitTicks(BARRIER_TICKS);
		boolean held = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			boolean up = ForceBarrier.isChannelling(p);
			press(p, AbilityAction.RELEASE);
			return up;
		});

		if (!held) {
			throw new AssertionError("Force Barrier did not hold for " + BARRIER_TICKS + " ticks on the dedicated server");
		}
	}

	/** Presses Barrier's key on the server's side, as the key's packet would. */
	private static void press(ServerPlayer p, AbilityAction action) {
		ForceAbility barrier = Abilities.get(ForceBarrier.ID);

		if (barrier == null) {
			throw new AssertionError("Force Barrier is not registered");
		}

		barrier.onInput(p, action);
	}

	/** Null when the player and the ship are where the flight to {@code to} should leave them. */
	private static @Nullable String landingProblem(MinecraftServer server, Destination to) {
		ServerPlayer p = player(server);
		ShipRecord r = ShipRegistry.of(server, p.getUUID()).orElseThrow();
		CourierShuttle ship = Ships.shuttle(server, p.getUUID());
		Planets.Planet planet = to.planet();

		if (planet == null) {
			return "the destination has no fixed world";
		}

		if (!p.level().dimension().equals(planet.level()) || !r.pos().dimension().equals(planet.level())) {
			return "the player is in " + p.level().dimension().identifier() + " and the ship in " + r.pos().dimension().identifier()
					+ ", not " + planet.level().identifier();
		}

		if (r.state() != ShipState.READY) {
			return "the ship is " + r.state() + " at hull " + r.hull() + ", fuel " + r.fuel() + ", not READY";
		}

		if (ship == null || p.distanceTo(ship) > BESIDE) {
			return "the player is not beside the ship (at " + p.blockPosition().toShortString() + ", ship at " + r.pos().pos().toShortString() + ")";
		}

		if (planet == Planets.Planet.HOME) {
			return null;
		}

		BlockPos hub = PlanetHubs.hub(server, planet);

		if (hub == null) {
			return "the " + planet.id() + " hub is not placed";
		}

		List<BlockPos> bays = PlanetHubs.padBays(server, planet);
		BlockPos at = r.pos().pos();
		boolean onBay = bays.stream().anyMatch(bay -> horizontal(bay, at) <= ON_BAY);

		if (!onBay && horizontal(hub, at) > OVERFLOW) {
			return "the ship stands at " + at.toShortString() + ", on no pad bay of " + bays + " and outside the overflow ring of the hub at "
					+ hub.toShortString();
		}

		return null;
	}

	private static double horizontal(BlockPos a, BlockPos b) {
		double dx = a.getX() - b.getX();
		double dz = a.getZ() - b.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** The phase of the player's flight as the client knows it, or {@link ShipRules#PHASE_GROUND}. */
	private static int phase() {
		FlightPhasePayload f = ShipClientState.flight();
		return f == null ? ShipRules.PHASE_GROUND : f.phase();
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}
}
