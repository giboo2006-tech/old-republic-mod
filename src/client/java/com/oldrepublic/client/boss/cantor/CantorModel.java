package com.oldrepublic.client.boss.cantor;

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
 * The First Cantor (spec MILESTONE_5.md 12.3): a towering spectral Sith in robes of light and shadow,
 * drawn 1.3 times a man's size by the renderer (2.6 blocks). A deep hood over a smooth mask, a
 * shoulder mantle, long bell sleeves, and no legs: the robe trails below him in two tiers and ends
 * in ragged wisps over the floor he floats above.
 *
 * <p>UV layout (128x64; the left half's top is the classic humanoid layout, its legs unused),
 * mirrored in {@code tools/assets/gen_textures_boss.py}: robe 0,32; sleeves 24,32; mantle 0,51;
 * hood 64,0; mask 100,0; upper skirt 64,18; lower skirt 94,18; tatters 64,36 (across) and 64,41 (front to back).
 *
 * <p>Poses, from the synced action: rising (arms at his sides, head bowed); the verse beam (the right
 * arm swept out level); the slam (both fists over his head through the tell, then driven down); chain
 * lightning (the left hand flung out); a verse (head back, arms half open); the engine's hymn (arms
 * spread wide); unravelling (arched back, arms flung wide). The robe stirs as he floats.
 */
