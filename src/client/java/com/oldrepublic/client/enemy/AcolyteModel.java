package com.oldrepublic.client.enemy;

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
 * Sith Acolyte model: the vanilla humanoid (the hat layer is the hood) plus a robe over the body, a
 * robe skirt below the waist, wide sleeves and a sash. The three skins (acolyte, Brenn, deserter)
 * share this UV layout, mirrored in {@code tools/assets/gen_textures_enemy.py}: robe 16,32, sleeves
 * 40,32, skirt 0,48, sash 24,48. The free (left) arm thrusts forward during the Force Shove
 * wind-up, and the model kneels when the acolyte yields or loses a spar.
 */
public class AcolyteModel extends HumanoidModel<AcolyteRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(OldRepublicMod.id("sith_acolyte"), "main");

	public AcolyteModel(ModelPart root) {
		super(root);
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0f);
		PartDefinition root = mesh.getRoot();
		PartDefinition body = root.getChild("body");
		PartDefinition rightArm = root.getChild("right_arm");
		PartDefinition leftArm = root.getChild("left_arm");

		body.addOrReplaceChild("robe", CubeListBuilder.create().texOffs(16, 32)
				.addBox(-4f, 0f, -2f, 8f, 12f, 4f, new CubeDeformation(0.3f)), PartPose.ZERO);
		body.addOrReplaceChild("skirt", CubeListBuilder.create().texOffs(0, 48)
				.addBox(-4f, 12f, -2f, 8f, 6f, 4f, new CubeDeformation(0.4f)), PartPose.ZERO);
		body.addOrReplaceChild("sash", CubeListBuilder.create().texOffs(24, 48)
				.addBox(-4f, 9.5f, -2f, 8f, 2f, 4f, new CubeDeformation(0.45f)), PartPose.ZERO);
		rightArm.addOrReplaceChild("sleeve", CubeListBuilder.create().texOffs(40, 32)
				.addBox(-3f, -2f, -2f, 4f, 12f, 4f, new CubeDeformation(0.3f)), PartPose.ZERO);
		leftArm.addOrReplaceChild("sleeve", CubeListBuilder.create().texOffs(40, 32).mirror()
				.addBox(-1f, -2f, -2f, 4f, 12f, 4f, new CubeDeformation(0.3f)), PartPose.ZERO);

		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	public void setupAnim(AcolyteRenderState state) {
		super.setupAnim(state);

		if (state.shoving && state.kneel <= 0f) {
			// The free hand thrust along the look, palm out, the body turned into it.
			leftArm.xRot = head.xRot - Mth.HALF_PI;
			leftArm.yRot = head.yRot + 0.1f;
			leftArm.zRot = 0f;
			body.yRot = body.yRot - 0.2f;
		}

		KneelPose.apply(this, state.kneel);
	}
}
