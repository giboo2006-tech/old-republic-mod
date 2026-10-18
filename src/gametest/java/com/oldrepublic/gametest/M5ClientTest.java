package com.oldrepublic.gametest;

import com.oldrepublic.gametest.m5.FlightClientSteps;
import com.oldrepublic.gametest.m5.FoesClientSteps;
import com.oldrepublic.gametest.m5.ForceClientSteps;
import com.oldrepublic.gametest.m5.PlanetClientSteps;
import com.oldrepublic.gametest.m5.SaberClientSteps;
import com.oldrepublic.gametest.m5.SpaceClientSteps;
import com.oldrepublic.gametest.m5.StoryClientSteps;
import com.oldrepublic.gametest.m5.TurningClientSteps;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * Milestone 5 ("Advanced Features") client test (spec MILESTONE_5.md 19.2): one seeded,
 * non-superflat world, made as {@code M4ClientTest} makes its world, in which each worker's steps
 * run in the merge order: planets, flight, space, sabers, powers, foes, turning, story. Screenshots
 * are {@code oldrepublic_m5_<nnn>_<what>}, numbered by band (W1 100 to 109 ... W8 185 to 199).
 * Fails on any crash or failed step.
 */
public class M5ClientTest implements FabricClientGameTest {
	/** The playthrough seed, so the seed survey's positions hold here too. */
	private static final String SEED = "echoes-of-the-old-republic";

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
			server.runCommand("time set noon");
			server.runCommand("weather clear");
			server.runCommand("gamemode survival @a");
			server.runCommand("effect give @a resistance infinite 4 true");
			server.runCommand("effect give @a saturation infinite 1 true");
			context.waitTicks(20);

			PlanetClientSteps.run(context, server);
			FlightClientSteps.run(context, server);
			SpaceClientSteps.run(context, server);
			SaberClientSteps.run(context, server);
			ForceClientSteps.run(context, server);
			FoesClientSteps.run(context, server);
			TurningClientSteps.run(context, server);
			StoryClientSteps.run(context, server);
		}
	}
}
