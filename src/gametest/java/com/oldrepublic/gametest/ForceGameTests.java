package com.oldrepublic.gametest;

import com.oldrepublic.force.BranchChoice;
import com.oldrepublic.force.ForceEffects;
import com.oldrepublic.force.ForcePush;
import com.oldrepublic.force.PushBalance;
import com.oldrepublic.force.PushBranch;
import com.oldrepublic.force.PushShape;
import com.oldrepublic.force.PushTargeting;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.quest.QuestStage;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/** Force Push rules that can be checked without a live player: shapes, costs, cone geometry, branch choice. */
public class ForceGameTests {
	private static boolean near(float a, float b) {
		return Math.abs(a - b) < 1.0e-3f;
	}

	@GameTest
	public void baseShapeMatchesSpec(GameTestHelper helper) {
		PushBalance b = new PushBalance();
		PushShape weak = PushShape.of(b, PushBranch.NONE, 0, 0f);
		PushShape full = PushShape.of(b, PushBranch.NONE, 0, 1f);
		helper.assertTrue(near(weak.halfAngle() * 2, 70f) && near(weak.range(), 6f), Component.literal("base cone must be 70 degrees, 6 blocks"));
		helper.assertTrue(near(weak.knockback(), 1.1f) && near(full.knockback(), 1.8f), Component.literal("knockback must scale 1.1 to 1.8"));
		helper.assertTrue(near(weak.cost(), 18f) && near(full.cost(), 28f), Component.literal("cost must scale 18 to 28"));
		helper.assertTrue(weak.cooldownTicks() == 70, Component.literal("cooldown must be 3.5 s"));
		helper.succeed();
	}

	@GameTest
	public void branchShapesMatchSpec(GameTestHelper helper) {
		PushBalance b = new PushBalance();
		PushShape repulse = PushShape.of(b, PushBranch.REPULSE_WAVE, 0, 1f);
		PushShape focused = PushShape.of(b, PushBranch.FOCUSED_BLAST, 0, 1f);
		PushShape pulse = PushShape.of(b, PushBranch.DEFLECTING_PULSE, 0, 1f);
		helper.assertTrue(near(repulse.halfAngle() * 2, 130f) && near(repulse.range(), 7f) && near(repulse.knockback(), 1.8f * 0.8f),
				Component.literal("Repulse Wave: 130 degrees, range 7, knockback x0.8"));
		helper.assertTrue(near(focused.halfAngle() * 2, 25f) && near(focused.range(), 12f) && near(focused.knockback(), 1.8f * 1.8f),
				Component.literal("Focused Blast: 25 degrees, range 12, knockback x1.8"));
		helper.assertTrue(pulse.spherical() && near(pulse.range(), 4f) && near(pulse.knockback(), 1.8f * 0.6f) && pulse.cooldownTicks() == 42,
				Component.literal("Deflecting Pulse: 360 degrees, radius 4, knockback x0.6, cooldown x0.6"));
		helper.succeed();
	}

	@GameTest
	public void masteryScalesRangeAndCostOnly(GameTestHelper helper) {
		PushBalance b = new PushBalance();
		PushShape rank0 = PushShape.of(b, PushBranch.FOCUSED_BLAST, 0, 0.5f);
		PushShape rank3 = PushShape.of(b, PushBranch.FOCUSED_BLAST, 3, 0.5f);
		helper.assertTrue(rank3.range() > rank0.range() && rank3.cost() < rank0.cost(), Component.literal("mastery should raise range and lower cost"));
		helper.assertTrue(near(rank3.halfAngle(), rank0.halfAngle()) && near(rank3.knockback(), rank0.knockback()), Component.literal("mastery must not change the branch"));
		helper.succeed();
	}

	@GameTest
	public void chargeNeedsMinimumHold(GameTestHelper helper) {
		PushBalance b = new PushBalance();
		helper.assertTrue(PushShape.chargeFromHold(b, 4) < 0f, Component.literal("below 0.25 s must cancel"));
		helper.assertTrue(near(PushShape.chargeFromHold(b, 5), 0f), Component.literal("0.25 s is the weakest push"));
		helper.assertTrue(near(PushShape.chargeFromHold(b, 20), 1f) && near(PushShape.chargeFromHold(b, 80), 1f), Component.literal("1 s and longer is full"));
		helper.succeed();
	}

