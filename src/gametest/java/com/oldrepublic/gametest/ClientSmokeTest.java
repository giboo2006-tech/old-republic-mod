package com.oldrepublic.gametest;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * Boots the real client, creates a superflat world, and walks through the vertical slice with
 * commands and real key presses, taking screenshots that CI prints so they can be reviewed. Fails
 * on any crash or on a screen that never opens.
 */
public class ClientSmokeTest implements FabricClientGameTest {
	/** Player stands here facing south (+Z), so targets placed at +Z are in front of them. */
	private static final String STAND = "0 -60 0";

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			TestServerContext server = singleplayer.getServer();
			singleplayer.getConnection().waitForChunksRender();
			server.runCommand("time set noon");
			server.runCommand("gamemode survival @a");
			server.runCommand("effect give @a resistance infinite 4 true");
			server.runCommand("effect give @a saturation infinite 1 true");
			server.runCommand("tp @a " + STAND + " 0 0");
			context.waitTicks(20);
			context.takeScreenshot("oldrepublic_00_spawn");

			awakening(context, server);
			forcePush(context, server);
			screens(context);
			lightsaber(context, server);
			poacher(context, server);
			crystalCave(context, server);
		}

		dedicatedServer(context);
	}

	/** Places the cave's heart piece underground and visits the cavern, hazard and trial. */
	private static void crystalCave(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("execute as @a run oldrepublic quest set 4");
		server.runCommand("place template oldrepublic:crystal_cave/heart 0 -40 0");
		server.runCommand("tp @a 22.5 -39 8.5 0 0");
		context.waitTicks(40);
		context.takeScreenshot("oldrepublic_15_cave_cavern");

		server.runCommand("tp @a 22.5 -39 33.5 0 10");
		context.waitTicks(40);
		context.takeScreenshot("oldrepublic_16_trial_chamber");
		server.runCommand("execute as @a run oldrepublic trial start");
		context.waitTicks(30);
		context.takeScreenshot("oldrepublic_17_trial_forming");
		context.waitTicks(40);
		context.takeScreenshot("oldrepublic_18_shadow_echo");
		server.runCommand("kill @e[type=oldrepublic:shadow_echo]");
		context.waitTicks(50);
		context.takeScreenshot("oldrepublic_19_crystal_formed");
	}

	/**
	 * Joins a real dedicated server (separate server settings, networking and player data) and
	 * runs the core loop there, so multiplayer sync and server-side code paths are exercised.
	 */
	private static void dedicatedServer(ClientGameTestContext context) {
		try (TestDedicatedServerContext server = context.worldBuilder().createServer();
				TestDedicatedServerConnection connection = server.connect()) {
			connection.waitForChunksRender();
			server.runCommand("time set noon");
			server.runCommand("effect give @a resistance infinite 4 true");
			server.runCommand("tp @a " + STAND + " 0 0");
			server.runCommand("execute as @a run oldrepublic awaken");
			context.waitTicks(220);
			server.runCommand("execute as @a run oldrepublic push grant");
			server.runCommand("summon husk 0 -60 4 {PersistenceRequired:1b,Rotation:[180f,0f],active_effects:[{id:\"minecraft:slowness\",amplifier:5,duration:-1,show_particles:0b}]}");
			context.getInput().lookAt(0, 10);
			context.waitTicks(20);
			context.getInput().holdKey(InputConstants.KEY_R);
			context.waitTicks(24);
			context.getInput().releaseKey(InputConstants.KEY_R);
			context.waitTicks(4);
			context.takeScreenshot("oldrepublic_14_dedicated_push");
			server.runCommand("execute as @a run oldrepublic progress");
		}
	}

	private static void awakening(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("execute as @a run oldrepublic awaken");
		context.waitTicks(60);
		context.takeScreenshot("oldrepublic_01_dream");
		context.waitTicks(160);
		context.takeScreenshot("oldrepublic_02_after_awakening");
	}

	private static void forcePush(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("execute as @a run oldrepublic push grant");
		server.runCommand("execute as @a run oldrepublic energy fill");
		server.runCommand("summon husk 0 -60 4 {PersistenceRequired:1b,Rotation:[180f,0f],active_effects:[{id:\"minecraft:slowness\",amplifier:5,duration:-1,show_particles:0b}]}");
		server.runCommand("summon husk 1 -60 5 {PersistenceRequired:1b,Rotation:[180f,0f],active_effects:[{id:\"minecraft:slowness\",amplifier:5,duration:-1,show_particles:0b}]}");
		server.runCommand("summon husk -1 -60 5 {PersistenceRequired:1b,Rotation:[180f,0f],active_effects:[{id:\"minecraft:slowness\",amplifier:5,duration:-1,show_particles:0b}]}");
		context.getInput().lookAt(0, 10);
		context.waitTicks(20);

		context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		context.getInput().holdKey(InputConstants.KEY_R);
		context.waitTicks(16);
		context.takeScreenshot("oldrepublic_03_push_charge");
		context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
		context.waitTicks(8);
		context.getInput().releaseKey(InputConstants.KEY_R);
		context.waitTicks(3);
		context.takeScreenshot("oldrepublic_04_push_release");
		context.waitTicks(12);
		context.takeScreenshot("oldrepublic_05_push_aftermath");
		server.runCommand("tp @e[type=husk] 0 -200 0"); // no death puffs in later shots
	}

	private static void screens(ClientGameTestContext context) {
		context.getInput().pressKey(InputConstants.KEY_K);
		context.waitFor(mc -> mc.gui.screen() != null && mc.gui.screen().getClass().getSimpleName().equals("ForceScreen"));
		context.waitTicks(10);
		context.takeScreenshot("oldrepublic_06_force_screen");
		context.setScreen(() -> null);

		context.getInput().pressKey(InputConstants.KEY_J);
		context.waitFor(mc -> mc.gui.screen() != null && mc.gui.screen().getClass().getSimpleName().equals("JournalScreen"));
		context.waitTicks(10);
		context.takeScreenshot("oldrepublic_07_journal");
		context.setScreen(() -> null);
	}

	private static void lightsaber(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("clear @a");
		server.runCommand("execute as @a run oldrepublic saber crystal green");
		server.runCommand("execute as @a run oldrepublic saber parts");
		server.runCommand("setblock 0 -60 2 oldrepublic:lightsaber_workbench");
		context.waitTicks(5);
		context.getInput().lookAt(new BlockPos(0, -60, 2));
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
		context.waitFor(mc -> mc.gui.screen() != null && mc.gui.screen().getClass().getSimpleName().equals("LightsaberWorkbenchScreen"));
		context.waitTicks(10);
		context.takeScreenshot("oldrepublic_08_workbench");
		context.setScreen(() -> null);
		server.runCommand("setblock 0 -60 2 air");

		server.runCommand("clear @a");
		server.runCommand("execute as @a run oldrepublic saber give green guardian wrapped weighted");
		context.getInput().lookAt(0, 5);
		context.waitTicks(10);
		context.getInput().pressKey(InputConstants.KEY_U);
		context.waitTicks(15);
		context.takeScreenshot("oldrepublic_09_saber_ignited");
		context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		context.waitTicks(5);
		context.takeScreenshot("oldrepublic_10_saber_third_person");
		context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
	}

	private static void poacher(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("summon oldrepublic:crystal_poacher 0 -60 11");
		context.getInput().lookAt(0, 5);
		context.waitTicks(40);
		context.takeScreenshot("oldrepublic_11_poacher");
		// Guard with the ignited saber so bolts are deflected.
		context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_RIGHT);
		context.waitTicks(30);
		context.takeScreenshot("oldrepublic_12_poacher_guard");
		context.waitTicks(30);
		context.takeScreenshot("oldrepublic_13_poacher_guard_late");
		context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_RIGHT);
		server.runCommand("kill @e[type=oldrepublic:crystal_poacher]");
	}
}
