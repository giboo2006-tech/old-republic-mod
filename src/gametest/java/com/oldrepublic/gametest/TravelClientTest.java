package com.oldrepublic.gametest;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.travel.ShipClientState;
import com.oldrepublic.gametest.m4.GearClientSteps;
import com.oldrepublic.planet.PlanetHubs;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.travel.CourierShuttle;
import com.oldrepublic.travel.Destination;
import com.oldrepublic.travel.ShipRecord;
import com.oldrepublic.travel.ShipRegistry;
import com.oldrepublic.travel.ShipRules;
import com.oldrepublic.travel.ShipState;
import com.oldrepublic.travel.Ships;
import com.oldrepublic.travel.TravelService;
import com.oldrepublic.travel.network.FlightPhasePayload;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * Milestone 4 travel client test (spec MILESTONE_4.md 17.2; the lead's driver, W3's ship). The
 * whole trip with real ticks: a ship from {@code /oldrepublic ship give}, every destination
 * unlocked, then flights to the Greenwatch (Selvaren), the Proving (Varkhast) and home, each
 * launched through {@link TravelService#launch} with its full checks. Boarding is done on the
 * server: {@code TravelClientSteps} already boards with real use presses.
 *
 * <p>After each landing, on the server: the player is in the destination's world, off the ship and
 * beside it; the ship is READY there with no flight left; on a planet the hub is placed and the ship
 * stands on one of its pad bays or the overflow ring (4.5).
 *
 * <p>Then the same round trip on a dedicated server with default properties
 * ({@code allow-flight=false}), followed by a three-charge jet pack chain
 * ({@link GearClientSteps#jetChain}); the player must still be connected at the end.
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
	/** The outermost overflow ring (40, 48, 56) and a little slack (4.5). */
	private static final double OVERFLOW = 60;
	private static final double LANDING_ALTITUDE = 5;

	@Override
	public void runTest(ClientGameTestContext context) {
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
			context.waitTicks(20);

			if (!server.computeOnServer(s -> s.getPlayerList().getPlayerCount() == 1)) {
				throw new AssertionError("The player was disconnected from the dedicated server after the trip and the jet chain");
			}
		}
	}

	private static void prepare(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("time set noon");
		server.runCommand("weather clear");
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
