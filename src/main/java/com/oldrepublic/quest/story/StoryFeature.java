package com.oldrepublic.quest.story;

import com.oldrepublic.mentor.MentorEntities;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

/**
 * Chapter Two's initialisation, called from the quest feature: the story attachment, the mentor
 * entities, dialogue conditions and actions, commands and the events only the story listens to,
 * then Chapter Three's ({@link ChapterThreeFeature}). Ticking, sleep, damage and logout are routed by
 * the quest feature alongside Chapter One.
 */
public final class StoryFeature {
	public static void init() {
		StoryAttachments.init();
		MentorEntities.init();
		StoryDialogue.init();
		StoryCommands.register();
		ChapterThreeFeature.init();

		ServerLivingEntityEvents.AFTER_DEATH.register(StoryService::onEntityDeath);
		ServerEntityEvents.ENTITY_LOAD.register(StoryService::onEntityLoad);
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> StoryService.forget(player));
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (hand == InteractionHand.MAIN_HAND && !level.isClientSide() && player instanceof ServerPlayer serverPlayer
					&& StoryService.onEntityUsed(serverPlayer, entity)) {
				return InteractionResult.SUCCESS;
			}

			return InteractionResult.PASS;
		});
	}

	private StoryFeature() {
	}
}
