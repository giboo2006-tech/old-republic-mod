package com.oldrepublic.quest.turning;

import com.oldrepublic.quest.Guidance;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/**
 * Milestone 5 server and common initialisation for paths and ranks (section 8): the Fall and the
 * Return, their costs and ceremonies, the cold mentors and rank 4 (Master, Darth and Wayfarer)
 * (spec MILESTONE_5.md). Owned by W5 paths and ranks; the lead calls it from {@code OldRepublicMod}
 * after the Phase 0 contracts, so the worker never edits the mod's entry point.
 *
 * <p>Registers the {@code watch_holocron} item, the {@code rank_four_checklist} payload, the dialogue conditions, actions
 * and redirects ({@link TurningDialogue}), the other-path talk of the mentors, the server events and the slow tick
 * ({@link TurningService}), the uses of mentors, messengers, the post's people and the Compass ({@link Mentors}), the
 * Calling during a task ({@link TurningGuidance}) and the {@code /oldrepublic turning} commands.
 */
public final class TurningFeature {
	public static void init() {
		TurningItems.init();
		PayloadTypeRegistry.clientboundPlay().register(RankFourChecklistPayload.TYPE, RankFourChecklistPayload.CODEC);
		TurningDialogue.init();
		TurningService.registerEvents();
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> Mentors.onUseEntity(player, level, hand, entity));
		UseBlockCallback.EVENT.register(Mentors::onUseBlock);
		Guidance.register(TurningGuidance::request);
		TurningCommands.init();
	}

	private TurningFeature() {
	}
}
