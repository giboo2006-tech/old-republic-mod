package com.oldrepublic.client.enemy.hask;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.enemy.mandalorian.HunterModel;
import com.oldrepublic.client.enemy.mandalorian.HunterRenderState;
import com.oldrepublic.enemy.hask.HaskWarrior;

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
 * The Clan Hask warrior's model (spec MILESTONE_5.md 12.1): the M4 hunter underneath (its poses: the
 * rifle's aim, the gadget tells, the jet hop), in clan-iron plate with a clan crest along the
 * helmet's crown; the champion carries a tall clan-iron shield on the left forearm. The blade and
 * the champion hold a vibrosword (the vanilla item-in-hand layer) and sling the rifle.
 *
 * <p>The mesh repeats the hunter's parts by name and UV (128x64, {@link HunterModel}'s layout, so the
 * hunter's poses apply), with the crest at 114,0, the shield at 88,0 and its boss at 106,0; mirrored
 * in {@code tools/assets/gen_textures_foes.py}.
 *
 * <p>Poses of its own, from the synced action: the jet-slam tell (crouched to spring, the pack's
 * grips in hand), the shield bash tell (the shield drawn back, the body turned), and the champion's
 * guard (the shield held across the front).
 */
public class HaskModel extends HunterModel {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(OldRepublicMod.id("hask_warrior"), "main");
	private static final float EASE_TICKS = 5f;

	private final ModelPart rifle;
	private final ModelPart scope;
	private final ModelPart shield;

	public HaskModel(ModelPart root) {
		super(root);
		this.rifle = rightArm.getChild("rifle");
		this.scope = rightArm.getChild("scope");
		this.shield = leftArm.getChild("shield");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0f);
		PartDefinition root = mesh.getRoot();
		PartDefinition hat = root.getChild("head").getChild("hat");
		PartDefinition body = root.getChild("body");
		PartDefinition rightArm = root.getChild("right_arm");
		PartDefinition leftArm = root.getChild("left_arm");

		// The hunter's parts, by name and UV (HunterModel finds them by name).
		hat.addOrReplaceChild("rangefinder", CubeListBuilder.create().texOffs(52, 39)
				.addBox(4.3f, -10.5f, -1.5f, 1f, 4f, 1f), PartPose.ZERO);
		body.addOrReplaceChild("cuirass", CubeListBuilder.create().texOffs(0, 32)
				.addBox(-4f, 0f, -2f, 8f, 12f, 4f, new CubeDeformation(0.35f)), PartPose.ZERO);
		body.addOrReplaceChild("jet_pack", CubeListBuilder.create().texOffs(24, 32)
				.addBox(-3.5f, 1f, 2.3f, 7f, 8f, 3f), PartPose.ZERO);
		body.addOrReplaceChild("nozzle_right", CubeListBuilder.create().texOffs(44, 39)
				.addBox(-3f, 9f, 2.8f, 2f, 2f, 2f), PartPose.ZERO);
		body.addOrReplaceChild("nozzle_left", CubeListBuilder.create().texOffs(44, 39)
				.addBox(1f, 9f, 2.8f, 2f, 2f, 2f), PartPose.ZERO);
		body.addOrReplaceChild("kama_front", CubeListBuilder.create().texOffs(44, 32)
				.addBox(-3.5f, 11.5f, -2.8f, 7f, 6f, 1f), PartPose.ZERO);
		body.addOrReplaceChild("kama_back", CubeListBuilder.create().texOffs(44, 32)
				.addBox(-3.5f, 11.5f, 1.8f, 7f, 6f, 1f), PartPose.ZERO);
		rightArm.addOrReplaceChild("pauldron", CubeListBuilder.create().texOffs(16, 48)
				.addBox(-3.5f, -2.6f, -2.5f, 5f, 3f, 5f, new CubeDeformation(0.1f)), PartPose.ZERO);
		leftArm.addOrReplaceChild("pauldron", CubeListBuilder.create().texOffs(16, 48).mirror()
				.addBox(-1.5f, -2.6f, -2.5f, 5f, 3f, 5f, new CubeDeformation(0.1f)), PartPose.ZERO);
		rightArm.addOrReplaceChild("gauntlet", CubeListBuilder.create().texOffs(0, 48)
				.addBox(-3f, 5f, -2f, 4f, 5f, 4f, new CubeDeformation(0.35f)), PartPose.ZERO);
		leftArm.addOrReplaceChild("gauntlet", CubeListBuilder.create().texOffs(0, 48).mirror()
				.addBox(-1f, 5f, -2f, 4f, 5f, 4f, new CubeDeformation(0.35f)), PartPose.ZERO);
		rightArm.addOrReplaceChild("rifle", CubeListBuilder.create().texOffs(64, 0)
				.addBox(-1.6f, 6f, -3.4f, 2f, 14f, 2f), PartPose.ZERO);
		rightArm.addOrReplaceChild("scope", CubeListBuilder.create().texOffs(36, 48)
				.addBox(-1.1f, 9f, -4.4f, 1f, 4f, 1f), PartPose.ZERO);
		PartDefinition axe = rightArm.addOrReplaceChild("axe", CubeListBuilder.create().texOffs(72, 0)
				.addBox(-1.1f, 3f, -0.5f, 1f, 16f, 1f), PartPose.ZERO);
		axe.addOrReplaceChild("blade", CubeListBuilder.create().texOffs(76, 0)
				.addBox(-1.1f, 14f, -4.5f, 1f, 5f, 4f), PartPose.ZERO);
		leftArm.addOrReplaceChild("held_helmet", CubeListBuilder.create().texOffs(32, 0)
				.addBox(-3f, 9f, -4f, 8f, 8f, 8f, new CubeDeformation(-0.6f)), PartPose.ZERO);

