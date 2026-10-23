package com.oldrepublic.client.enemy;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.enemy.PoacherCaptain;

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
 * Poacher Captain model: the vanilla humanoid (the hat layer is the crown of her hat) plus a long
 * coat with tails, coat sleeves and a wide hat brim. UV layout, mirrored in
 * {@code tools/assets/gen_textures_enemy.py}: coat 16,32, sleeves 40,32, tails 0,48, brim 24,48.
 *
 * <p>Every attack has a pose tell: the vibroblade drawn back for a swing, raised overhead for the
 * heavy, the pistol (off hand) levelled along the look; staggered she reels back. She kneels when
 * yielded.
 */
public class CaptainModel extends HumanoidModel<CaptainRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(OldRepublicMod.id("poacher_captain"), "main");

	public CaptainModel(ModelPart root) {
		super(root);
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0f);
		PartDefinition root = mesh.getRoot();
		PartDefinition head = root.getChild("head");
		PartDefinition body = root.getChild("body");
		PartDefinition rightArm = root.getChild("right_arm");
		PartDefinition leftArm = root.getChild("left_arm");

		body.addOrReplaceChild("coat", CubeListBuilder.create().texOffs(16, 32)
				.addBox(-4f, 0f, -2f, 8f, 12f, 4f, new CubeDeformation(0.3f)), PartPose.ZERO);
		body.addOrReplaceChild("tails", CubeListBuilder.create().texOffs(0, 48)
				.addBox(-4f, 12f, -2f, 8f, 5f, 4f, new CubeDeformation(0.35f)), PartPose.ZERO);
		rightArm.addOrReplaceChild("sleeve", CubeListBuilder.create().texOffs(40, 32)
				.addBox(-3f, -2f, -2f, 4f, 12f, 4f, new CubeDeformation(0.28f)), PartPose.ZERO);
		leftArm.addOrReplaceChild("sleeve", CubeListBuilder.create().texOffs(40, 32).mirror()
				.addBox(-1f, -2f, -2f, 4f, 12f, 4f, new CubeDeformation(0.28f)), PartPose.ZERO);
		head.addOrReplaceChild("brim", CubeListBuilder.create().texOffs(24, 48)
				.addBox(-5f, -8.6f, -5f, 10f, 1f, 10f), PartPose.ZERO);

		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	public void setupAnim(CaptainRenderState state) {
		super.setupAnim(state);

		if (state.kneel > 0f) {
			KneelPose.apply(this, state.kneel);
			return;
		}

		float ease = Mth.clamp(state.actionTicks / 4f, 0f, 1f);

		switch (state.action) {
			case SWING_TELL -> {
				rightArm.xRot = Mth.lerp(ease, rightArm.xRot, -2.3f);
				rightArm.yRot = Mth.lerp(ease, rightArm.yRot, 0.55f);
				rightArm.zRot = Mth.lerp(ease, rightArm.zRot, 0.35f);
			}
			case HEAVY_WINDUP -> {
				// Raised high overhead, the off hand braced forward.
				rightArm.xRot = Mth.lerp(ease, rightArm.xRot, -3.0f);
				rightArm.yRot = Mth.lerp(ease, rightArm.yRot, 0.15f);
				rightArm.zRot = Mth.lerp(ease, rightArm.zRot, 0.1f);
				leftArm.xRot = Mth.lerp(ease, leftArm.xRot, -0.9f);
				body.xRot = body.xRot - 0.1f * ease;
			}
			case PISTOL_TELL -> {
				leftArm.xRot = Mth.lerp(ease, leftArm.xRot, head.xRot - Mth.HALF_PI);
				leftArm.yRot = Mth.lerp(ease, leftArm.yRot, head.yRot);
				leftArm.zRot = Mth.lerp(ease, leftArm.zRot, 0f);
			}
			case STAGGERED -> {
				rightArm.zRot = Mth.lerp(ease, rightArm.zRot, 0.7f);
				leftArm.zRot = Mth.lerp(ease, leftArm.zRot, -0.7f);
				head.xRot = head.xRot - 0.35f * ease;
				body.xRot = body.xRot - 0.15f * ease;
			}
			case IDLE -> {
			}
		}
	}

	/** True while the pistol tell shows (the glow layer draws at the off hand). */
	static boolean aimingPistol(CaptainRenderState state) {
		return state.kneel <= 0f && state.action == PoacherCaptain.Action.PISTOL_TELL;
	}
}
