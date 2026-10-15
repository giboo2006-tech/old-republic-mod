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

/**
 * Crystal Poacher model: the vanilla humanoid body (64x64 texture, upper half in the classic
 * layout) plus spacer gear in the lower half of the texture: a padded jacket, a sleeve on the
 * shooting arm, a scavenged shoulder plate, goggles, and a tool belt with a crystal cutter.
 * UV layout is mirrored in {@code tools/assets/gen_textures_enemy.py}.
 */
public class PoacherModel extends HumanoidModel<PoacherRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(OldRepublicMod.id("crystal_poacher"), "main");

	public PoacherModel(ModelPart root) {
		super(root);
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0f);
		PartDefinition root = mesh.getRoot();
		PartDefinition head = root.getChild("head");
		PartDefinition body = root.getChild("body");
		PartDefinition rightArm = root.getChild("right_arm");
		PartDefinition leftArm = root.getChild("left_arm");

		body.addOrReplaceChild("jacket", CubeListBuilder.create().texOffs(16, 32)
				.addBox(-4f, 0f, -2f, 8f, 12f, 4f, new CubeDeformation(0.25f)), PartPose.ZERO);
		body.addOrReplaceChild("belt", CubeListBuilder.create().texOffs(0, 58)
				.addBox(-4f, 10f, -2f, 8f, 2f, 4f, new CubeDeformation(0.5f)), PartPose.ZERO);
		body.addOrReplaceChild("cutter", CubeListBuilder.create().texOffs(24, 58)
				.addBox(4.2f, 9f, -1f, 1f, 4f, 2f), PartPose.ZERO);
		rightArm.addOrReplaceChild("sleeve", CubeListBuilder.create().texOffs(40, 32)
				.addBox(-3f, -2f, -2f, 4f, 12f, 4f, new CubeDeformation(0.25f)), PartPose.ZERO);
		leftArm.addOrReplaceChild("shoulder_plate", CubeListBuilder.create().texOffs(0, 48)
				.addBox(-1.5f, -3f, -2.5f, 5f, 4f, 5f, new CubeDeformation(0.2f)), PartPose.ZERO);
		head.addOrReplaceChild("goggle_strap", CubeListBuilder.create().texOffs(20, 48)
				.addBox(-4f, -6f, -4f, 8f, 2f, 8f, new CubeDeformation(0.55f)), PartPose.ZERO);
		head.addOrReplaceChild("goggle_lenses", CubeListBuilder.create().texOffs(56, 48)
				.addBox(-3.6f, -6.2f, -5f, 3f, 2f, 1f)
				.addBox(0.6f, -6.2f, -5f, 3f, 2f, 1f), PartPose.ZERO);

		return LayerDefinition.create(mesh, 64, 64);
	}
}
