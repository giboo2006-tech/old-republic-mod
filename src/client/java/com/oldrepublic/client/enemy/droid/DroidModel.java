package com.oldrepublic.client.enemy.droid;

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
 * The Mk IV security droid's model (spec MILESTONE_4.md 10.2): a tall, narrow sentry frame on the
 * humanoid skeleton (the renderer scales it to 2.3 blocks), its head a sensor housing with one eye
 * slit and an antenna, a chest plate and shoulder caps, and a twin rotary blaster at the end of each
 * forearm whose barrels turn while it spins up and fires. The frontal plate shield folds out from
 * the chest while it is up. The hat layer is empty.
 *
 * <p>UV layout (64x64; the upper half is the classic humanoid layout), mirrored in
 * {@code tools/assets/gen_textures_foes.py}: barrels 0,32; antenna 8,32; shield 16,32; shoulder caps
 * 38,32; chest plate 0,46. The eye layer uses the same layout.
 */
public class DroidModel extends HumanoidModel<DroidRenderer.State> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(OldRepublicMod.id("security_droid"), "main");
	/** Barrel turn per tick while spinning up, and while firing. */
	private static final float SPIN_SPEED = 0.5f;
	private static final float FIRE_SPEED = 1.1f;

	private final ModelPart rightBarrels;
	private final ModelPart leftBarrels;
	private final ModelPart shield;

	public DroidModel(ModelPart root) {
		super(root);
		this.rightBarrels = rightArm.getChild("barrels");
		this.leftBarrels = leftArm.getChild("barrels");
		this.shield = body.getChild("shield");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0f);
		PartDefinition root = mesh.getRoot();
		PartDefinition head = root.getChild("head");
		PartDefinition body = root.getChild("body");
		PartDefinition rightArm = root.getChild("right_arm");
		PartDefinition leftArm = root.getChild("left_arm");

		head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
		head.addOrReplaceChild("antenna", CubeListBuilder.create().texOffs(8, 32)
				.addBox(2f, -11f, 1f, 1f, 3f, 1f), PartPose.ZERO);
		body.addOrReplaceChild("chest_plate", CubeListBuilder.create().texOffs(0, 46)
				.addBox(-4f, 0f, -2f, 8f, 12f, 4f, new CubeDeformation(0.3f)), PartPose.ZERO);
		body.addOrReplaceChild("shield", CubeListBuilder.create().texOffs(16, 32)
				.addBox(-5f, -1f, -4.5f, 10f, 13f, 1f), PartPose.ZERO);
		rightArm.addOrReplaceChild("cap", CubeListBuilder.create().texOffs(38, 32)
				.addBox(-3.5f, -2.5f, -2.5f, 5f, 2f, 5f), PartPose.ZERO);
		leftArm.addOrReplaceChild("cap", CubeListBuilder.create().texOffs(38, 32).mirror()
				.addBox(-1.5f, -2.5f, -2.5f, 5f, 2f, 5f), PartPose.ZERO);
		// Each blaster turns about the forearm's own axis.
		barrels(rightArm, -1f, false);
		barrels(leftArm, 1f, true);

		return LayerDefinition.create(mesh, 64, 64);
	}

	private static void barrels(PartDefinition arm, float centreX, boolean mirror) {
		CubeListBuilder cubes = CubeListBuilder.create().texOffs(0, 32);

		if (mirror) {
			cubes.mirror();
		}

		arm.addOrReplaceChild("barrels", cubes
				.addBox(-1.75f, 8f, -0.5f, 1f, 9f, 1f)
				.addBox(0.75f, 8f, -0.5f, 1f, 9f, 1f), PartPose.offset(centreX, 0f, 0f));
	}

	@Override
	public void setupAnim(DroidRenderer.State state) {
		super.setupAnim(state);
		shield.visible = state.shielded;

		boolean levelled = state.spinning || state.firing || state.aggressive;

		if (levelled) {
			// Both blasters levelled along the sensor's look.
			rightArm.xRot = head.xRot - Mth.HALF_PI;
			leftArm.xRot = head.xRot - Mth.HALF_PI;
			rightArm.yRot = head.yRot - 0.05f;
			leftArm.yRot = head.yRot + 0.05f;
			rightArm.zRot = 0f;
			leftArm.zRot = 0f;
		} else {
			// Patrolling: the sensor sweeps slowly from side to side.
			head.yRot += Mth.sin(state.ageInTicks * 0.05f) * 0.6f;
		}

		float turn = state.firing ? FIRE_SPEED : state.spinning ? SPIN_SPEED : 0f;
		rightBarrels.yRot = state.ageInTicks * turn;
		leftBarrels.yRot = -state.ageInTicks * turn;
	}
}
