package com.oldrepublic.client.identity;

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
 * Frontier Settler model: the vanilla humanoid body (64x64, upper half in the classic layout) plus
 * a loose work tunic over the body and a rolled hood around the neck, both in the lower half of the
 * texture. A captive kneels: everything above the hips drops by {@link #KNEEL_DROP} pixels, the
 * legs fold back along the ground and the arms are pulled behind the back. The UV layout is
 * mirrored in {@code tools/assets/gen_textures_identity.py}.
 */
public class SettlerModel extends HumanoidModel<SettlerRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(OldRepublicMod.id("frontier_settler"), "main");
	/** How far (model pixels) the upper body sinks when kneeling: thigh length minus half a leg. */
	private static final float KNEEL_DROP = 10f;

	public SettlerModel(ModelPart root) {
		super(root);
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0f);
		PartDefinition root = mesh.getRoot();
		PartDefinition body = root.getChild("body");
		body.addOrReplaceChild("tunic", CubeListBuilder.create().texOffs(16, 32)
				.addBox(-4f, 0f, -2f, 8f, 14f, 4f, new CubeDeformation(0.3f)), PartPose.ZERO);
		body.addOrReplaceChild("hood", CubeListBuilder.create().texOffs(0, 52)
				.addBox(-4.5f, -1f, -2.5f, 9f, 3f, 5f, new CubeDeformation(0.1f)), PartPose.ZERO);
		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	public void setupAnim(SettlerRenderState state) {
		super.setupAnim(state);

		if (!state.captive) {
			return;
		}

		// API-CHECK: ModelPart x/y/z and xRot/yRot/zRot are public mutable fields reset by super.setupAnim.
		head.y += KNEEL_DROP;
		body.y += KNEEL_DROP;
		rightArm.y += KNEEL_DROP;
		leftArm.y += KNEEL_DROP;
		rightLeg.y += KNEEL_DROP;
		leftLeg.y += KNEEL_DROP;

		// Shins folded back flat on the ground.
		rightLeg.xRot = Mth.HALF_PI;
		leftLeg.xRot = Mth.HALF_PI;
		rightLeg.yRot = 0.08f;
		leftLeg.yRot = -0.08f;

		// Wrists bound behind the back; a slow, small sway so they do not look frozen.
		float sway = Mth.sin(state.ageInTicks * 0.05f) * 0.03f;
		rightArm.xRot = 0.55f + sway;
		leftArm.xRot = 0.55f - sway;
		rightArm.yRot = 0f;
		leftArm.yRot = 0f;
		rightArm.zRot = -0.28f;
		leftArm.zRot = 0.28f;
		body.xRot = 0.12f;
		head.xRot = Math.max(head.xRot, 0.15f);
	}
}
