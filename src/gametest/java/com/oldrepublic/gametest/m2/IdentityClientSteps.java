package com.oldrepublic.gametest.m2;

import com.mojang.blaze3d.platform.InputConstants;
import com.oldrepublic.client.identity.StandingPanel;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Screenshot steps of the identity feature for the M2 client test: the Standing panel on each
 * page, an alignment toast, the dialogue screen with a disabled option and its reason, captive
 * settlers, and a yielding Poacher Captain with its prompt and the Spare / Execute dialogue.
 * Screenshots are {@code oldrepublic_m2_1x_*}.
 */
public final class IdentityClientSteps {
	/** Where the player stands, facing south (+Z), on the superflat test world. */
	private static final String STAND = "0 -60 0";

	/** A bare screen hosting the Standing panel the way the Force screen does. */
	private static final class StandingHost extends Screen {
		private final StandingPanel panel = new StandingPanel();

		StandingHost(String page) {
			super(Component.literal("Standing"));
			panel.open(page);
		}

		@Override
		public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
			int w = Math.min(width - 16, 440);
			int h = Math.min(height - 16, 230);
			panel.render(graphics, font, (width - w) / 2, (height - h) / 2, w, h, partialTick);
			super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		}

		@Override
		public boolean isPauseScreen() {
			return false;
		}
	}

	public static void run(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("tp @a " + STAND + " 0 0");
		server.runCommand("oldrepublic identity reset @a");
		server.runCommand("oldrepublic identity introduce @a");
		context.waitTicks(10);

		alignmentToast(context, server);
		standing(context, server);
		dialogue(context, server);
		captives(context, server);
		yieldPrompt(context, server);
	}

	private static void alignmentToast(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("oldrepublic identity alignment @a shift 12");
		context.waitTicks(12);
		context.takeScreenshot("oldrepublic_m2_10_alignment_toast");
		server.runCommand("oldrepublic identity contextual @a helped_other");
		server.runCommand("oldrepublic identity reputation @a frontier add 150");
		context.waitTicks(12);
		context.takeScreenshot("oldrepublic_m2_11_toast_stack");
		context.waitTicks(60);
	}

	private static void standing(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("oldrepublic identity path @a jedi");
		server.runCommand("oldrepublic identity trust @a jedi set 35");
		server.runCommand("oldrepublic identity reputation @a sith_empire set -260");
		server.runCommand("oldrepublic identity alignment @a minor -2");
		context.waitTicks(10);

		for (String page : new String[] {"alignment", "factions", "path"}) {
			context.setScreen(() -> new StandingHost(page));
			context.waitTicks(10);
			context.takeScreenshot("oldrepublic_m2_12_standing_" + page);
		}

		context.setScreen(() -> null);
	}

	private static void dialogue(ClientGameTestContext context, TestServerContext server) {
		// The test dialogue has an option locked behind Trusted standing with the Jedi Order.
		server.runCommand("oldrepublic identity reputation @a jedi_order set 40");
		server.runCommand("execute as @a run oldrepublic dialogue open oldrepublic_test:conditions");
		context.waitFor(mc -> mc.gui.screen() != null && mc.gui.screen().getClass().getSimpleName().equals("DialogueScreen"));
		context.waitTicks(4);
		context.takeScreenshot("oldrepublic_m2_13_dialogue_typing");
		context.getInput().pressKey(InputConstants.KEY_SPACE);
		context.waitTicks(4);
		context.takeScreenshot("oldrepublic_m2_14_dialogue_disabled_option");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitFor(mc -> mc.gui.screen() == null);
	}

	private static void captives(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("execute as @a run oldrepublic identity settler captives");
		context.waitTicks(20);
		context.takeScreenshot("oldrepublic_m2_15_captives");
		server.runCommand("kill @e[type=oldrepublic:frontier_settler]");
	}

	/**
	 * Brings the Captain to low health and lets the player finish the fight with a real punch, so
	 * the yield goes through the normal damage path (clamp, kneel, prompt).
	 */
	private static void yieldPrompt(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("summon oldrepublic:poacher_captain 0 -60 2 {PersistenceRequired:1b,NoAI:1b,Rotation:[180f,0f]}");
		context.waitTicks(10);
		server.runCommand("data merge entity @e[type=oldrepublic:poacher_captain,limit=1] {Health:6f}");
		context.getInput().lookAt(0, 15);
		context.waitTicks(5);
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(15);
		context.takeScreenshot("oldrepublic_m2_16_yield_prompt");
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
		context.waitTicks(30);
		context.takeScreenshot("oldrepublic_m2_17_yield_dialogue");
		context.setScreen(() -> null);
		server.runCommand("kill @e[type=oldrepublic:poacher_captain]");
	}

	private IdentityClientSteps() {
	}
}