public class CantorModel extends HumanoidModel<CantorRenderer.State> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(OldRepublicMod.id("first_cantor"), "main");
	private static final float EASE_TICKS = 6f;
	/** The slam's tell (raid.slamTell's default): the fists come down after it. */
	private static final float SLAM_TELL_TICKS = 20f;

	private final ModelPart skirtUpper;
	private final ModelPart skirtLower;
	private final ModelPart tatters;

	public CantorModel(ModelPart root) {
		super(root);
		this.skirtUpper = body.getChild("skirt_upper");
		this.skirtLower = skirtUpper.getChild("skirt_lower");
		this.tatters = skirtLower.getChild("tatters");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0f);
		PartDefinition root = mesh.getRoot();
		PartDefinition head = root.getChild("head");
		PartDefinition body = root.getChild("body");
		PartDefinition rightArm = root.getChild("right_arm");
		PartDefinition leftArm = root.getChild("left_arm");

		// No legs: he floats, and the robe trails where they would be.
		root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9f, 12f, 0f));
		root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9f, 12f, 0f));
		head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);

		head.addOrReplaceChild("hood", CubeListBuilder.create().texOffs(64, 0)
				.addBox(-4.5f, -8.6f, -4.4f, 9f, 9f, 9f), PartPose.ZERO);
		head.addOrReplaceChild("mask", CubeListBuilder.create().texOffs(100, 0)
				.addBox(-4f, -8f, -4.6f, 8f, 8f, 1f), PartPose.ZERO);

		body.addOrReplaceChild("robe", CubeListBuilder.create().texOffs(0, 32)
				.addBox(-4f, 0f, -2f, 8f, 12f, 4f, new CubeDeformation(0.35f)), PartPose.ZERO);
		body.addOrReplaceChild("mantle", CubeListBuilder.create().texOffs(0, 51)
				.addBox(-5f, -0.5f, -3f, 10f, 4f, 6f), PartPose.ZERO);
		PartDefinition skirtUpper = body.addOrReplaceChild("skirt_upper", CubeListBuilder.create().texOffs(64, 18)
				.addBox(-4.5f, 0f, -3f, 9f, 9f, 6f), PartPose.offset(0f, 12f, 0f));
		PartDefinition skirtLower = skirtUpper.addOrReplaceChild("skirt_lower", CubeListBuilder.create().texOffs(94, 18)
				.addBox(-4f, 0f, -2.5f, 8f, 7f, 5f), PartPose.offsetAndRotation(0f, 9f, 0.3f, 0.1f, 0f, 0f));
		// Two crossed sheets of ragged light below the hem (their texture is mostly clear).
		skirtLower.addOrReplaceChild("tatters", CubeListBuilder.create().texOffs(64, 36)
				.addBox(-4f, 0f, 0f, 8f, 5f, 0f)
				.texOffs(64, 41).addBox(0f, 0f, -2.5f, 0f, 5f, 5f), PartPose.offset(0f, 7f, 0f));

		rightArm.addOrReplaceChild("sleeve", CubeListBuilder.create().texOffs(24, 32)
				.addBox(-3.5f, -2f, -2.5f, 5f, 14f, 5f, new CubeDeformation(0.2f)), PartPose.ZERO);
		leftArm.addOrReplaceChild("sleeve", CubeListBuilder.create().texOffs(24, 32).mirror()
				.addBox(-1.5f, -2f, -2.5f, 5f, 14f, 5f, new CubeDeformation(0.2f)), PartPose.ZERO);

		return LayerDefinition.create(mesh, 128, 64);
	}

	@Override
	public void setupAnim(CantorRenderer.State state) {
		super.setupAnim(state);
		float t = state.ageInTicks;
		// The robe stirs in the song's draught, the hem trailing behind the upper tier.
		skirtUpper.xRot = 0.05f + Mth.sin(t * 0.07f) * 0.04f;
		skirtLower.xRot = 0.1f + Mth.sin(t * 0.07f - 0.8f) * 0.07f;
		tatters.xRot = 0.08f + Mth.sin(t * 0.09f - 1.6f) * 0.12f;
		tatters.zRot = Mth.sin(t * 0.05f) * 0.06f;

		// The arms drift a little at rest (no stride: he never walks).
		rightArm.xRot = Mth.sin(t * 0.05f) * 0.05f;
		leftArm.xRot = -Mth.sin(t * 0.05f) * 0.05f;
		rightArm.zRot = 0.08f;
		leftArm.zRot = -0.08f;

		float ease = Mth.clamp(state.actionTicks / EASE_TICKS, 0f, 1f);

		switch (state.action) {
			case RISE -> {
				head.xRot = 0.5f;
				rightArm.zRot = 0.04f;
				leftArm.zRot = -0.04f;
			}
			case BEAM -> {
				// The right arm swept out level, palm open, the body turned into it.
				rightArm.xRot = Mth.lerp(ease, rightArm.xRot, -Mth.HALF_PI);
				rightArm.yRot = Mth.lerp(ease, rightArm.yRot, -0.35f);
				body.yRot = Mth.lerp(ease, body.yRot, -0.2f);
			}
			case SLAM -> {
				if (state.actionTicks < SLAM_TELL_TICKS) {
					// Both fists raised high over his head, gathering.
					float lift = Mth.clamp(state.actionTicks / (SLAM_TELL_TICKS * 0.6f), 0f, 1f);
					rightArm.xRot = Mth.lerp(lift, rightArm.xRot, -2.9f);
					leftArm.xRot = Mth.lerp(lift, leftArm.xRot, -2.9f);
					rightArm.zRot = Mth.lerp(lift, rightArm.zRot, -0.15f);
					leftArm.zRot = Mth.lerp(lift, leftArm.zRot, 0.15f);
					head.xRot = Mth.lerp(lift, head.xRot, -0.3f);
				} else {
					// Driven down: fists low before him, bent over the blow.
					rightArm.xRot = -0.55f;
					leftArm.xRot = -0.55f;
					rightArm.zRot = -0.1f;
					leftArm.zRot = 0.1f;
					body.xRot = 0.35f;
					head.xRot = 0.3f;
				}
			}
			case CHAIN -> {
				// The left hand flung out along his gaze.
				leftArm.xRot = Mth.lerp(ease, leftArm.xRot, head.xRot - Mth.HALF_PI);
				leftArm.yRot = Mth.lerp(ease, leftArm.yRot, head.yRot + 0.1f);
				leftArm.zRot = 0f;
				body.yRot = Mth.lerp(ease, body.yRot, 0.15f);
			}
			case VERSE -> {
				float sway = Mth.sin(t * 0.09f) * 0.05f;
				head.xRot = Mth.lerp(ease, head.xRot, -0.55f);
				rightArm.xRot = Mth.lerp(ease, rightArm.xRot, -0.5f);
				rightArm.zRot = Mth.lerp(ease, rightArm.zRot, 0.5f + sway);
				leftArm.xRot = Mth.lerp(ease, leftArm.xRot, -0.5f);
				leftArm.zRot = Mth.lerp(ease, leftArm.zRot, -0.5f + sway);
			}
			case HYMN -> {
				float swell = Mth.sin(t * 0.06f) * 0.08f;
				head.xRot = Mth.lerp(ease, head.xRot, -0.35f);
				rightArm.zRot = Mth.lerp(ease, rightArm.zRot, 1.25f + swell);
				leftArm.zRot = Mth.lerp(ease, leftArm.zRot, -1.25f - swell);
				rightArm.xRot = Mth.lerp(ease, rightArm.xRot, -0.2f);
				leftArm.xRot = Mth.lerp(ease, leftArm.xRot, -0.2f);
			}
			case UNRAVEL -> {
				float shake = Mth.sin(t * 1.7f) * 0.04f;
				head.xRot = Mth.lerp(ease, head.xRot, -0.8f);
				body.xRot = Mth.lerp(ease, body.xRot, -0.25f);
				rightArm.zRot = Mth.lerp(ease, rightArm.zRot, 1.6f + shake);
				leftArm.zRot = Mth.lerp(ease, leftArm.zRot, -1.6f - shake);
				rightArm.xRot = Mth.lerp(ease, rightArm.xRot, -0.4f);
				leftArm.xRot = Mth.lerp(ease, leftArm.xRot, -0.4f);
			}
			default -> {
			}
		}
	}
}
