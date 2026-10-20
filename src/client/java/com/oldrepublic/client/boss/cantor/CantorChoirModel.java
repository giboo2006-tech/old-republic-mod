package com.oldrepublic.client.boss.cantor;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * A choir echo of the First Cantor (spec MILESTONE_5.md 12.3): a spectral singer in a plain hooded
 * robe that falls to the floor (no legs). Singing, it lifts its face, its hands clasped before its
 * chest, swaying with the verse; silenced (staggered by a Push, or held), it bows and its arms fall.
 *
 * <p>UV layout (128x64; the left half's top is the classic humanoid layout, its legs unused),
 * mirrored in {@code tools/assets/gen_textures_boss.py}: robe 0,32; sleeves 24,32; hood 64,0;
 * skirt 64,18.
 */
public class CantorChoirModel extends HumanoidModel<CantorChoirRenderer.State> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(OldRepublicMod.id("cantor_choir"), "main");
	/** Ticks the head and arms take to bow or rise again. */
	private static final float EASE_TICKS = 8f;

	public CantorChoirModel(ModelPart root) {
		super(root);
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0f);
		PartDefinition root = mesh.getRoot();
		PartDefinition head = root.getChild("head");
		PartDefinition body = root.getChild("body");

		root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9f, 12f, 0f));
		root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9f, 12f, 0f));
		head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);

		head.addOrReplaceChild("hood", CubeListBuilder.create().texOffs(64, 0)
				.addBox(-4.5f, -8.6f, -4.3f, 9f, 9f, 9f), PartPose.ZERO);
		body.addOrReplaceChild("robe", CubeListBuilder.create().texOffs(0, 32)
				.addBox(-4f, 0f, -2f, 8f, 12f, 4f, new CubeDeformation(0.3f)), PartPose.ZERO);
		body.addOrReplaceChild("skirt", CubeListBuilder.create().texOffs(64, 18)
				.addBox(-4f, 0f, -2.5f, 8f, 12f, 5f, new CubeDeformation(0.2f)), PartPose.offset(0f, 12f, 0f));

		root.getChild("right_arm").addOrReplaceChild("sleeve", CubeListBuilder.create().texOffs(24, 32)
				.addBox(-3f, -2f, -2f, 4f, 12f, 4f, new CubeDeformation(0.3f)), PartPose.ZERO);
		root.getChild("left_arm").addOrReplaceChild("sleeve", CubeListBuilder.create().texOffs(24, 32).mirror()
				.addBox(-1f, -2f, -2f, 4f, 12f, 4f, new CubeDeformation(0.3f)), PartPose.ZERO);

		return LayerDefinition.create(mesh, 128, 64);
	}

	@Override
	public void setupAnim(CantorChoirRenderer.State state) {
		super.setupAnim(state);
		// 0 singing, 1 silenced: eased so a stagger reads as the song breaking off.
		float hush = state.silent ? Mth.clamp(state.silentTicks / EASE_TICKS, 0f, 1f) : 1f - Mth.clamp(state.silentTicks / EASE_TICKS, 0f, 1f);
		float t = state.ageInTicks + state.seed * 11f;
		float sway = Mth.sin(t * 0.08f) * 0.06f;

		// Singing: face lifted, hands clasped before the chest, a slow sway with the verse.
		float singHead = -0.35f + Mth.sin(t * 0.11f) * 0.04f;
		head.xRot = Mth.lerp(hush, singHead, 0.6f);
		body.zRot = sway * (1f - hush);
		rightArm.xRot = Mth.lerp(hush, -1.05f, 0.1f);
		rightArm.yRot = Mth.lerp(hush, -0.55f, 0f);
		rightArm.zRot = Mth.lerp(hush, 0f, 0.06f);
		leftArm.xRot = Mth.lerp(hush, -1.05f, 0.1f);
		leftArm.yRot = Mth.lerp(hush, 0.55f, 0f);
		leftArm.zRot = Mth.lerp(hush, 0f, -0.06f);
		body.xRot = Mth.lerp(hush, 0f, 0.18f);
	}
}
