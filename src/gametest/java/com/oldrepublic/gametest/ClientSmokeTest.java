package com.oldrepublic.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * Boots the real client, creates a world, and walks through the vertical slice with commands,
 * taking screenshots that CI prints so they can be reviewed. Fails on any crash.
 */
public class ClientSmokeTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			context.waitTicks(20);
			context.takeScreenshot("oldrepublic_00_spawn");

			singleplayer.getServer().runCommand("/oldrepublic awaken");
			context.waitTicks(60);
			context.takeScreenshot("oldrepublic_01_awakening");
			context.waitTicks(160);
			context.takeScreenshot("oldrepublic_02_after_awakening");
		}
	}
}
