package com.oldrepublic.gametest;

import com.oldrepublic.gametest.m5.FlightOrbitTrip;
import com.oldrepublic.gametest.m5.FlightSpike;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;

/**
 * Milestone 5 free-flight client test (spec MILESTONE_5.md 4.1 and 19.2; the lead's driver, W1's
 * assertions in {@link FlightSpike}). A dedicated server with default properties
 * ({@code allow-flight=false}): the player gets a ship with every destination unlocked, on
 * Peaceful so no stray mob locks a launch, then the spike runs, then the orbit trip ({@link FlightOrbitTrip}: up into
 * {@code orbit_home} and back down, up again and a jump to {@code orbit_ghessar}, down and a landing at Saltgate by
 * hand). The player must still be connected at the end.
 */
public class FlightClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestDedicatedServerContext server = context.worldBuilder().createServer();
				TestDedicatedServerConnection connection = server.connect()) {
			connection.waitForChunksRender();
			server.runCommand("time set noon");
			server.runCommand("weather clear");
			server.runCommand("difficulty peaceful");
			server.runCommand("gamemode survival @a");
			server.runCommand("effect give @a resistance infinite 4 true");
			server.runCommand("effect give @a saturation infinite 1 true");
			server.runCommand("oldrepublic ship give @p");
			server.runCommand("oldrepublic ship unlock @p all");
			context.waitTicks(20);

			FlightSpike.run(context, server);
			context.waitTicks(20);
			FlightOrbitTrip.run(context, server);
			context.waitTicks(20);

			if (!server.computeOnServer(s -> s.getPlayerList().getPlayerCount() == 1)) {
				throw new AssertionError("The player was disconnected from the dedicated server during the flight spike or the orbit trip");
			}
		}
	}
}
