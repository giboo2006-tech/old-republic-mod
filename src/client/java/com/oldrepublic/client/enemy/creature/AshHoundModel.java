package com.oldrepublic.client.enemy.creature;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.enemy.creature.AshHound;

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
 * The ash hound's model (spec MILESTONE_4.md 10.4): a lean, hairless hound, deep in the chest and
 * narrow at the waist, with a long skull, small swept ears and a whip tail.
 *
 * <p>Poses: running; the lunge tell (a low crouch, the haunches drawn under, head level and ears
 * flat); the leap (legs stretched fore and aft); the howl (sitting back, head thrown up); the
 * stagger (head shaking, the body twisted aside).
 *
 * <p>UV layout (64x64), mirrored in {@code tools/assets/gen_textures_foes.py}: body 0,0; head 40,0;
 * snout 40,10; ear 52,10; legs 0,20; tail 8,20.
 */
public class AshHoundModel extends EntityModel<AshHoundRenderer.State> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(OldRepublicMod.id("ash_hound"), "main");
	private static final float TELL_EASE_TICKS = 4f;

	private final ModelPart body;
	private final ModelPart head;
	private final ModelPart rightEar;
	private final ModelPart leftEar;
	private final ModelPart tail;
	private final ModelPart rightFrontLeg;
	private final ModelPart leftFrontLeg;
	private final ModelPart rightHindLeg;
	private final ModelPart leftHindLeg;

	public AshHoundModel(ModelPart root) {
		super(root);
		this.body = root.getChild("body");
		this.head = root.getChild("head");
		this.rightEar = head.getChild("ear_right");
		this.leftEar = head.getChild("ear_left");
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
				.addBox(-3f, -6f, -7f, 6f, 6f, 14f), PartPose.offset(0f, 17f, 0f));
		body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(8, 20)
				.addBox(-0.5f, -0.5f, 0f, 1f, 1f, 7f), PartPose.offsetAndRotation(0f, -5f, 7f, -0.5f, 0f, 0f));

		PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(40, 0)
				.addBox(-2.5f, -3f, -5f, 5f, 5f, 5f), PartPose.offset(0f, 12f, -7f));
		head.addOrReplaceChild("snout", CubeListBuilder.create().texOffs(40, 10)
				.addBox(-1.5f, -0.5f, -8f, 3f, 2f, 3f), PartPose.ZERO);
		head.addOrReplaceChild("ear_right", CubeListBuilder.create().texOffs(52, 10)
				.addBox(-0.5f, -2f, -0.5f, 1f, 2f, 1f), PartPose.offsetAndRotation(-1.8f, -3f, -1f, -0.6f, 0f, -0.3f));
		head.addOrReplaceChild("ear_left", CubeListBuilder.create().texOffs(52, 10).mirror()
				.addBox(-0.5f, -2f, -0.5f, 1f, 2f, 1f), PartPose.offsetAndRotation(1.8f, -3f, -1f, -0.6f, 0f, 0.3f));

		CubeListBuilder leg = CubeListBuilder.create().texOffs(0, 20).addBox(-1f, 0f, -1f, 2f, 7f, 2f);
		CubeListBuilder mirrored = CubeListBuilder.create().texOffs(0, 20).mirror().addBox(-1f, 0f, -1f, 2f, 7f, 2f);
		root.addOrReplaceChild("right_front_leg", leg, PartPose.offset(-1.8f, 17f, -5f));
		root.addOrReplaceChild("left_front_leg", mirrored, PartPose.offset(1.8f, 17f, -5f));
		root.addOrReplaceChild("right_hind_leg", leg, PartPose.offset(-1.8f, 17f, 5.5f));
		root.addOrReplaceChild("left_hind_leg", mirrored, PartPose.offset(1.8f, 17f, 5.5f));
		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	public void setupAnim(AshHoundRenderer.State state) {
		super.setupAnim(state);
		float swing = state.walkAnimationSpeed * 1.3f;
		float pos = state.walkAnimationPos * 0.8f;
		rightFrontLeg.xRot = Mth.cos(pos) * swing;
		leftFrontLeg.xRot = Mth.cos(pos + Mth.PI) * swing;
		rightHindLeg.xRot = Mth.cos(pos + Mth.PI) * swing;
		leftHindLeg.xRot = Mth.cos(pos) * swing;
		head.xRot = state.xRot * Mth.DEG_TO_RAD;
		head.yRot = state.yRot * Mth.DEG_TO_RAD;
		tail.yRot = Mth.sin(state.ageInTicks * 0.3f) * 0.15f;
		float ease = Mth.clamp(state.stateTicks / TELL_EASE_TICKS, 0f, 1f);

		switch (state.state) {
			case AshHound.STATE_CROUCH -> {
				// Low, haunches drawn under, head level, ears flat: the lunge is coming.
				body.y = Mth.lerp(ease, 17f, 19.5f);
				body.xRot = Mth.lerp(ease, 0f, -0.12f);
				head.y = Mth.lerp(ease, 12f, 15f);
				head.xRot = Mth.lerp(ease, head.xRot, 0.1f);
				rightHindLeg.xRot = Mth.lerp(ease, rightHindLeg.xRot, -0.9f);
				leftHindLeg.xRot = Mth.lerp(ease, leftHindLeg.xRot, -0.9f);
				rightFrontLeg.xRot = Mth.lerp(ease, rightFrontLeg.xRot, -0.5f);
				leftFrontLeg.xRot = Mth.lerp(ease, leftFrontLeg.xRot, -0.5f);
				rightEar.xRot = -1.3f;
				leftEar.xRot = -1.3f;
			}
			case AshHound.STATE_LEAP -> {
				// Stretched out in the air.
				rightFrontLeg.xRot = -1.2f;
				leftFrontLeg.xRot = -1.1f;
				rightHindLeg.xRot = 1.1f;
				leftHindLeg.xRot = 1.2f;
				head.xRot = -0.15f;
				rightEar.xRot = -1.3f;
				leftEar.xRot = -1.3f;
			}
			case AshHound.STATE_HOWL -> {
				// Sat back on the haunches, head thrown up.
				body.xRot = Mth.lerp(ease, 0f, -0.6f);
				body.y = Mth.lerp(ease, 17f, 18.5f);
				head.y = Mth.lerp(ease, 12f, 10f);
				head.xRot = Mth.lerp(ease, head.xRot, -1.1f);
				rightHindLeg.xRot = Mth.lerp(ease, rightHindLeg.xRot, -1.3f);
				leftHindLeg.xRot = Mth.lerp(ease, leftHindLeg.xRot, -1.3f);
			}
			case AshHound.STATE_STAGGERED -> {
				// Turned aside, shaking its head.
				head.zRot = Mth.sin(state.ageInTicks * 1.4f) * 0.45f;
				body.yRot = 0.3f;
			}
			default -> {
			}
		}
	}
}
