package com.oldrepublic.client.vendor;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

/**
 * Vessba the Hutt's model (spec MILESTONE_5.md 3.3, 6.3, 18): a Hutt is not a humanoid, so she has
 * her own. The design is original: a broad, low slug body resting on the dais, a tail that tapers in
 * four segments and curls round to her right, an upright belly-and-chest mass, and a wide, flat head
 * that sits straight on it with heavy jowls below, two heavy-lidded eyes set in the brow, and two
 * short arms folded over the belly. She is two blocks tall and almost two wide (the {@code vessba}
 * entity is 1.9 by 2.0).
 *
 * <p>Poses: she breathes (the chest swells a little), turns her head to whoever she looks at, the
 * tail tip stirs, and her hands drum on the belly now and then; when she moves at all, the tail
 * ripples.
 *
 * <p>UV layout (128x128), mirrored in {@code tools/assets/gen_textures_hub.py} ({@code vessba()}):
 * base 0,0 (22x12x22); tail1 0,34 (16x9x12); tail2 56,34 (11x6x10); tail3 88,0 (7x4x8); tail tip
 * 98,34 (4x3x6); torso 0,56 (18x14x14); head 64,56 (16x9x13); jowls 0,84 (14x3x11); eyes 50,84
 * (3x2x2, the left mirrors the right); arms 64,78 (3x9x3, the left mirrors the right).
 */
public class VessbaModel extends EntityModel<LivingEntityRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(OldRepublicMod.id("vessba"), "main");
	/** How far the head follows the look, of the full angle. */
	private static final float LOOK = 0.55f;

	private final ModelPart torso;
	private final ModelPart head;
	private final ModelPart jowls;
	private final ModelPart tail2;
	private final ModelPart tail3;
	private final ModelPart tip;
	private final ModelPart rightArm;
	private final ModelPart leftArm;

	public VessbaModel(ModelPart root) {
		super(root);
		ModelPart base = root.getChild("base");
		ModelPart tail1 = base.getChild("tail1");
		this.tail2 = tail1.getChild("tail2");
		this.tail3 = tail2.getChild("tail3");
		this.tip = tail3.getChild("tip");
		this.torso = base.getChild("torso");
		this.head = torso.getChild("head");
		this.jowls = head.getChild("jowls");
		this.rightArm = torso.getChild("right_arm");
		this.leftArm = torso.getChild("left_arm");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

		// The low body on the ground (y 24 is the ground), and the tail curling round to her right.
		PartDefinition base = root.addOrReplaceChild("base", CubeListBuilder.create().texOffs(0, 0)
				.addBox(-11f, -12f, -9f, 22f, 12f, 22f), PartPose.offset(0f, 24f, 0f));
		PartDefinition tail1 = base.addOrReplaceChild("tail1", CubeListBuilder.create().texOffs(0, 34)
				.addBox(-8f, -9f, 0f, 16f, 9f, 12f), PartPose.offsetAndRotation(0f, 0f, 12f, 0f, 0.2f, 0f));
		PartDefinition tail2 = tail1.addOrReplaceChild("tail2", CubeListBuilder.create().texOffs(56, 34)
				.addBox(-5.5f, -6f, 0f, 11f, 6f, 10f), PartPose.offsetAndRotation(0f, 0f, 11f, 0f, 0.45f, 0f));
		PartDefinition tail3 = tail2.addOrReplaceChild("tail3", CubeListBuilder.create().texOffs(88, 0)
				.addBox(-3.5f, -4f, 0f, 7f, 4f, 8f), PartPose.offsetAndRotation(0f, 0f, 9f, 0f, 0.6f, 0f));
		tail3.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(98, 34)
				.addBox(-2f, -3f, 0f, 4f, 3f, 6f), PartPose.offsetAndRotation(0f, 0f, 7f, 0.15f, 0.7f, 0f));

		// The upright chest, the head straight on it with the jowls below, the eyes in the brow.
		PartDefinition torso = base.addOrReplaceChild("torso", CubeListBuilder.create().texOffs(0, 56)
				.addBox(-9f, -14f, -7f, 18f, 14f, 14f), PartPose.offsetAndRotation(0f, -10f, -3f, -0.06f, 0f, 0f));
		PartDefinition head = torso.addOrReplaceChild("head", CubeListBuilder.create().texOffs(64, 56)
				.addBox(-8f, -9f, -7f, 16f, 9f, 13f), PartPose.offset(0f, -13f, -1f));
		head.addOrReplaceChild("jowls", CubeListBuilder.create().texOffs(0, 84)
				.addBox(-7f, 0f, -7f, 14f, 3f, 11f), PartPose.ZERO);
		head.addOrReplaceChild("right_eye", CubeListBuilder.create().texOffs(50, 84)
				.addBox(-1.5f, -1f, -1f, 3f, 2f, 2f), PartPose.offset(-4f, -6f, -7f));
		head.addOrReplaceChild("left_eye", CubeListBuilder.create().texOffs(50, 84).mirror()
				.addBox(-1.5f, -1f, -1f, 3f, 2f, 2f), PartPose.offset(4f, -6f, -7f));

		// Short arms, folded forward over the belly.
		torso.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(64, 78)
				.addBox(-3f, -1f, -1.5f, 3f, 9f, 3f), PartPose.offsetAndRotation(-9f, -10f, -3f, -0.9f, 0f, -0.35f));
		torso.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(64, 78).mirror()
				.addBox(0f, -1f, -1.5f, 3f, 9f, 3f), PartPose.offsetAndRotation(9f, -10f, -3f, -0.9f, 0f, 0.35f));
		return LayerDefinition.create(mesh, 128, 128);
	}

	@Override
	public void setupAnim(LivingEntityRenderState state) {
		super.setupAnim(state);
		float age = state.ageInTicks;

		// Breathing: the chest swells and the jowls settle.
		float breath = Mth.sin(age * 0.06f);
		torso.xScale = 1f + breath * 0.015f;
		torso.zScale = 1f + breath * 0.02f;
		jowls.y += breath * 0.25f;

		// The head follows the look, slowly and not far: she does not crane.
		head.yRot = Mth.clamp(state.yRot, -50f, 50f) * Mth.DEG_TO_RAD * LOOK;
		head.xRot = Mth.clamp(state.xRot, -20f, 25f) * Mth.DEG_TO_RAD * LOOK;

		// The tail stirs at rest and ripples when she moves.
		float ripple = state.walkAnimationSpeed * 0.5f;
		float wave = state.walkAnimationPos * 0.5f;
		tail2.yRot += Mth.sin(age * 0.04f) * 0.06f + Mth.sin(wave) * ripple;
		tail3.yRot += Mth.sin(age * 0.04f - 0.8f) * 0.1f + Mth.sin(wave - 1f) * ripple;
		tip.yRot += Mth.sin(age * 0.04f - 1.6f) * 0.16f + Mth.sin(wave - 2f) * ripple;
		tip.xRot += Mth.sin(age * 0.07f) * 0.05f;

		// Fingers drumming on the belly: a short lift of one hand, then the other, every few seconds.
		float drum = Mth.clamp(Mth.sin(age * 0.035f) * 4f - 3f, 0f, 1f);
		rightArm.xRot -= drum * Mth.abs(Mth.sin(age * 0.9f)) * 0.18f;
		leftArm.xRot -= drum * Mth.abs(Mth.sin(age * 0.9f + 1.4f)) * 0.18f;
	}
}
