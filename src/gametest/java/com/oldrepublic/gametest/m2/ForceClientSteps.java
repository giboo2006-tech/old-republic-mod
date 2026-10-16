package com.oldrepublic.gametest.m2;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;

/**
 * Screenshot steps of the force feature for the M2 client test (spec 16.2): Pull mid-drag; Jump
 * gather, launch and landing; a Heal channel; Lightning charge, arcs on a target, a chain and the
 * frame after the stop (nothing may remain). The powers are driven with {@code /oldrepublic force
 * use} so the steps do not depend on slot assignment. Screenshots are {@code oldrepublic_m2_4x_*}.
 */
public final class ForceClientSteps {
	/** Where the player stands, facing south (+Z), on the superflat test world. */
	private static final String STAND = "0 -60 0";
	private static final String HUSK = "{PersistenceRequired:1b,Rotation:[180f,0f],active_effects:[{id:\"minecraft:slowness\",amplifier:5,duration:-1,show_particles:0b}]}";

	public static void run(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("time set noon");
		server.runCommand("gamemode survival @a");
		server.runCommand("effect give @a resistance infinite 4 true");
		server.runCommand("execute as @a run oldrepublic quest set 4");
		server.runCommand("oldrepublic force grant all @p");
		server.runCommand("tp @a " + STAND + " 0 0");
		context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		context.waitTicks(20);

		pull(context, server);
		jump(context, server);
		heal(context, server);
		lightning(context, server);

		server.runCommand("kill @e[type=minecraft:husk]");
		context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
		context.waitTicks(10);
	}

	private static void refill(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("execute as @a run oldrepublic energy fill");
		server.runCommand("execute as @a run oldrepublic force cooldowns");
		context.waitTicks(2);
	}

	private static void use(TestServerContext server, String power, String action) {
		server.runCommand("execute as @a run oldrepublic force use " + power + " " + action);
	}

	private static void pull(ClientGameTestContext context, TestServerContext server) {
		refill(context, server);
		server.runCommand("summon husk 0 -60 9 " + HUSK);
		context.getInput().lookAt(0, 5);
		context.waitTicks(20);
		use(server, "force_pull", "start");
		// Reach (3 ticks), then the husk is on its way.
		context.waitTicks(6);
		context.takeScreenshot("oldrepublic_m2_40_pull_drag");
		context.waitTicks(30);
		server.runCommand("kill @e[type=minecraft:husk]");
	}

	private static void jump(ClientGameTestContext context, TestServerContext server) {
		refill(context, server);
		server.runCommand("tp @a " + STAND + " 0 0");
		context.waitTicks(10);
		use(server, "force_jump", "start");
		context.waitTicks(12);
		context.takeScreenshot("oldrepublic_m2_41_jump_gather");
		context.waitTicks(6);
		use(server, "force_jump", "release");
		context.waitTicks(3);
		context.takeScreenshot("oldrepublic_m2_42_jump_launch");
		context.waitTicks(60);
		context.takeScreenshot("oldrepublic_m2_43_jump_landing");
		context.waitTicks(20);
	}

	private static void heal(ClientGameTestContext context, TestServerContext server) {
		refill(context, server);
		server.runCommand("tp @a " + STAND + " 0 0");
		server.runCommand("effect clear @a resistance");
		server.runCommand("damage @p 8 minecraft:generic");
		server.runCommand("effect give @a resistance infinite 4 true");
		context.waitTicks(30);
		use(server, "force_heal", "start");
		context.waitTicks(25);
		context.takeScreenshot("oldrepublic_m2_44_heal_channel");
		use(server, "force_heal", "release");
		context.waitTicks(30);
	}

	private static void lightning(ClientGameTestContext context, TestServerContext server) {
		refill(context, server);
		server.runCommand("tp @a " + STAND + " 0 0");
		server.runCommand("summon husk 0 -60 6 " + HUSK);
		context.getInput().lookAt(0, 8);
		context.waitTicks(20);

		use(server, "force_lightning", "start");
		context.waitTicks(4);
		context.takeScreenshot("oldrepublic_m2_45_lightning_charge");
		context.waitTicks(16);
		context.takeScreenshot("oldrepublic_m2_46_lightning_arcs");
		use(server, "force_lightning", "release");
		context.waitTicks(40);

		// Chain Lightning: two more husks within 4 blocks of the first.
		refill(context, server);
		server.runCommand("oldrepublic force grant lightning_chain @p");
		server.runCommand("summon husk 2 -60 7 " + HUSK);
		server.runCommand("summon husk -2 -60 7 " + HUSK);
		context.waitTicks(10);
		use(server, "force_lightning", "start");
		context.waitTicks(22);
		context.takeScreenshot("oldrepublic_m2_47_lightning_chain");
		use(server, "force_lightning", "release");
		// The frame after the stop: no arc may remain (smoke wisps at the hand only).
		context.waitTicks(3);
		context.takeScreenshot("oldrepublic_m2_48_lightning_after_stop");
		context.waitTicks(20);
	}

	private ForceClientSteps() {
	}
}
