package com.oldrepublic.gametest.m2;

import java.util.function.Function;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;

/**
 * Screenshot steps of the saber combat and enemies feature for the M2 client test (spec 16.2): the
 * combo finisher pose, the heavy wind-up tell, the lunge, the Soresu and Ataru salutes, the thrown
 * saber in flight, a Sith Acolyte's wind-up tell and broken guard, the Poacher Captain yielding,
 * and a won spar. Real inputs drive the combo and the heavy attack; the lunge and Saber Throw use
 * their debug commands so the steps do not depend on sprinting or slot assignment. Screenshots are
 * {@code oldrepublic_m2_5x_*}.
 */
public final class SaberClientSteps {
	/** Where the player stands, facing south (+Z), on the superflat test world. */
	private static final String STAND = "0 -60 0";
	/** A target that cannot move or hit back. */
	private static final String HUSK = "{PersistenceRequired:1b,NoAI:1b,Rotation:[180f,0f]}";
	private static final Function<Options, KeyMapping> SNEAK = o -> o.keyShift;
	private static final Function<Options, KeyMapping> ATTACK = o -> o.keyAttack;

	public static void run(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("time set noon");
		server.runCommand("gamemode survival @a");
		server.runCommand("effect give @a resistance infinite 4 true");
		server.runCommand("effect give @a regeneration infinite 4 true");
		server.runCommand("execute as @a run oldrepublic quest set 4");
		server.runCommand("tp @a " + STAND + " 0 0");
		server.runCommand("clear @a");
		server.runCommand("execute as @a run oldrepublic saber give green duelist ridged balanced");
		server.runCommand("execute as @a run oldrepublic skill grant saber_lunge");
		server.runCommand("execute as @a run oldrepublic skill grant saber_throw");
		context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		context.waitTicks(10);
		// Ignite with the Toggle Lightsaber key (G).
		context.getInput().pressKey(InputConstants.KEY_G);
		context.waitTicks(20);

		combo(context, server);
		heavy(context, server);
		lunge(context, server);
		forms(context, server);
		saberThrow(context, server);
		acolyte(context, server);
		captain(context, server);
		spar(context, server);

		server.runCommand("kill @e[type=!minecraft:player]");
		server.runCommand("execute as @a run oldrepublic saber form unformed");
		context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
		context.waitTicks(10);
	}

	private static void reset(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("kill @e[type=!minecraft:player]");
		server.runCommand("tp @a " + STAND + " 0 0");
		server.runCommand("execute as @a run oldrepublic saber stamina refill");
		server.runCommand("execute as @a run oldrepublic energy fill");
		context.getInput().lookAt(0, 0);
		context.waitTicks(10);
	}

	/** Three full-strength hits on a husk: the third is the overhead finisher. */
	private static void combo(ClientGameTestContext context, TestServerContext server) {
		reset(context, server);
		server.runCommand("summon husk 0 -60 2 " + HUSK);
		context.getInput().lookAt(0, 10);
		context.waitTicks(20);

		for (int i = 0; i < 3; i++) {
			context.getInput().pressKey(ATTACK);
			// Past the Duelist/Ridged/Balanced attack cooldown, inside the combo window.
			context.waitTicks(i < 2 ? 13 : 2);
		}

		context.takeScreenshot("oldrepublic_m2_50_combo_finisher");
		context.waitTicks(20);
	}

	/** Sneak + Attack: the drawn-back wind-up. */
	private static void heavy(ClientGameTestContext context, TestServerContext server) {
		reset(context, server);
		server.runCommand("summon husk 0 -60 2 " + HUSK);
		context.waitTicks(10);
		context.getInput().holdKey(SNEAK);
		context.waitTicks(2);
		context.getInput().pressKey(ATTACK);
		context.waitTicks(5);
		context.takeScreenshot("oldrepublic_m2_51_heavy_windup");
		context.getInput().releaseKey(SNEAK);
		context.waitTicks(30);
	}

