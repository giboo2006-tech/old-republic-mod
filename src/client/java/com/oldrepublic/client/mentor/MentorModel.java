package com.oldrepublic.client.mentor;

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
 * Mentor model: the vanilla humanoid body (64x64, upper half in the classic layout) with a robe
 * over the body and a skirt to the knees, plus the Jedi's lowered hood around the shoulders or the
 * Sith's black half-cape down the back (only one shows). Meditating, the mentor kneels with the
 * hands resting on the thighs. The UV layout is mirrored in {@code tools/assets/gen_textures_mentor.py}.
 */
public class MentorModel extends HumanoidModel<MentorRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(OldRepublicMod.id("mentor"), "main");
	/** How far (model pixels) the upper body sinks when kneeling. */
	private static final float KNEEL_DROP = 10f;

	private final ModelPart hood;
	private final ModelPart cape;

	public MentorModel(ModelPart root) {
		super(root);
		this.hood = body.getChild("hood");
		this.cape = body.getChild("cape");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0f);
		PartDefinition body = mesh.getRoot().getChild("body");
		body.addOrReplaceChild("robe", CubeListBuilder.create().texOffs(16, 32)
				.addBox(-4f, 0f, -2f, 8f, 14f, 4f, new CubeDeformation(0.35f)), PartPose.ZERO);
		body.addOrReplaceChild("skirt", CubeListBuilder.create().texOffs(0, 50)
				.addBox(-4.5f, 12f, -2.5f, 9f, 9f, 5f, new CubeDeformation(0.05f)), PartPose.ZERO);
		body.addOrReplaceChild("hood", CubeListBuilder.create().texOffs(36, 50)
				.addBox(-4.5f, -1f, -2.5f, 9f, 3f, 5f, new CubeDeformation(0.25f)), PartPose.ZERO);
		body.addOrReplaceChild("cape", CubeListBuilder.create().texOffs(40, 32)
				.addBox(-4f, 0f, 2.4f, 8f, 16f, 1f, CubeDeformation.NONE), PartPose.ZERO);
		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	public void setupAnim(MentorRenderState state) {
		super.setupAnim(state);
		// API-CHECK: ModelPart#visible is a public mutable field.
		hood.visible = state.jedi;
		cape.visible = !state.jedi;

		if (!state.meditating) {
			return;
		}

		// API-CHECK: ModelPart x/y/z and xRot/yRot/zRot are public mutable fields reset by super.setupAnim.
		head.y += KNEEL_DROP;
		body.y += KNEEL_DROP;
		rightArm.y += KNEEL_DROP;
		leftArm.y += KNEEL_DROP;
		rightLeg.y += KNEEL_DROP;
		leftLeg.y += KNEEL_DROP;

		rightLeg.xRot = Mth.HALF_PI;
		leftLeg.xRot = Mth.HALF_PI;
		rightLeg.yRot = 0.1f;
		leftLeg.yRot = -0.1f;

		// Hands on the thighs, a slow breath in the shoulders.
		float breath = Mth.sin(state.ageInTicks * 0.04f) * 0.02f;
		rightArm.xRot = -0.45f + breath;
		leftArm.xRot = -0.45f + breath;
		rightArm.yRot = 0f;
		leftArm.yRot = 0f;
		rightArm.zRot = 0.08f;
		leftArm.zRot = -0.08f;
		head.xRot = 0.25f;
	}
}
