package com.oldrepublic.gametest;

import java.util.HashSet;
import java.util.Set;

import com.oldrepublic.progression.ForceData;
import com.oldrepublic.quest.JournalEntries;
import com.oldrepublic.quest.QuestRules;
import com.oldrepublic.quest.QuestStage;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;

public class QuestGameTests {
	private static void assertNot(GameTestHelper helper, boolean value, Component message) {
		helper.assertTrue(!value, message);
	}

	@GameTest
	public void stagesOnlyMoveForward(GameTestHelper helper) {
		ForceData data = new ForceData();

		assertNot(helper, QuestRules.advance(data, QuestStage.TRIAL, QuestStage.CALLING), Component.literal("a stirring player must not jump to the trial"));
		helper.assertTrue(QuestRules.advance(data, QuestStage.FOCUS, QuestStage.STIRRING), Component.literal("awakening should reach Focus"));
		assertNot(helper, QuestRules.advance(data, QuestStage.FOCUS, QuestStage.STIRRING), Component.literal("awakening twice must not change anything"));

		data.questStage = QuestStage.CONSTRUCTION.ordinal();
		assertNot(helper, QuestRules.advance(data, QuestStage.TRIAL, QuestStage.CALLING), Component.literal("entering the cave late must not move the stage back"));
		helper.assertTrue(QuestRules.advance(data, QuestStage.COMPLETE, QuestStage.CALLING), Component.literal("building the saber should complete the chapter"));
		helper.assertTrue(data.questStage == QuestStage.COMPLETE.ordinal(), Component.literal("stage should be COMPLETE"));
		helper.succeed();
	}

	@GameTest
	public void awakeningStartsOnlyOnce(GameTestHelper helper) {
		ForceData data = new ForceData();

		helper.assertTrue(QuestRules.awakeningCanStart(data, false), Component.literal("a stirring player can dream"));
		assertNot(helper, QuestRules.awakeningCanStart(data, true), Component.literal("a dream already in progress must not restart"));

		data.questStage = QuestStage.FOCUS.ordinal();
		assertNot(helper, QuestRules.awakeningCanStart(data, false), Component.literal("an awakened player must never dream again"));
		helper.succeed();
	}

	@GameTest
	public void practiceCountsOnlyDuringPractice(GameTestHelper helper) {
		ForceData data = new ForceData();
		data.questStage = QuestStage.FOCUS.ordinal();
		helper.assertTrue(QuestRules.addPractice(data, 3, 5) == -1, Component.literal("pushes before Practice must not count"));

		data.questStage = QuestStage.PRACTICE.ordinal();
		helper.assertTrue(QuestRules.addPractice(data, 0, 5) == -1, Component.literal("pushes with no hostile hit must not count"));
		helper.assertTrue(QuestRules.addPractice(data, 3, 5) == 3, Component.literal("three hits should count three"));
		helper.assertTrue(QuestRules.addPractice(data, 4, 5) == 5, Component.literal("the counter must clamp at the goal"));
		helper.succeed();
	}

	@GameTest
	public void meditationRewardsOncePerDay(GameTestHelper helper) {
		ForceData data = new ForceData();
		assertNot(helper, QuestRules.meditationAvailable(data, 0), Component.literal("no meditation before the Awakening"));

		data.questStage = QuestStage.FOCUS.ordinal();
		helper.assertTrue(QuestRules.meditationAvailable(data, 100), Component.literal("the Focus meditation is always available"));

		data.questStage = QuestStage.PRACTICE.ordinal();
		QuestRules.markMeditated(data, 30_000);
		assertNot(helper, QuestRules.meditationAvailable(data, 40_000), Component.literal("a second meditation the same day must not be available"));
		helper.assertTrue(QuestRules.meditationAvailable(data, 48_000), Component.literal("the next game day should allow training again"));
		helper.succeed();
	}

	@GameTest
	public void journalIdsAreUnique(GameTestHelper helper) {
		Set<String> seen = new HashSet<>();

		for (String id : JournalEntries.ENTRIES) {
			helper.assertTrue(seen.add(JournalEntries.entryFlag(id)), Component.literal("duplicate journal entry " + id));
		}

		for (String id : JournalEntries.CODEX) {
			helper.assertTrue(seen.add(JournalEntries.codexFlag(id)), Component.literal("duplicate codex entry " + id));
		}

		helper.succeed();
	}
}
