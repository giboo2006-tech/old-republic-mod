package com.oldrepublic.client.enemy.droid;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * The seeker droid's model (spec MILESTONE_4.md 10.7), also Rattle's: a faceted ball with a
 * single lens in front, a cluster of three antennae on top and two short manipulator prongs below.
 * It hovers with a slow bob, and the prongs twitch. Model space is the vanilla one (y down, the
 * ground at y = 24); the ball floats half a block up.
 *
 * <p>UV layout (32x32), mirrored in {@code tools/assets/gen_textures_foes.py}: ball 0,0; lens
 * 24,0; antenna 24,4; prong 28,4.
 */
public class SeekerModel extends EntityModel<SeekerRenderer.State> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(OldRepublicMod.id("seeker_droid"), "main");

	private final ModelPart ball;
	private final ModelPart rightProng;
	private final ModelPart leftProng;

	public SeekerModel(ModelPart root) {
		super(root);
		this.ball = root.getChild("ball");
		this.rightProng = ball.getChild("prong_right");
		this.leftProng = ball.getChild("prong_left");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition ball = root.addOrReplaceChild("ball", CubeListBuilder.create().texOffs(0, 0)
				.addBox(-3f, -3f, -3f, 6f, 6f, 6f), PartPose.offset(0f, 18f, 0f));
		ball.addOrReplaceChild("lens", CubeListBuilder.create().texOffs(24, 0)
				.addBox(-1f, -1f, -3.6f, 2f, 2f, 1f), PartPose.ZERO);
		ball.addOrReplaceChild("antenna_a", CubeListBuilder.create().texOffs(24, 4)
				.addBox(-0.5f, -7f, -0.5f, 1f, 4f, 1f), PartPose.ZERO);
		ball.addOrReplaceChild("antenna_b", CubeListBuilder.create().texOffs(24, 4)
				.addBox(-0.5f, -6f, -0.5f, 1f, 3f, 1f), PartPose.offsetAndRotation(1.5f, 0f, 1f, 0.3f, 0f, -0.35f));
		ball.addOrReplaceChild("antenna_c", CubeListBuilder.create().texOffs(24, 4)
				.addBox(-0.5f, -6f, -0.5f, 1f, 3f, 1f), PartPose.offsetAndRotation(-1.5f, 0f, 1f, 0.3f, 0f, 0.35f));
		ball.addOrReplaceChild("prong_right", CubeListBuilder.create().texOffs(28, 4)
				.addBox(-0.5f, 0f, -0.5f, 1f, 3f, 1f), PartPose.offset(-1.5f, 3f, -1f));
		ball.addOrReplaceChild("prong_left", CubeListBuilder.create().texOffs(28, 4)
				.addBox(-0.5f, 0f, -0.5f, 1f, 3f, 1f), PartPose.offset(1.5f, 3f, -1f));
		return LayerDefinition.create(mesh, 32, 32);
	}

	@Override
	public void setupAnim(SeekerRenderer.State state) {
		super.setupAnim(state);
		ball.y += Mth.sin(state.ageInTicks * 0.15f) * 0.8f;
		ball.yRot = state.yRot * Mth.DEG_TO_RAD;
		ball.xRot = state.xRot * Mth.DEG_TO_RAD;
		rightProng.xRot = 0.2f + Mth.sin(state.ageInTicks * 0.6f) * 0.15f;
		leftProng.xRot = 0.2f + Mth.cos(state.ageInTicks * 0.55f) * 0.15f;
	}
}