	@GameTest
	public void coneGeometry(GameTestHelper helper) {
		Vec3 eye = Vec3.ZERO;
		Vec3 look = new Vec3(0, 0, 1);
		helper.assertTrue(PushTargeting.inShape(eye, look, new Vec3(0, 0, 5), 6, 35, false), Component.literal("straight ahead is inside"));
		helper.assertTrue(PushTargeting.inShape(eye, look, new Vec3(2, 0, 4), 6, 35, false), Component.literal("27 degrees off is inside a 70 degree cone"));
		helper.assertTrue(!PushTargeting.inShape(eye, look, new Vec3(4, 0, 4), 6, 35, false), Component.literal("45 degrees off is outside"));
		helper.assertTrue(!PushTargeting.inShape(eye, look, new Vec3(0, 0, 7), 6, 35, false), Component.literal("beyond range is outside"));
		helper.assertTrue(PushTargeting.inShape(eye, look, new Vec3(0, 0, -3), 4, 180, true), Component.literal("the pulse reaches behind"));
		helper.succeed();
	}

	@GameTest
	public void releaseVariantRoundTrips(GameTestHelper helper) {
		PushShape shape = PushShape.of(new PushBalance(), PushBranch.FOCUSED_BLAST, 2, 1f);
		int variant = ForceEffects.packRelease(PushBranch.FOCUSED_BLAST, shape);
		helper.assertTrue(ForceEffects.releaseBranch(variant) == PushBranch.FOCUSED_BLAST, Component.literal("branch lost"));
		helper.assertTrue(Math.abs(ForceEffects.releaseRange(variant) - shape.range()) <= 0.125f, Component.literal("range lost"));
		helper.assertTrue(Math.abs(ForceEffects.releaseHalfAngle(variant) - shape.halfAngle()) <= 0.5f, Component.literal("angle lost"));
		helper.succeed();
	}

	@GameTest
	public void branchChoiceRules(GameTestHelper helper) {
		PushBalance b = new PushBalance();
		ForceData data = new ForceData();
		helper.assertTrue(BranchChoice.check(data, PushBranch.REPULSE_WAVE, b) == BranchChoice.Result.NOT_AWAKENED, Component.literal("needs awakening"));
		data.questStage = QuestStage.PRACTICE.ordinal();
		helper.assertTrue(BranchChoice.check(data, PushBranch.REPULSE_WAVE, b) == BranchChoice.Result.NOT_LEARNED, Component.literal("needs push"));
		data.abilities.add(ForcePush.ABILITY_ID);
		data.level = 2;
		data.skillPoints = 5;
		helper.assertTrue(BranchChoice.check(data, PushBranch.REPULSE_WAVE, b) == BranchChoice.Result.LEVEL_TOO_LOW, Component.literal("needs level 3"));
		data.level = 3;
		data.skillPoints = 0;
		helper.assertTrue(BranchChoice.check(data, PushBranch.REPULSE_WAVE, b) == BranchChoice.Result.NOT_ENOUGH_POINTS, Component.literal("first choice costs 1"));
		data.skillPoints = 1;
		helper.assertTrue(BranchChoice.check(data, PushBranch.REPULSE_WAVE, b) == BranchChoice.Result.OK, Component.literal("1 point is enough the first time"));
		helper.assertTrue(BranchChoice.check(data, PushBranch.NONE, b) == BranchChoice.Result.INVALID, Component.literal("cannot choose no branch"));
		data.pushBranch = PushBranch.REPULSE_WAVE.id();
		helper.assertTrue(BranchChoice.check(data, PushBranch.REPULSE_WAVE, b) == BranchChoice.Result.ALREADY_CHOSEN, Component.literal("same branch is rejected"));
		data.skillPoints = 2;
		helper.assertTrue(BranchChoice.check(data, PushBranch.FOCUSED_BLAST, b) == BranchChoice.Result.NOT_ENOUGH_POINTS, Component.literal("re-choosing costs 3"));
		data.skillPoints = 3;
		helper.assertTrue(BranchChoice.check(data, PushBranch.FOCUSED_BLAST, b) == BranchChoice.Result.OK, Component.literal("3 points allow a re-choice"));
		helper.succeed();
	}
}
