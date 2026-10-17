package com.oldrepublic.gametest.m2;

import java.util.HashMap;
import java.util.Map;

import com.oldrepublic.ability.network.AbilityStatePayload;
import com.oldrepublic.client.ability.AbilityInput;
import com.oldrepublic.client.skill.ForceScreen;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.QuestStage;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.server.level.ServerPlayer;

/**
 * Screenshot steps of the skills feature for the M2 client test: the Force screen on every tab
 * (owned power with its slot buttons, a locked node, a dormant node, a path-locked tree, the
 * Standing tab, the respec confirmation) and the HUD with four sockets during a cooldown and a
 * channel. Also fails when two mod keys share a default binding. Screenshots are
 * {@code oldrepublic_m2_3x_*}. Every shortcut is a logged command.
 */
public final class SkillClientSteps {
	private static final String STAND = "0 -60 0";
	private static final String AS = "execute as @p run oldrepublic ";

	public static void run(ClientGameTestContext context, TestServerContext server) {
		checkKeyDefaults(context);
		setUp(context, server);
		forceScreen(context, server);
		hud(context, server);
	}

	/** No two of the mod's keys may share a default binding (unbound keys are ignored). */
	private static void checkKeyDefaults(ClientGameTestContext context) {
		String clash = context.computeOnClient(mc -> {
			Map<String, String> seen = new HashMap<>();

			for (KeyMapping key : mc.options.keyMappings) {
				if (!key.getName().startsWith("key.oldrepublic.") || key.getDefaultKey().getValue() < 0) {
					continue;
				}

				String previous = seen.put(key.getDefaultKey().getName(), key.getName());

				if (previous != null) {
					return previous + " and " + key.getName() + " both default to " + key.getDefaultKey().getName();
				}
			}

			return seen.size() < 6 ? "only " + seen.size() + " mod keys found: " + seen : null;
		});

		if (clash != null) {
			throw new AssertionError("Mod key defaults: " + clash);
		}
	}

	private static void setUp(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("tp @a " + STAND + " 0 0");
		server.runCommand("effect give @a saturation infinite 1 true");
		server.runCommand(AS + "quest set " + QuestStage.COMPLETE.ordinal());
		server.runCommand(AS + "skills reset");
		server.runCommand(AS + "setlevel 6");
		server.runCommand(AS + "grantskillpoints 7");
		server.runCommand("oldrepublic skills grant force_push @p");
		server.runCommand(AS + "skills learn push_repulse_wave");
		server.runCommand(AS + "skills learn force_pull");
		server.runCommand(AS + "skills learn force_jump");
		server.runCommand(AS + "skills learn deep_reserves");
		server.runCommand("oldrepublic identity path @p jedi");
		server.runCommand("oldrepublic identity rank @p set 2");
		server.runCommand("oldrepublic identity alignment @p set 45");
		server.runCommand("oldrepublic skills grant force_heal @p");
		server.runCommand("oldrepublic skills grant centered_mind @p");
		server.runCommand("oldrepublic skills grant calm_focus @p");
		server.runCommand("oldrepublic skills grant form_soresu @p");
		// Drift below +40: calm_focus falls dormant, centered_mind stays active.
		server.runCommand("oldrepublic identity alignment @p set 22");
		server.runCommand(AS + "energy fill");
		// The skill service re-checks open slots once a second.
		context.waitTicks(30);
	}

	private static void forceScreen(ClientGameTestContext context, TestServerContext server) {
		shot(context, "universal", "force_pull", "oldrepublic_m2_30_force_universal_owned");
		shot(context, "universal", "jump_soaring_leap", "oldrepublic_m2_31_force_universal_locked");
		shot(context, "saber", "saber_throw", "oldrepublic_m2_32_force_saber");
		shot(context, "jedi", "calm_focus", "oldrepublic_m2_33_force_jedi_dormant");
		shot(context, "sith", "fury", "oldrepublic_m2_34_force_sith_locked");
		shot(context, "standing", null, "oldrepublic_m2_35_force_standing");
		context.runOnClient(mc -> ForceScreen.open(mc, true, "universal"));
		context.waitTicks(10);
		context.takeScreenshot("oldrepublic_m2_36_force_respec_confirm");
		context.setScreen(() -> null);
		context.waitTicks(5);
	}

	private static void shot(ClientGameTestContext context, String tab, String node, String name) {
		context.runOnClient(mc -> ForceScreen.openOn(mc, tab, node));
		context.waitTicks(10);
		context.takeScreenshot(name);
	}

	private static void hud(ClientGameTestContext context, TestServerContext server) {
		server.runCommand(AS + "skills slot 1 force_push");
		server.runCommand(AS + "skills slot 2 force_pull");
		server.runCommand(AS + "skills slot 3 force_jump");
		server.runCommand(AS + "skills slot 4 force_heal");
		server.runOnServer(s -> {
			ServerPlayer player = s.getPlayerList().getPlayers().getFirst();
			Progression.setCooldown(player, "force_pull", 160);
			Progression.setCooldown(player, "force_jump", 50);
		});
		// Visual only: show slot 4 as channelling (the arc reads the client's ability state).
		context.runOnClient(mc -> AbilityInput.onState(new AbilityStatePayload(3, "force_heal", true)));
		context.waitTicks(20);
		context.takeScreenshot("oldrepublic_m2_37_hud_sockets_cooldown_channel");
		context.runOnClient(mc -> AbilityInput.onState(new AbilityStatePayload(3, "force_heal", false)));
		context.waitTicks(5);
	}

	private SkillClientSteps() {
	}
}
