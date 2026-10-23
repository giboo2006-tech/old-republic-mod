package com.oldrepublic.client.enemy.stormback;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.enemy.stormback.Stormback;

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
 * The stormback's model (spec MILESTONE_5.md 12.1): a heavy grazer of Tyrvann, low-slung and broad,
 * with a row of slate plates like split basalt along its spine (they crackle when it is charged), a
 * low head under a broad brow shield with two short forward horns, a heavy jaw, four thick legs and a
 * short tail.
 *
 * <p>Poses: walking and grazing; the charge tell (head down behind the brow shield, a forehoof
 * pawing); the charge (head low, a full gallop); stunned (head hanging and swaying); the roar
 * (reared on its hind legs, head thrown back, jaw wide).
 *
 * <p>UV layout (128x64), mirrored in {@code tools/assets/gen_textures_foes.py}: body 0,0; head 72,0;
 * brow 72,18; horns 96,18; plates 0,34; legs 18,34; tail 38,34; jaw 46,34.
 */
public class StormbackModel extends EntityModel<StormbackRenderer.State> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(OldRepublicMod.id("stormback"), "main");
	private static final float EASE_TICKS = 6f;

	private final ModelPart body;
	private final ModelPart head;
	private final ModelPart jaw;
	private final ModelPart tail;
	private final ModelPart rightFrontLeg;
	private final ModelPart leftFrontLeg;
	private final ModelPart rightHindLeg;
	private final ModelPart leftHindLeg;

	public StormbackModel(ModelPart root) {
		super(root);
		this.body = root.getChild("body");
		this.head = body.getChild("head");
		this.jaw = head.getChild("jaw");
		this.tail = body.getChild("tail");
		this.rightFrontLeg = root.getChild("right_front_leg");
		this.leftFrontLeg = root.getChild("left_front_leg");
		this.rightHindLeg = root.getChild("right_hind_leg");
		this.leftHindLeg = root.getChild("left_hind_leg");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0)
				.addBox(-7f, -12f, -11f, 14f, 12f, 22f), PartPose.offset(0f, 14f, 0f));

		// The slate plates along the spine, tallest over the shoulders.
		float[] heights = {6f, 7f, 6f, 5f};

		for (int i = 0; i < heights.length; i++) {
			float tilt = -0.25f + i * 0.12f;
			body.addOrReplaceChild("plate" + i, CubeListBuilder.create().texOffs(0, 34)
					.addBox(-1f, -heights[i], -3.5f, 2f, 6f, 7f), PartPose.offsetAndRotation(i % 2 == 0 ? -1.2f : 1.2f, -11f, -7f + i * 5f, tilt, 0f,
							i % 2 == 0 ? -0.12f : 0.12f));
		}

		PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(72, 0)
				.addBox(-4f, -4f, -10f, 8f, 8f, 10f), PartPose.offsetAndRotation(0f, -6f, -11f, 0.25f, 0f, 0f));
		head.addOrReplaceChild("brow", CubeListBuilder.create().texOffs(72, 18)
				.addBox(-5f, -5.5f, -9f, 10f, 4f, 2f), PartPose.ZERO);
		head.addOrReplaceChild("horn_right", CubeListBuilder.create().texOffs(96, 18)
				.addBox(-1f, -1f, -5f, 2f, 2f, 5f), PartPose.offsetAndRotation(-3.5f, -4f, -9f, 0.35f, 0.2f, 0f));
		head.addOrReplaceChild("horn_left", CubeListBuilder.create().texOffs(96, 18).mirror()
				.addBox(-1f, -1f, -5f, 2f, 2f, 5f), PartPose.offsetAndRotation(3.5f, -4f, -9f, 0.35f, -0.2f, 0f));
		head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(46, 34)
				.addBox(-3f, 0f, -4f, 6f, 3f, 4f), PartPose.offset(0f, 3.5f, -5.5f));

		body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(38, 34)
				.addBox(-1f, 0f, -1f, 2f, 8f, 2f), PartPose.offsetAndRotation(0f, -10f, 11f, 0.45f, 0f, 0f));

		CubeListBuilder leg = CubeListBuilder.create().texOffs(18, 34).addBox(-2.5f, 0f, -2.5f, 5f, 10f, 5f);
		CubeListBuilder mirrored = CubeListBuilder.create().texOffs(18, 34).mirror().addBox(-2.5f, 0f, -2.5f, 5f, 10f, 5f);
		root.addOrReplaceChild("right_front_leg", leg, PartPose.offset(-4.5f, 14f, -7.5f));
		root.addOrReplaceChild("left_front_leg", mirrored, PartPose.offset(4.5f, 14f, -7.5f));
		root.addOrReplaceChild("right_hind_leg", leg, PartPose.offset(-4.5f, 14f, 7.5f));
		root.addOrReplaceChild("left_hind_leg", mirrored, PartPose.offset(4.5f, 14f, 7.5f));
		return LayerDefinition.create(mesh, 128, 64);
	}

	@Override
	public void setupAnim(StormbackRenderer.State state) {
		super.setupAnim(state);

		boolean charging = state.state == Stormback.STATE_CHARGING;
		float swing = state.walkAnimationSpeed * (charging ? 1.3f : 1.1f);
		float pos = state.walkAnimationPos * (charging ? 0.75f : 0.6f);
		rightFrontLeg.xRot = Mth.cos(pos) * swing;
		leftFrontLeg.xRot = Mth.cos(pos + Mth.PI) * swing;
		rightHindLeg.xRot = Mth.cos(pos + Mth.PI) * swing;
		leftHindLeg.xRot = Mth.cos(pos) * swing;
		tail.zRot = Mth.sin(state.ageInTicks * 0.07f) * 0.18f;
		head.xRot += state.xRot * Mth.DEG_TO_RAD * 0.4f;
		head.yRot = state.yRot * Mth.DEG_TO_RAD * 0.5f;
		float ease = Mth.clamp(state.stateTicks / EASE_TICKS, 0f, 1f);

		switch (state.state) {
			case Stormback.STATE_TELL -> {
				// Head down behind the brow shield, a forehoof pawing the ground.
				head.xRot = Mth.lerp(ease, head.xRot, 0.85f);
				rightFrontLeg.xRot = Mth.lerp(ease, rightFrontLeg.xRot, -0.3f + Mth.sin(state.ageInTicks * 0.9f) * 0.35f);
			}
			case Stormback.STATE_CHARGING -> head.xRot = 0.8f;
			case Stormback.STATE_STUNNED -> {
				// Dazed: the head hangs and sways.
				head.xRot = 0.95f;
				head.yRot = Mth.sin(state.ageInTicks * 0.35f) * 0.35f;
				head.zRot = Mth.sin(state.ageInTicks * 0.25f) * 0.2f;
			}
			case Stormback.STATE_ROAR -> {
				// Reared on the hind legs, head thrown back to the sky, jaw wide.
				body.xRot = Mth.lerp(ease, body.xRot, -0.55f);
				body.y = Mth.lerp(ease, body.y, 12f);
				rightFrontLeg.xRot = Mth.lerp(ease, rightFrontLeg.xRot, -1.1f);
				leftFrontLeg.xRot = Mth.lerp(ease, leftFrontLeg.xRot, -0.9f);
				rightFrontLeg.y = Mth.lerp(ease, rightFrontLeg.y, 8f);
				leftFrontLeg.y = Mth.lerp(ease, leftFrontLeg.y, 8f);
				rightFrontLeg.z = Mth.lerp(ease, rightFrontLeg.z, -6f);
				leftFrontLeg.z = Mth.lerp(ease, leftFrontLeg.z, -6f);
				head.xRot = Mth.lerp(ease, head.xRot, -0.7f);
				jaw.xRot = Mth.lerp(ease, jaw.xRot, 0.6f + Mth.sin(state.ageInTicks * 0.8f) * 0.05f);
			}
			default -> {
				if (state.walkAnimationSpeed < 0.05f) {
					// Grazing: the head dips to the ground now and then.
					float graze = Mth.clamp(Mth.sin(state.ageInTicks * 0.02f + state.grazeOffset) * 3f - 2f, 0f, 1f);
					head.xRot = Mth.lerp(graze, head.xRot, 1.1f);
					jaw.xRot = graze * Math.max(0f, Mth.sin(state.ageInTicks * 0.5f)) * 0.2f;
				}
			}
		}
	}
}