	private static void lunge(ClientGameTestContext context, TestServerContext server) {
		reset(context, server);
		server.runCommand("summon husk 0 -60 6 " + HUSK);
		context.waitTicks(10);
		server.runCommand("execute as @a run oldrepublic saber lunge");
		context.waitTicks(3);
		context.takeScreenshot("oldrepublic_m2_52_lunge");
		context.waitTicks(30);
	}

	/** The form changes: a salute for the length of the change. */
	private static void forms(ClientGameTestContext context, TestServerContext server) {
		reset(context, server);
		server.runCommand("execute as @a run oldrepublic saber form soresu");
		context.waitTicks(10);
		context.takeScreenshot("oldrepublic_m2_53_soresu_salute");
		context.waitTicks(30);
		server.runCommand("execute as @a run oldrepublic saber form ataru");
		context.waitTicks(10);
		context.takeScreenshot("oldrepublic_m2_54_ataru_salute");
		context.waitTicks(30);
		server.runCommand("execute as @a run oldrepublic saber form unformed");
		context.waitTicks(30);
	}

	private static void saberThrow(ClientGameTestContext context, TestServerContext server) {
		reset(context, server);
		server.runCommand("summon husk 0 -60 8 " + HUSK);
		context.waitTicks(10);
		server.runCommand("execute as @a run oldrepublic saber throw");
		context.waitTicks(5);
		context.takeScreenshot("oldrepublic_m2_55_saber_throw_flight");
		// Back in the hand (or recalled) before the next step.
		context.waitTicks(60);
		server.runCommand("execute as @a run oldrepublic saber recall");
		context.waitTicks(10);
	}

	/**
	 * A hostile acolyte: its attack tell shows within a few seconds of engaging. Heavy attacks while
	 * it guards then break its guard. Both moments depend on the acolyte's choices, so the frames are
	 * taken at fixed times and reviewed by eye.
	 */
	private static void acolyte(ClientGameTestContext context, TestServerContext server) {
		reset(context, server);
		server.runCommand("execute as @a run oldrepublic enemy spawn acolyte hostile acolyte");
		context.waitTicks(30);
		context.takeScreenshot("oldrepublic_m2_56_acolyte_windup");

		for (int i = 0; i < 4; i++) {
			server.runCommand("execute as @a run oldrepublic saber stamina refill");
			context.getInput().holdKey(SNEAK);
			context.waitTicks(1);
			context.getInput().pressKey(ATTACK);
			context.waitTicks(12);
			context.getInput().releaseKey(SNEAK);
			context.waitTicks(16);
		}

		context.takeScreenshot("oldrepublic_m2_57_acolyte_guard_break");
		context.waitTicks(10);
	}

	/** A lethal player blow makes the Captain yield and kneel. */
	private static void captain(ClientGameTestContext context, TestServerContext server) {
		reset(context, server);
		server.runCommand("execute as @a run oldrepublic enemy spawn captain");
		context.waitTicks(10);
		server.runCommand("execute as @a run damage @e[type=oldrepublic:poacher_captain,limit=1,sort=nearest] 1000 minecraft:player_attack by @s");
		context.waitTicks(15);
		context.takeScreenshot("oldrepublic_m2_58_captain_yield");
		context.waitTicks(10);
	}

	/** A spar with Brenn won by a decisive blow: he kneels, unharmed. */
	private static void spar(ClientGameTestContext context, TestServerContext server) {
		reset(context, server);
		server.runCommand("execute as @a run oldrepublic enemy spawn acolyte rival brenn");
		context.waitTicks(10);
		server.runCommand("execute as @a run oldrepublic saber spar yield");
		context.waitTicks(20);
		server.runCommand("execute as @a run damage @e[type=oldrepublic:sith_acolyte,limit=1,sort=nearest] 1000 minecraft:player_attack by @s");
		context.waitTicks(15);
		context.takeScreenshot("oldrepublic_m2_59_spar_won");
		context.waitTicks(10);
	}

	private SaberClientSteps() {
	}
}
