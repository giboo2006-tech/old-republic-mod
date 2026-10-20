package com.oldrepublic.boss.ulvaine;

import com.oldrepublic.force.ForceReactions;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;

/**
 * Server and common initialisation of Darth Ulvaine's fight (spec MILESTONE_5.md 12.2): her entity and
 * the Crescendo's resonators, her narrowed Force reactions, the fights' tick, the Heart's lectern (the
 * echo) and the {@code ulvaine} command. Called from {@code FoesM5Feature}.
 */
public final class UlvaineFeature {
	public static void init() {
		UlvaineEntities.init();
		ForceReactions.register(new UlvaineReactions());

		ServerTickEvents.END_SERVER_TICK.register(UlvaineFight::tick);
		// Before the levels save, so no fight's entities linger.
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> UlvaineFight.clear());
		UseBlockCallback.EVENT.register(UlvaineFight::onUseBlock);

		UlvaineCommands.init();
	}

	private UlvaineFeature() {
	}
}
