package com.oldrepublic.gametest.m2;

import com.oldrepublic.client.quest.JournalScreen;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;

/**
 * Screenshot steps of Chapter Two for the M2 client test: the Crossing with both mentors' blades
 * lit, the chosen mentor's camp, the Chapter Two journal page, and the raider camp of Smoke on the
 * Ridge from about 30 blocks with its smoke. Every story shortcut used here is logged by the story
 * commands. Screenshots are {@code oldrepublic_m2_2x_*}.
 */
public final class StoryClientSteps {
	/** Where the player starts on the superflat test world. */
	private static final String STAND = "0 -60 0";

	public static void run(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("time set noon");
		server.runCommand("gamemode survival @a");
		server.runCommand("effect give @a resistance infinite 4 true");
		server.runCommand("tp @a " + STAND + " 0 0");
		server.runCommand("oldrepublic story reset @p");
		context.waitTicks(10);

		crossing(context, server);
		camp(context, server);
		journal(context);
		raiderCamp(context, server);

		server.runCommand("oldrepublic story reset @p");
		server.runCommand("effect clear @a resistance");
		context.waitTicks(10);
	}

	/** The meeting point: both mentors facing each other, blades lit; the player 8 blocks south of it, looking north. */
	private static void crossing(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("oldrepublic story meet @p");
		server.runCommand("execute as @a at @s run tp @s ~ ~ ~ 180 8");
		context.waitTicks(40);
		context.takeScreenshot("oldrepublic_m2_20_crossing");
	}

	/** The Jedi is chosen: the Sith walks off and the Watchwoman's camp is placed at the meeting point. */
	private static void camp(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("oldrepublic story choose @p jedi");
		context.waitTicks(40);
		context.takeScreenshot("oldrepublic_m2_21_jedi_camp");
	}

	/** The journal's left page shows the Chapter Two objective. */
	private static void journal(ClientGameTestContext context) {
		context.setScreen(JournalScreen::new);
		context.waitTicks(10);
		context.takeScreenshot("oldrepublic_m2_22_journal_chapter_two");
		context.setScreen(() -> null);
		context.waitTicks(5);
	}

	/**
	 * The raider camp placed 24 blocks south, then seen from 6 blocks further back. The crew spawns
	 * as the player is within 48 blocks; the shot is taken once the signal fire's smoke has risen.
	 */
	private static void raiderCamp(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 0");
		server.runCommand("oldrepublic story reckoning @p here");
		server.runCommand("execute as @a at @s run tp @s ~ ~ ~-6 0 12");
		context.waitTicks(80);
		context.takeScreenshot("oldrepublic_m2_23_raider_camp");
	}

	private StoryClientSteps() {
	}
}
