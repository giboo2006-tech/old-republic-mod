package com.oldrepublic.client.enemy.creature;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.enemy.creature.Hollowhorn;

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
 * The hollowhorn's model (spec MILESTONE_4.md 10.3): a shaggy, elk-like grazer the size of a horse,
 * with a heavy chest mane, a short tail and two horns that sweep back and curl up and forward.
 * Calves use the same model at a smaller scale (the server's scale attribute) without horns.
 *
 * <p>Poses: walking and grazing (the head dips now and then while idle); the charge tell (head
 * lowered, horns forward, one forehoof scraping); the charge (head low, a full gallop); stunned
 * (head hanging and swaying).
 *
 * <p>UV layout (128x64), mirrored in {@code tools/assets/gen_textures_foes.py}: body 0,0; neck 68,0;
 * head 88,0; muzzle 116,0; horn base 68,14; horn sweep 76,14; horn tip 88,14; legs 0,32; tail 16,32;
 * mane 24,32.
 */
public class HollowhornModel extends EntityModel<HollowhornRenderer.State> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(OldRepublicMod.id("hollowhorn"), "main");
	private static final float TELL_EASE_TICKS = 5f;

	private final ModelPart neck;
	private final ModelPart head;
	private final ModelPart rightHorn;
	private final ModelPart leftHorn;
	private final ModelPart rightFrontLeg;
	private final ModelPart leftFrontLeg;
	private final ModelPart rightHindLeg;
	private final ModelPart leftHindLeg;
	private final ModelPart tail;

	public HollowhornModel(ModelPart root) {
		super(root);
		ModelPart body = root.getChild("body");
		this.neck = body.getChild("neck");
		this.head = neck.getChild("head");
		this.rightHorn = head.getChild("horn_right");
		this.leftHorn = head.getChild("horn_left");
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
				.addBox(-6f, -10f, -11f, 12f, 10f, 22f), PartPose.offset(0f, 13f, 0f));
		body.addOrReplaceChild("mane", CubeListBuilder.create().texOffs(24, 32)
				.addBox(-4f, 0f, -2f, 8f, 6f, 4f), PartPose.offsetAndRotation(0f, -2f, -10f, 0.3f, 0f, 0f));
		body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(16, 32)
				.addBox(-1f, 0f, 0f, 2f, 6f, 2f), PartPose.offsetAndRotation(0f, -8f, 10.5f, 0.5f, 0f, 0f));

		PartDefinition neck = body.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(68, 0)
				.addBox(-2.5f, -8f, -2.5f, 5f, 9f, 5f), PartPose.offsetAndRotation(0f, -7f, -10f, 0.55f, 0f, 0f));
		PartDefinition head = neck.addOrReplaceChild("head", CubeListBuilder.create().texOffs(88, 0)
				.addBox(-3f, -3f, -7f, 6f, 6f, 8f), PartPose.offsetAndRotation(0f, -8f, 0f, -0.55f, 0f, 0f));
		head.addOrReplaceChild("muzzle", CubeListBuilder.create().texOffs(116, 0)
				.addBox(-2f, -0.5f, -9f, 4f, 3f, 2f), PartPose.ZERO);
		horn(head, "horn_right", -1f);
		horn(head, "horn_left", 1f);

		CubeListBuilder leg = CubeListBuilder.create().texOffs(0, 32).addBox(-2f, 0f, -2f, 4f, 12f, 4f);
		CubeListBuilder mirrored = CubeListBuilder.create().texOffs(0, 32).mirror().addBox(-2f, 0f, -2f, 4f, 12f, 4f);
		root.addOrReplaceChild("right_front_leg", leg, PartPose.offset(-4f, 12f, -8f));
		root.addOrReplaceChild("left_front_leg", mirrored, PartPose.offset(4f, 12f, -8f));
		root.addOrReplaceChild("right_hind_leg", leg, PartPose.offset(-4f, 12f, 8f));
		root.addOrReplaceChild("left_hind_leg", mirrored, PartPose.offset(4f, 12f, 8f));
		return LayerDefinition.create(mesh, 128, 64);
	}

	/** One horn: a base at the temple, a sweep back, and a tip curling up and forward. */
	private static void horn(PartDefinition head, String name, float side) {
		CubeListBuilder base = CubeListBuilder.create().texOffs(68, 14);
		CubeListBuilder sweep = CubeListBuilder.create().texOffs(76, 14);
		CubeListBuilder tip = CubeListBuilder.create().texOffs(88, 14);

		if (side > 0) {
			base.mirror();
			sweep.mirror();
			tip.mirror();
		}

		PartDefinition horn = head.addOrReplaceChild(name, base.addBox(-1f, -1f, -1f, 2f, 2f, 2f),
				PartPose.offsetAndRotation(side * 3.2f, -3f, -2f, 0f, 0f, side * 0.5f));
		PartDefinition middle = horn.addOrReplaceChild("sweep", sweep.addBox(-1f, -1f, 0f, 2f, 2f, 4f),
				PartPose.offsetAndRotation(side * 0.5f, 0f, 0.5f, 0.45f, side * 0.4f, 0f));
		middle.addOrReplaceChild("tip", tip.addBox(-0.5f, -0.5f, -3f, 1f, 1f, 3f),
				PartPose.offsetAndRotation(0f, 0f, 3.5f, -1.9f, 0f, 0f));
	}

	@Override
	public void setupAnim(HollowhornRenderer.State state) {
		super.setupAnim(state);
		rightHorn.visible = !state.calf;
		leftHorn.visible = !state.calf;

		boolean charging = state.state == Hollowhorn.STATE_CHARGING;
		float stride = charging ? 1.1f : 1f;
		float swing = state.walkAnimationSpeed * 1.2f;
		float pos = state.walkAnimationPos * 0.6662f * stride;
		rightFrontLeg.xRot = Mth.cos(pos) * swing;
		leftFrontLeg.xRot = Mth.cos(pos + Mth.PI) * swing;
		rightHindLeg.xRot = Mth.cos(pos + Mth.PI) * swing;
		leftHindLeg.xRot = Mth.cos(pos) * swing;
		tail.zRot = Mth.sin(state.ageInTicks * 0.08f) * 0.2f;

		head.xRot += state.xRot * Mth.DEG_TO_RAD * 0.5f;
		neck.yRot = state.yRot * Mth.DEG_TO_RAD * 0.6f;

		switch (state.state) {
			case Hollowhorn.STATE_TELL -> {
				// Head down, horns forward, a forehoof scraping the ground.
				float ease = Mth.clamp(state.stateTicks / TELL_EASE_TICKS, 0f, 1f);
				neck.xRot = Mth.lerp(ease, neck.xRot, 1.35f);
				head.xRot = Mth.lerp(ease, head.xRot, -0.4f);
				rightFrontLeg.xRot = Mth.lerp(ease, rightFrontLeg.xRot, -0.35f + Mth.sin(state.ageInTicks * 0.9f) * 0.3f);
			}
			case Hollowhorn.STATE_CHARGING -> {
				neck.xRot = 1.25f;
				head.xRot = -0.35f;
			}
			case Hollowhorn.STATE_STUNNED -> {
				// Dazed: the head hangs and sways.
				neck.xRot = 1.15f;
				neck.yRot = Mth.sin(state.ageInTicks * 0.35f) * 0.35f;
				head.zRot = Mth.sin(state.ageInTicks * 0.25f) * 0.25f;
			}
			default -> {
				if (state.walkAnimationSpeed < 0.05f) {
					// Grazing: the head dips to the grass now and then.
					float graze = Mth.clamp(Mth.sin(state.ageInTicks * 0.02f + state.grazeOffset) * 3f - 2f, 0f, 1f);
					neck.xRot = Mth.lerp(graze, neck.xRot, 1.9f);
				}
			}
		}
	}
}
