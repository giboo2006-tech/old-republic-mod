package com.oldrepublic.gametest;

import java.util.Optional;
import java.util.UUID;

import com.oldrepublic.progression.ForceData;
import com.oldrepublic.quest.QuestStage;
import com.oldrepublic.world.CaveLocator;
import com.oldrepublic.world.WorldEntities;
import com.oldrepublic.world.block.ResonantSealBlock;
import com.oldrepublic.world.trial.CrystalGrant;
import com.oldrepublic.world.trial.ShadowEchoEntity;
import com.oldrepublic.world.trial.TrialManager;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

public class WorldGameTests {
	@GameTest
	public void caveTemplatesLoad(GameTestHelper helper) {
		checkTemplate(helper, CaveLocator.CAMP_TEMPLATE, new Vec3i(25, 16, 25));
		checkTemplate(helper, CaveLocator.DESCENT_TEMPLATE, new Vec3i(9, 30, 32));
		checkTemplate(helper, CaveLocator.HEART_TEMPLATE, new Vec3i(45, 22, 48));
		helper.succeed();
	}

	private static void checkTemplate(GameTestHelper helper, Identifier id, Vec3i size) {
		Optional<StructureTemplate> template = helper.getLevel().getStructureManager().get(id);
		helper.assertTrue(template.isPresent(), Component.literal("missing structure template " + id));
		helper.assertTrue(template.get().getSize().equals(size), Component.literal(id + " has size " + template.get().getSize() + ", expected " + size));
	}

	@GameTest
	public void sealRefusesTheUnawakened(GameTestHelper helper) {
		ForceData data = new ForceData();
		helper.assertTrue(ResonantSealBlock.refusalKey(data, false) != null, Component.literal("an un-awakened player must be refused"));
		helper.assertTrue(ResonantSealBlock.refusalKey(data, true) == null, Component.literal("creative players may always open the seal"));

		data.questStage = QuestStage.FOCUS.ordinal();
		helper.assertTrue("message.oldrepublic.seal.refuse_no_push".equals(ResonantSealBlock.refusalKey(data, false)),
				Component.literal("an awakened player without Force Push must be refused for that reason"));

		data.abilities.add("force_push");
		helper.assertTrue(ResonantSealBlock.refusalKey(data, false) == null, Component.literal("awakened with Force Push must open the seal"));
		helper.succeed();
	}

	@GameTest
	public void trialRequirements(GameTestHelper helper) {
		ForceData data = new ForceData();
		data.questStage = QuestStage.FOCUS.ordinal();
		data.abilities.add("force_push");
		helper.assertTrue("message.oldrepublic.trial.refuse_not_called".equals(TrialManager.requirementRefusal(data, false)),
				Component.literal("the trial must wait for the Calling"));

		data.questStage = QuestStage.TRIAL.ordinal();
		helper.assertTrue(TrialManager.requirementRefusal(data, false) == null, Component.literal("a called, awakened pusher may start the trial"));

		data.flags.add(CrystalGrant.TRIAL_COMPLETE);
		helper.assertTrue(TrialManager.requirementRefusal(data, true) != null, Component.literal("a completed trial must never restart, not even in creative"));
		helper.succeed();
	}

	@GameTest
	public void unclaimedEchoIsNotLive(GameTestHelper helper) {
		ShadowEchoEntity echo = new ShadowEchoEntity(WorldEntities.SHADOW_ECHO, helper.getLevel());
		helper.assertTrue(!TrialManager.isLiveEcho(echo), Component.literal("an echo without an owner must not be claimed"));
		helper.assertTrue(CrystalGrant.naturalColor(UUID.randomUUID()) != 0xFF2E2E, Component.literal("natural crystal colours never include red"));
		helper.succeed();
	}
}
