package com.oldrepublic.client.enemy.hymn;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.enemy.hymn.Hymnsworn;

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
 * The Hymnsworn's model (spec MILESTONE_5.md 12.1): the vanilla humanoid in the cult's long robes.
 * The hat layer is the deep hood; a cloth veil hangs across the lower face; a short mantle covers the
 * shoulders, a stole with the Hymn's verses runs down the front, wide sleeves, and the robe falls to
 * the ankles. The chanter carries an electrostaff and the blade a vibroblade (the vanilla
 * item-in-hand layer); the storm-caller's hands are empty.
 *
 * <p>UV layout (64x64; the upper half is the classic humanoid layout), mirrored in
 * {@code tools/assets/gen_textures_foes.py}: robe 0,32; sleeves 24,32; stole 40,32; veil 46,32;
 * skirt 0,48; mantle 24,48.
 *
 * <p>Poses, from the synced action: the chant (hands drawn together before the chest, head bowed, a
 * slow sway); the storm-call tell (both arms raised to the sky, head back); a burst of Force
 * lightning (the left hand thrust at the target, fingers spread, the right drawn back).
 */
public class HymnswornModel extends HumanoidModel<HymnswornRenderer.State> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(OldRepublicMod.id("hymnsworn"), "main");
	/** Ticks a pose takes to ease in. */
	private static final float EASE_TICKS = 6f;

	public HymnswornModel(ModelPart root) {
		super(root);
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0f);
		PartDefinition root = mesh.getRoot();
		PartDefinition head = root.getChild("head");
		PartDefinition body = root.getChild("body");
		PartDefinition rightArm = root.getChild("right_arm");
		PartDefinition leftArm = root.getChild("left_arm");

		head.addOrReplaceChild("veil", CubeListBuilder.create().texOffs(46, 32)
				.addBox(-4f, -4f, -4.6f, 8f, 4f, 1f), PartPose.ZERO);

		body.addOrReplaceChild("robe", CubeListBuilder.create().texOffs(0, 32)
				.addBox(-4f, 0f, -2f, 8f, 12f, 4f, new CubeDeformation(0.3f)), PartPose.ZERO);
		body.addOrReplaceChild("mantle", CubeListBuilder.create().texOffs(24, 48)
				.addBox(-4.5f, -0.4f, -2.5f, 9f, 3f, 5f, new CubeDeformation(0.25f)), PartPose.ZERO);
		body.addOrReplaceChild("stole", CubeListBuilder.create().texOffs(40, 32)
				.addBox(-1f, 0.5f, -2.7f, 2f, 14f, 1f), PartPose.ZERO);
		// The robe's lower half, from the waist to the shins (the legs stride beneath it).
		body.addOrReplaceChild("skirt", CubeListBuilder.create().texOffs(0, 48)
				.addBox(-4f, 0f, -2f, 8f, 8f, 4f, new CubeDeformation(0.45f)), PartPose.offset(0f, 12f, 0f));

		rightArm.addOrReplaceChild("sleeve", CubeListBuilder.create().texOffs(24, 32)
				.addBox(-3f, -2f, -2f, 4f, 12f, 4f, new CubeDeformation(0.35f)), PartPose.ZERO);
		leftArm.addOrReplaceChild("sleeve", CubeListBuilder.create().texOffs(24, 32).mirror()
				.addBox(-1f, -2f, -2f, 4f, 12f, 4f, new CubeDeformation(0.35f)), PartPose.ZERO);

		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	public void setupAnim(HymnswornRenderer.State state) {
		super.setupAnim(state);
		float ease = Mth.clamp(state.actionTicks / EASE_TICKS, 0f, 1f);

		switch (state.action) {
			case Hymnsworn.ACTION_CHANT -> {
				// Hands drawn together before the chest, head bowed, a slow sway with the verse.
				float sway = Mth.sin(state.ageInTicks * 0.09f) * 0.05f;
				rightArm.xRot = Mth.lerp(ease, rightArm.xRot, -1.0f);
				rightArm.yRot = Mth.lerp(ease, rightArm.yRot, -0.55f);
				leftArm.xRot = Mth.lerp(ease, leftArm.xRot, -1.0f);
				leftArm.yRot = Mth.lerp(ease, leftArm.yRot, 0.55f);
				head.xRot = Mth.lerp(ease, head.xRot, 0.3f);
				body.zRot = Mth.lerp(ease, body.zRot, sway);
			}
			case Hymnsworn.ACTION_CALL -> {
				// Both arms raised to the storm, head thrown back.
				float tremble = Mth.sin(state.ageInTicks * 1.3f) * 0.04f;
				rightArm.xRot = Mth.lerp(ease, rightArm.xRot, -2.75f + tremble);
				rightArm.zRot = Mth.lerp(ease, rightArm.zRot, 0.3f);
				leftArm.xRot = Mth.lerp(ease, leftArm.xRot, -2.75f - tremble);
				leftArm.zRot = Mth.lerp(ease, leftArm.zRot, -0.3f);
				head.xRot = Mth.lerp(ease, head.xRot, -0.5f);
			}
			case Hymnsworn.ACTION_LIGHTNING -> {
				// The left hand thrust along the look, the right drawn back, the body turned into it.
				leftArm.xRot = head.xRot - Mth.HALF_PI;
				leftArm.yRot = head.yRot + 0.1f;
				leftArm.zRot = 0f;
				rightArm.xRot = 0.35f;
				rightArm.zRot = 0.25f;
				body.yRot = -0.2f;
			}
			default -> {
			}
		}
	}
}
