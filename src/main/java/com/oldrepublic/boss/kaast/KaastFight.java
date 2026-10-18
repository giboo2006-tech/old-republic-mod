package com.oldrepublic.boss.kaast;

import com.oldrepublic.quest.story.KaastOutcome;

import net.minecraft.server.level.ServerPlayer;

/** The end of the Kaast duel (spec MILESTONE_4.md 8.6, 10.5). Phase 0 stub; W6 writes it. */
public final class KaastFight {
	/**
	 * Resolves the owner's yielded duel with {@code outcome} (W2's {@code kaast:resolve} dialogue
	 * action calls it): the scene's staging and the boss defeat hooks. W2 grants the rewards.
	 */
	public static void resolve(ServerPlayer owner, KaastOutcome outcome) {
	}

	private KaastFight() {
	}
}
