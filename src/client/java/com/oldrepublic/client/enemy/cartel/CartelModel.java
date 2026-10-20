package com.oldrepublic.client.enemy.cartel;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.enemy.cartel.CartelEnforcer;

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
 * The cartel enforcer's model (spec MILESTONE_5.md 12.1): the vanilla humanoid in a sand-worn duster
 * with the kajidic's colours. The hat layer is a head-wrap with dust goggles pushed up on it; a long
 * jacket with its tails to the knees front and back, jacket sleeves, and for the captain a single
 * armoured pauldron on the left shoulder. The weapons are items in hand (the vanilla item-in-hand
 * layer): the gunman's rifle, the bruiser's vibrosword, the captain's pistol.
 *
 * <p>UV layout (64x64; the upper half is the classic humanoid layout), mirrored in
 * {@code tools/assets/gen_textures_foes.py}: jacket 0,32; sleeves 24,32; tails 40,32; pauldron 0,48;
 * goggles 40,40.
 *
 * <p>Poses, from the synced state: the rifle shouldered while fighting (the renderer's arm pose); the
 * captain's pistol levelled along the look while its shot charges; the frag grenade's arm drawn back
 * and up before the throw.
 */
public class CartelModel extends HumanoidModel<CartelRenderer.State> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(OldRepublicMod.id("cartel_enforcer"), "main");
	private static final float EASE_TICKS = 5f;

	private final ModelPart pauldron;
	private final ModelPart tailFront;
	private final ModelPart tailBack;

	public CartelModel(ModelPart root) {
		super(root);
		this.pauldron = leftArm.getChild("pauldron");
		this.tailFront = body.getChild("tail_front");
		this.tailBack = body.getChild("tail_back");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0f);
		PartDefinition root = mesh.getRoot();
		PartDefinition hat = root.getChild("head").getChild("hat");
		PartDefinition body = root.getChild("body");
		PartDefinition rightArm = root.getChild("right_arm");
		PartDefinition leftArm = root.getChild("left_arm");

		hat.addOrReplaceChild("goggles", CubeListBuilder.create().texOffs(40, 40)
				.addBox(-4.5f, -8.2f, -4.8f, 9f, 2f, 1f), PartPose.ZERO);

		body.addOrReplaceChild("jacket", CubeListBuilder.create().texOffs(0, 32)
				.addBox(-4f, 0f, -2f, 8f, 12f, 4f, new CubeDeformation(0.3f)), PartPose.ZERO);
		body.addOrReplaceChild("tail_front", CubeListBuilder.create().texOffs(40, 32)
				.addBox(-4f, 0f, -0.5f, 8f, 6f, 1f), PartPose.offset(0f, 12f, -2.3f));
		body.addOrReplaceChild("tail_back", CubeListBuilder.create().texOffs(40, 32)
				.addBox(-4f, 0f, -0.5f, 8f, 6f, 1f), PartPose.offset(0f, 12f, 2.3f));

		rightArm.addOrReplaceChild("sleeve", CubeListBuilder.create().texOffs(24, 32)
				.addBox(-3f, -2f, -2f, 4f, 12f, 4f, new CubeDeformation(0.3f)), PartPose.ZERO);
		leftArm.addOrReplaceChild("sleeve", CubeListBuilder.create().texOffs(24, 32).mirror()
				.addBox(-1f, -2f, -2f, 4f, 12f, 4f, new CubeDeformation(0.3f)), PartPose.ZERO);
		leftArm.addOrReplaceChild("pauldron", CubeListBuilder.create().texOffs(0, 48)
				.addBox(-1.5f, -2.8f, -2.5f, 5f, 3f, 5f, new CubeDeformation(0.15f)), PartPose.ZERO);

		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	public void setupAnim(CartelRenderer.State state) {
		super.setupAnim(state);
		pauldron.visible = state.variant == CartelEnforcer.Variant.CAPTAIN;

		// The jacket's tails swing back a little with the stride.
		float swing = Math.abs(rightLeg.xRot - leftLeg.xRot) * 0.25f;
		tailFront.xRot = -swing;
		tailBack.xRot = swing;

		float ease = Mth.clamp(state.tellTicks / EASE_TICKS, 0f, 1f);

		if (state.action == CartelEnforcer.ACTION_GRENADE) {
			// The frag drawn back and up in the off hand, the body turned into the throw.
			leftArm.xRot = Mth.lerp(ease, leftArm.xRot, -2.5f);
			leftArm.yRot = Mth.lerp(ease, leftArm.yRot, 0.3f);
			leftArm.zRot = Mth.lerp(ease, leftArm.zRot, -0.2f);
			body.yRot = Mth.lerp(ease, body.yRot, 0.3f);
			return;
		}

		if (state.charging && state.variant == CartelEnforcer.Variant.CAPTAIN) {
			// The pistol levelled along the look.
			rightArm.xRot = Mth.lerp(ease, rightArm.xRot, head.xRot - Mth.HALF_PI);
			rightArm.yRot = Mth.lerp(ease, rightArm.yRot, head.yRot);
			rightArm.zRot = Mth.lerp(ease, rightArm.zRot, 0f);
		}
	}
}
