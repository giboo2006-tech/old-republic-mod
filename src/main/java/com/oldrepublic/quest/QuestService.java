package com.oldrepublic.quest;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.enemy.construct.SentinelVariant;
import com.oldrepublic.enemy.trooper.TrooperRole;
import com.oldrepublic.equipment.ArmourSet;
import com.oldrepublic.holocron.ArchiveChoice;
import com.oldrepublic.holocron.HolocronChoice;
import com.oldrepublic.identity.CaptiveOutcome;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.identity.Path;
import com.oldrepublic.identity.YieldOutcome;
import com.oldrepublic.mission.MissionHooks;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.story.ChapterFiveService;
import com.oldrepublic.quest.story.ChapterFourService;
import com.oldrepublic.quest.story.ChapterThreeService;
import com.oldrepublic.quest.story.StoryService;
import com.oldrepublic.world.site.SitePiece;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Quest logic behind every {@link QuestHooks} event: Chapter One ("Echoes in the Stone") here,
 * Chapter Two, Three and Four in {@code quest.story}, missions and kill reputation in the mission feature
 * ({@link MissionHooks}). Boss hooks are looked up by boss id ({@link #bossChapter}, spec MILESTONE_5.md
 * 12.6): Chapter Four for Torvin Kaast, Chapter Five for Darth Ulvaine and Chapter Three for every
 * other boss. Stages only move forward; every reward is guarded by a
 * one-shot flag or by the stage change itself, so no hook can be farmed.
 */
final class QuestService {
	private static final String TRIAL_DONE = "quest:trial_done";
	private static final String COMPLETE_REWARDED = "quest:complete_rewarded";

	static void onAwakeningDue(ServerPlayer player, String reason) {
		Awakening.onDue(player, reason);
	}

	static void onLevelUp(ServerPlayer player, int level) {
		// Chapter One gives level-ups no meaning (the HUD flourish comes from the level_up effect); Chapter Three's level gate may open.
		ChapterThreeService.onLevelUp(player, level);
		ChapterFourService.onLevelUp(player, level);
	}

	static void onAbilityUsed(ServerPlayer player, String abilityId, int hostileHits) {
		ChapterFourService.onAbilityUsed(player, abilityId, hostileHits);

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
		ChapterFourService.onAbilityHit(player, abilityId, target);
	}

	static void onSaberTechnique(ServerPlayer player, String technique, @Nullable LivingEntity target, boolean hit) {
		StoryService.onSaberTechnique(player, technique, target, hit);
	}

	static void onYieldResolved(ServerPlayer player, LivingEntity yielded, YieldOutcome outcome) {
		StoryService.onYieldResolved(player, yielded, outcome);
		MissionHooks.onYieldResolved(player, yielded, outcome);
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

	// Milestone 3 hooks: Chapter Three (quest.story) and the mission feature give them meaning.

	static void onSiteEntered(ServerPlayer player, SitePiece piece) {
		MissionHooks.onSiteEntered(player, piece);
		ChapterThreeService.onSiteEntered(player, piece);
		ChapterFourService.onSiteEntered(player, piece);
	}

	static void onPuzzleSolved(ServerPlayer player, SitePiece piece, String puzzleId) {
		// No story step waits on a puzzle: the sites reward their own puzzles, and the pedestals behind them report attunement.
	}

	static void onForceSense(ServerPlayer player, int revealed) {
		MissionHooks.onForceSense(player, revealed);
		ChapterFourService.onForceSense(player, revealed);
	}

	static void onCairnTouched(ServerPlayer player, BlockPos pos, int index, boolean cache) {
		ChapterThreeService.onCairnTouched(player, pos, index, cache);
	}

	static void onHolocronAttuned(ServerPlayer player, String holocronId, HolocronChoice choice, boolean first) {
		ChapterThreeService.onHolocronAttuned(player, holocronId, choice, first);
	}

	static void onArchiveResolved(ServerPlayer player, ArchiveChoice choice) {
		ChapterThreeService.onArchiveResolved(player, choice);
	}

	static void onTrooperKilled(ServerPlayer player, LivingEntity trooper, Faction army, TrooperRole role, boolean wasHostile) {
		MissionHooks.onTrooperKilled(player, trooper, army, role, wasHostile);
	}

	static void onTrooperTended(ServerPlayer player, LivingEntity trooper, Faction army) {
		MissionHooks.onTrooperTended(player, trooper, army);
	}

	static void onConstructKilled(ServerPlayer player, LivingEntity construct, SentinelVariant variant) {
		MissionHooks.onConstructKilled(player, construct, variant);
	}

	static void onArmourEquipped(ServerPlayer player, ArmourSet set) {
		ChapterThreeService.onArmourEquipped(player, set);
	}

	// Boss hooks (spec MILESTONE_5.md 12.6): a lookup by boss id. A boss's family keeps its prefix, so an
	// echo's id goes where its boss goes (ChapterFourService.isKaast already reads "kaast" that way).

	/** The boss id prefix of Darth Ulvaine and her echo (12.2). */
	static final String ULVAINE = "ulvaine";

	/** The chapter whose service receives {@code bossId}'s boss hooks: 4 for Kaast, 5 for Ulvaine, 3 for every other boss. */
	static int bossChapter(String bossId) {
		if (ChapterFourService.isKaast(bossId)) {
			return 4;
		}

		return bossId.startsWith(ULVAINE) ? 5 : 3;
	}

	static void onBossPhase(ServerPlayer player, String bossId, int phase) {
		switch (bossChapter(bossId)) {
			case 4 -> {
				// Chapter Four gives Kaast's phases no meaning (M4).
			}
			case 5 -> ChapterFiveService.onBossPhase(player, bossId, phase);
			default -> ChapterThreeService.onBossPhase(player, bossId, phase);
		}
	}

	static void onBossYielded(ServerPlayer owner, String bossId) {
		switch (bossChapter(bossId)) {
			case 4 -> ChapterFourService.onBossYielded(owner, bossId);
			case 5 -> ChapterFiveService.onBossYielded(owner, bossId);
			default -> ChapterThreeService.onBossYielded(owner, bossId);
		}
	}

	static void onBossDefeated(ServerPlayer player, String bossId, BossOutcome outcome, UUID owner, boolean firstClear) {
		switch (bossChapter(bossId)) {
			case 4 -> ChapterFourService.onBossDefeated(player, bossId, outcome, owner, firstClear);
			case 5 -> ChapterFiveService.onBossDefeated(player, bossId, outcome, owner, firstClear);
			default -> ChapterThreeService.onBossDefeated(player, bossId, outcome, owner, firstClear);
		}
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
