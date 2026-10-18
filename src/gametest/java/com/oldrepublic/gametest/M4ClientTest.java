package com.oldrepublic.gametest;

import com.oldrepublic.gametest.m4.CraftingClientSteps;
import com.oldrepublic.gametest.m4.FoesClientSteps;
import com.oldrepublic.gametest.m4.GearClientSteps;
import com.oldrepublic.gametest.m4.PlanetClientSteps;
import com.oldrepublic.gametest.m4.StoryClientSteps;
import com.oldrepublic.gametest.m4.TravelClientSteps;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * Milestone 4 ("Broader Content") client test (spec 17.2): one seeded, non-superflat world, made
 * as {@code ExplorationClientTest} makes its world, in which each worker's steps run in the merge
 * order: planets, travel, gear, crafting, foes, story. Screenshots are
 * {@code oldrepublic_m4_<nn>_<what>}, numbered by band (W1 10 to 19 ... W6 60 to 69). Fails on any
 * crash or failed step.
 */
public class M4ClientTest implements FabricClientGameTest {
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
			TravelClientSteps.run(context, server);
			GearClientSteps.run(context, server);
			CraftingClientSteps.run(context, server);
			FoesClientSteps.run(context, server);
			StoryClientSteps.run(context, server);
		}
	}
}
