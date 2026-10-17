package com.oldrepublic.gametest;

import java.util.Optional;
import java.util.UUID;

import com.oldrepublic.progression.ForceData;
import com.oldrepublic.quest.QuestStage;
import com.oldrepublic.world.CampPoolElement;
import com.oldrepublic.world.CaveLocator;
import com.oldrepublic.world.WorldEntities;
import com.oldrepublic.world.block.ResonantSealBlock;
import com.oldrepublic.world.trial.CrystalGrant;
import com.oldrepublic.world.trial.ShadowEchoEntity;
import com.oldrepublic.world.trial.TrialManager;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
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

	/**
	 * A camp foundation over a gap (a slope's downhill side): every foundation column inside the
	 * writable box is filled down to the ground, leaves in the way included; the open shaft column
	 * and columns outside the writable box (another chunk's job) are left alone.
	 */
	@GameTest
	public void campFoundationIsSupported(GameTestHelper helper) {
		for (int x = 1; x <= 5; x++) {
			for (int z = 1; z <= 5; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
				helper.setBlock(new BlockPos(x, 5, z), x == 3 && z == 3 ? Blocks.AIR : Blocks.STONE);
			}
		}

		helper.setBlock(new BlockPos(2, 3, 2), Blocks.OAK_LEAVES);
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		BlockPos low = helper.absolutePos(new BlockPos(1, 5, 1));
		BlockPos high = helper.absolutePos(new BlockPos(5, 5 + 15, 5));
		BoundingBox piece = BoundingBox.fromCorners(low, high);
		// The "chunk" being generated ends before x = 5.
		BoundingBox writable = BoundingBox.fromCorners(origin, helper.absolutePos(new BlockPos(4, 7, 7)));
		CampPoolElement.adaptTerrain(helper.getLevel(), piece, writable);

		for (int x = 1; x <= 5; x++) {
			for (int z = 1; z <= 5; z++) {
				for (int y = 2; y <= 4; y++) {
					BlockPos pos = new BlockPos(x, y, z);
					boolean air = helper.getBlockState(pos).isAir();
					boolean shouldFill = x <= 4 && !(x == 3 && z == 3);
					helper.assertTrue(air != shouldFill, Component.literal(pos.toShortString() + (shouldFill ? " must be filled under the foundation" : " must stay open")
							+ ", is " + helper.getBlockState(pos)));
				}
			}
		}

		helper.assertTrue(!helper.getBlockState(new BlockPos(2, 3, 2)).is(Blocks.OAK_LEAVES), Component.literal("leaves under the foundation must be replaced"));
		helper.succeed();
	}

	/**
	 * A camp yard whose surroundings lie two blocks lower: the outer ring steps down two blocks and
	 * the next ring one, so the yard can be walked into; the inner yard keeps its height.
	 */
	@GameTest
	public void campEdgeStepsDownToLowGround(GameTestHelper helper) {
		for (int x = 1; x <= 5; x++) {
			for (int z = 1; z <= 5; z++) {
				for (int y = 1; y <= 4; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
				}

				helper.setBlock(new BlockPos(x, 5, z), Blocks.GRASS_BLOCK);
			}
		}

		BlockPos low = helper.absolutePos(new BlockPos(1, 1, 1));
		BoundingBox piece = BoundingBox.fromCorners(low, helper.absolutePos(new BlockPos(5, 1 + 15, 5)));
		BoundingBox writable = BoundingBox.fromCorners(helper.absolutePos(new BlockPos(0, 1, 0)), helper.absolutePos(new BlockPos(7, 7, 7)));
		// The yard is stood on at y 6 (piece bottom + 5); the ground outside is stood on at y 4.
		int outsideStand = helper.absolutePos(new BlockPos(0, 4, 0)).getY();
		CampPoolElement.adaptTerrain(helper.getLevel(), piece, writable, (x, z) -> outsideStand);

		helper.assertTrue(helper.getBlockState(new BlockPos(1, 3, 3)).is(Blocks.GRASS_BLOCK) && helper.getBlockState(new BlockPos(1, 4, 3)).isAir(),
				Component.literal("the outer ring must step down two blocks to the outside ground"));
		helper.assertTrue(helper.getBlockState(new BlockPos(2, 4, 3)).is(Blocks.GRASS_BLOCK) && helper.getBlockState(new BlockPos(2, 5, 3)).isAir(),
				Component.literal("the second ring must step down one block"));
		helper.assertTrue(helper.getBlockState(new BlockPos(3, 5, 3)).is(Blocks.GRASS_BLOCK), Component.literal("the inner yard must keep its height"));
		helper.succeed();
	}
}
