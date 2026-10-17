package com.oldrepublic.quest.story;

import com.oldrepublic.force.sense.SenseTargets;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;

/**
 * Chapter Three's initialisation, called from {@link StoryFeature#init}: its dialogue conditions and
 * actions, the {@code /oldrepublic c3} commands, the STORY provider of the journal's Calling, the
 * Force Sense provider for the player's own cairns and caches, and the Courier's Fall pedestal use
 * that opens the Pathfinder's dialogue. Ticking, sleep, damage, logout and entity loads reach the
 * chapter through {@link StoryService}.
 */
public final class ChapterThreeFeature {
	public static void init() {
		ChapterThreeDialogue.init();
		ChapterThreeCommands.register();
		StoryGuidance.init();
		SenseTargets.register(new CairnSense());
		UseBlockCallback.EVENT.register(RankThree::onUseBlock);
	}

	private ChapterThreeFeature() {
	}
}