		// Clan Hask's own: the crest along the helmet's crown, the champion's shield and its boss.
		hat.addOrReplaceChild("crest", CubeListBuilder.create().texOffs(114, 0)
				.addBox(-0.5f, -10f, -3f, 1f, 2f, 6f), PartPose.ZERO);
		PartDefinition shield = leftArm.addOrReplaceChild("shield", CubeListBuilder.create().texOffs(88, 0)
				.addBox(3.4f, -1f, -4f, 1f, 12f, 8f), PartPose.ZERO);
		shield.addOrReplaceChild("boss", CubeListBuilder.create().texOffs(106, 0)
				.addBox(4.4f, 3.5f, -1.5f, 1f, 3f, 3f), PartPose.ZERO);

		return LayerDefinition.create(mesh, 128, 64);
	}

	@Override
	public void setupAnim(HunterRenderState state) {
		super.setupAnim(state);

		if (!(state instanceof HaskRenderer.HaskRenderState hask)) {
			return;
		}

		boolean champion = hask.variant == HaskWarrior.Variant.CHAMPION;
		rifle.visible = hask.variant == HaskWarrior.Variant.RIFLE;
		scope.visible = rifle.visible;
		shield.visible = champion;

		float ease = Mth.clamp(hask.actionTicks / EASE_TICKS, 0f, 1f);

		switch (hask.action) {
			case HaskWarrior.ACTION_SLAM -> {
				// Crouched to spring, hands to the pack's grips: the pack whines.
				body.xRot = Mth.lerp(ease, body.xRot, 0.35f);
				rightLeg.xRot = Mth.lerp(ease, rightLeg.xRot, -0.4f);
				leftLeg.xRot = Mth.lerp(ease, leftLeg.xRot, -0.4f);
				rightArm.xRot = Mth.lerp(ease, rightArm.xRot, 0.45f);
				leftArm.xRot = Mth.lerp(ease, leftArm.xRot, 0.45f);
			}
			case HaskWarrior.ACTION_BASH -> {
				// The shield drawn back and out, the body turned away to put weight behind it.
				leftArm.xRot = Mth.lerp(ease, leftArm.xRot, 0.55f);
				leftArm.yRot = Mth.lerp(ease, leftArm.yRot, -0.35f);
				leftArm.zRot = Mth.lerp(ease, leftArm.zRot, -0.45f);
				body.yRot = Mth.lerp(ease, body.yRot, 0.4f);
			}
			default -> {
				if (champion && !state.jetting) {
					// The guard: the shield held across the front.
					leftArm.xRot = -0.85f + leftArm.xRot * 0.2f;
					leftArm.yRot = 0.55f;
					leftArm.zRot = 0f;
				}
			}
		}
	}
}
