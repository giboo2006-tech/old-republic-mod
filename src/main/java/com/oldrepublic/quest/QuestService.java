package com.oldrepublic.quest;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.identity.CaptiveOutcome;
import com.oldrepublic.identity.Path;
import com.oldrepublic.identity.YieldOutcome;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.story.StoryService;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Introductory quest logic for "Echoes in the Stone": decides what each {@link QuestHooks} event
 * means for the player's current stage. Stages only move forward; every reward is guarded by a
 * one-shot flag or by the stage change itself, so no hook can be farmed.
 */
final class QuestService {
	private static final String TRIAL_DONE = "quest:trial_done";
	private static final String COMPLETE_REWARDED = "quest:complete_rewarded";

	static void onAwakeningDue(ServerPlayer player, String reason) {
		Awakening.onDue(player, reason);
	}

	static void onLevelUp(ServerPlayer player, int level) {
		// Level-ups have no story meaning in chapter one; the HUD flourish comes from the level_up effect.
	}

	static void onAbilityUsed(ServerPlayer player, String abilityId, int hostileHits) {
		if (!"force_push".equals(abilityId)) {
			return;
		}

		QuestBalance b = QuestFeature.balance();
		ForceData data = Progression.get(player);
		int count = QuestRules.addPractice(data, hostileHits, b.practiceHits);

		if (count < 0) {
			return;
		}

		boolean done = count >= b.practiceHits && QuestRules.advance(data, QuestStage.CALLING, QuestStage.PRACTICE);
		Progression.commit(player, data);
		player.sendOverlayMessage(Component.translatable("message.oldrepublic.practice", count, b.practiceHits));

		if (done) {
			Progression.addXp(player, b.practiceXp, "quest");
			Journal.unlockEntry(player, JournalEntries.THE_PULL);
			Journal.unlockCodex(player, JournalEntries.CODEX_KYBER);
		}
	}

	static void onCaveEntered(ServerPlayer player) {
		if (advance(player, QuestStage.TRIAL, QuestStage.CALLING)) {
			Journal.unlockEntry(player, JournalEntries.VOICES_IN_THE_ROCK);
			Journal.unlockCodex(player, JournalEntries.CODEX_POACHERS);
		}
	}

	static void onTrialStarted(ServerPlayer player) {
		if (Progression.get(player).isAwakened()) {
			Journal.unlockCodex(player, JournalEntries.CODEX_THE_TRIAL);
		}
	}

	static void onTrialCompleted(ServerPlayer player) {
		ForceData data = Progression.get(player);

		if (data.questStage < QuestStage.CALLING.ordinal() || !data.flags.add(TRIAL_DONE)) {
			return;
		}

		QuestRules.advance(data, QuestStage.TRIAL, QuestStage.CALLING);
		Progression.commit(player, data);

		QuestBalance b = QuestFeature.balance();
		Journal.unlockCodex(player, JournalEntries.CODEX_THE_TRIAL);
		Journal.unlockEntry(player, JournalEntries.REFLECTION);
		Progression.addXp(player, b.trialXp, "trial");
		Progression.grantResonanceMilestone(player, "trial", b.trialResonance);
	}

	static void onTrialFailed(ServerPlayer player) {
		if (Progression.get(player).isAwakened()) {
			Journal.unlockEntry(player, JournalEntries.WHAT_WAITS_BELOW);
		}
	}

	static void onCrystalObtained(ServerPlayer player) {
		if (advance(player, QuestStage.CONSTRUCTION, QuestStage.CALLING)) {
			Journal.unlockEntry(player, JournalEntries.THE_CRYSTAL);
			Journal.unlockCodex(player, JournalEntries.CODEX_KYBER);
			Journal.unlockCodex(player, JournalEntries.CODEX_LIGHTSABER);
		}
	}

	static void onPoacherKilled(ServerPlayer player) {
		ForceData data = Progression.get(player);

		if (!data.isAwakened()) {
			return;
		}

		data.counters.merge(QuestRules.POACHER_KILLS, 1, Integer::sum);
		Progression.commit(player, data);
		Journal.unlockEntry(player, JournalEntries.CUTTERS);
		Journal.unlockCodex(player, JournalEntries.CODEX_POACHERS);
	}

	static void onSaberConstructed(ServerPlayer player) {
		ForceData data = Progression.get(player);

		if (data.questStage < QuestStage.CALLING.ordinal()) {
			return;
		}

		QuestRules.advance(data, QuestStage.COMPLETE, QuestStage.CALLING);
		boolean reward = data.flags.add(COMPLETE_REWARDED);
		Progression.commit(player, data);
		Journal.unlockEntry(player, JournalEntries.A_BLADE_OF_MY_OWN);

		if (reward) {
			QuestBalance b = QuestFeature.balance();
			Progression.addXp(player, b.completeXp, "quest");
			Progression.grantSkillPoints(player, b.completeSkillPoints);
			Progression.grantResonanceMilestone(player, "first_saber", b.firstSaberResonance);
			player.sendSystemMessage(Component.translatable("message.oldrepublic.chapter_complete", b.completeSkillPoints));
		}
	}

	static void onBoltDeflected(ServerPlayer player, boolean parried) {
		if (Progression.get(player).isAwakened()) {
			Journal.unlockEntry(player, JournalEntries.RETURNED_FIRE);
		}
	}

	// Milestone 2 hooks: Chapter Two gives them meaning.

	static void onAbilityHit(ServerPlayer player, String abilityId, @Nullable Entity target) {
		StoryService.onAbilityHit(player, abilityId, target);
	}

	static void onSaberTechnique(ServerPlayer player, String technique, @Nullable LivingEntity target, boolean hit) {
		StoryService.onSaberTechnique(player, technique, target, hit);
	}

	static void onYieldResolved(ServerPlayer player, LivingEntity yielded, YieldOutcome outcome) {
		StoryService.onYieldResolved(player, yielded, outcome);
	}

	static void onCaptivesResolved(ServerPlayer player, String groupId, CaptiveOutcome outcome) {
		StoryService.onCaptivesResolved(player, groupId, outcome);
	}

	static void onNodeLearned(ServerPlayer player, String nodeId) {
		// No story step waits on a bought node: lessons and trials grant theirs directly.
	}

	static void onPathChanged(ServerPlayer player, Path path) {
		StoryService.onPathChanged(player, path);
	}

	private static boolean advance(ServerPlayer player, QuestStage to, QuestStage minFrom) {
		ForceData data = Progression.get(player);

		if (!QuestRules.advance(data, to, minFrom)) {
			return false;
		}

		Progression.commit(player, data);
		return true;
	}

	private QuestService() {
	}
}
