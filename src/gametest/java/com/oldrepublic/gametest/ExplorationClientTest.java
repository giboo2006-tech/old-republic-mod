package com.oldrepublic.gametest;

import com.oldrepublic.gametest.m3.BossClientSteps;
import com.oldrepublic.gametest.m3.HolocronClientSteps;
import com.oldrepublic.gametest.m3.StoryClientSteps;
import com.oldrepublic.gametest.m3.TrooperClientSteps;
import com.oldrepublic.gametest.m3.WorldClientSteps;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * Milestone 3 ("Echoes and Outposts") client test (spec 17.2): one seeded, non-superflat world
 * (consistent client worlds have structures off, so this one is created as {@code PlaythroughTest}
 * creates its world), in which each worker's steps run in order: world, story, holocron, trooper,
 * boss. Screenshots are {@code oldrepublic_m3_<nn>_<what>}, numbered by band (W1 10 to 19 ... W5
 * 50 to 59). Fails on any crash or failed step.
 */
public class ExplorationClientTest implements FabricClientGameTest {
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

			WorldClientSteps.run(context, server);
			StoryClientSteps.run(context, server);
			HolocronClientSteps.run(context, server);
			TrooperClientSteps.run(context, server);
			BossClientSteps.run(context, server);
		}
	}
}
