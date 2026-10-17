package com.oldrepublic.gametest;

import java.util.function.BiConsumer;

import com.oldrepublic.gametest.m2.ForceClientSteps;
import com.oldrepublic.gametest.m2.IdentityClientSteps;
import com.oldrepublic.gametest.m2.SaberClientSteps;
import com.oldrepublic.gametest.m2.SkillClientSteps;
import com.oldrepublic.gametest.m2.StoryClientSteps;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * Milestone 2 ("Identity and Builds") screenshot tour. Each feature's steps run in a fresh
 * superflat world so one feature's state cannot leak into the next; screenshots are named
 * {@code oldrepublic_m2_<nn>_<what>}. Fails on any crash or failed step.
 */
public class IdentityBuildsClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		inFreshWorld(context, SkillClientSteps::run);
		inFreshWorld(context, ForceClientSteps::run);
		inFreshWorld(context, SaberClientSteps::run);
		inFreshWorld(context, IdentityClientSteps::run);
		inFreshWorld(context, StoryClientSteps::run);
	}

	private static void inFreshWorld(ClientGameTestContext context, BiConsumer<ClientGameTestContext, TestServerContext> steps) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			TestServerContext server = singleplayer.getServer();
			singleplayer.getConnection().waitForChunksRender();
			server.runCommand("time set noon");
			server.runCommand("gamemode survival @a");
			server.runCommand("effect give @a resistance infinite 4 true");
			server.runCommand("effect give @a saturation infinite 1 true");
			context.waitTicks(20);
			steps.accept(context, server);
		}
	}
}
