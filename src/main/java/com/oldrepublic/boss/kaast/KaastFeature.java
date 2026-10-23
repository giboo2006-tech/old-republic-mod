package com.oldrepublic.boss.kaast;

import com.oldrepublic.force.ControlEffects;
import com.oldrepublic.force.ForceReactions;
import com.oldrepublic.force.sense.SenseTargets;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/**
 * Server and common initialisation of Torvin Kaast and his hunting camp (spec MILESTONE_4.md 10.5):
 * the boss, Rattle and the camp mine, the camp gate, his narrowed control reactions, live mines for
 * Force Sense, the fights' tick and the {@code kaast} command. Called from {@code FoesFeature}.
 */
public final class KaastFeature {
	public static void init() {
		KaastEntities.init();
		KaastBlocks.init();

		KaastReactions reactions = new KaastReactions();
		ForceReactions.register(reactions);
		ControlEffects.registerDurationModifier(reactions);
		SenseTargets.register(CampMine::collectSense);

		ServerTickEvents.END_SERVER_TICK.register(KaastFight::tick);
		// Before the levels save, so the gate stands open and no fight's entities linger.
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> KaastFight.clear());

		KaastCommands.init();
	}

	private KaastFeature() {
	}
}
