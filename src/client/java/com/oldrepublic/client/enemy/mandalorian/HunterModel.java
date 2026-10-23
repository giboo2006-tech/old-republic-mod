package com.oldrepublic.client.enemy.mandalorian;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.enemy.mandalorian.MandalorianHunter;

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
 * The Mandalorian hunter's model (spec MILESTONE_4.md 10.1), also Torvin Kaast's: the vanilla
 * humanoid in hunter's plate. The hat layer is the helmet shell (its T-visor is painted), with a
 * range-finder stalk on the left; a cuirass, shoulder plates, forearm gauntlets (the wrist flamer,
 * rocket and net launcher), a kama on the belt, and a jet pack with two nozzles on the back. The
 * rifle hangs from the right hand; Kaast's vibro-axe takes its place in phase 3, and in his yield he
 * holds his helmet at his side (the face under it shows).
 *
 * <p>UV layout (128x64; the upper-left 64x32 is the classic humanoid layout), mirrored in
 * {@code tools/assets/gen_textures_foes.py}: cuirass 0,32; jet pack 24,32; kama 44,32; nozzle 44,39;
 * range-finder 52,39; gauntlet 0,48; pauldron 16,48; scope 36,48; rifle 64,0; axe haft 72,0; axe
 * head 76,0. The held helmet reuses the helmet's own UV (32,0).
 *
 * <p>Poses, from the synced state: the rifle carried low, shouldered while aiming; each gadget's
 * tell (the jet crouch, the flamer wrist raised, the rocket arm levelled, the net arm drawn back);
 * legs trailing in a hop; the kneel of a yield or of Kaast setting a mine; the axe drawn back for
 * the combo's third blow; the reel of a stagger.
 */
public class HunterModel extends HumanoidModel<HunterRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(OldRepublicMod.id("mandalorian_hunter"), "main");
	/** How far the body is lowered in a full kneel, in blocks (the renderer applies it). */
	public static final float KNEEL_DROP = 0.55f;
	/** Ticks a tell's pose takes to ease in. */
	private static final float TELL_EASE_TICKS = 5f;

	private final ModelPart rangefinder;
	private final ModelPart rifle;
	private final ModelPart scope;
	private final ModelPart axe;
	private final ModelPart heldHelmet;

	public HunterModel(ModelPart root) {
		super(root);
		this.rangefinder = hat.getChild("rangefinder");
		this.rifle = rightArm.getChild("rifle");
		this.scope = rightArm.getChild("scope");
		this.axe = rightArm.getChild("axe");
		this.heldHelmet = leftArm.getChild("held_helmet");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0f);
		PartDefinition root = mesh.getRoot();
		PartDefinition hat = root.getChild("head").getChild("hat");
		PartDefinition body = root.getChild("body");
		PartDefinition rightArm = root.getChild("right_arm");
		PartDefinition leftArm = root.getChild("left_arm");

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

		// The rifle lies along the forearm and past the hand, so it points where the arm points.
		rightArm.addOrReplaceChild("rifle", CubeListBuilder.create().texOffs(64, 0)
				.addBox(-1.6f, 6f, -3.4f, 2f, 14f, 2f), PartPose.ZERO);
		rightArm.addOrReplaceChild("scope", CubeListBuilder.create().texOffs(36, 48)
				.addBox(-1.1f, 9f, -4.4f, 1f, 4f, 1f), PartPose.ZERO);
		// Kaast's vibro-axe: a haft through the fist, the head beyond it.
		PartDefinition axe = rightArm.addOrReplaceChild("axe", CubeListBuilder.create().texOffs(72, 0)
				.addBox(-1.1f, 3f, -0.5f, 1f, 16f, 1f), PartPose.ZERO);
		axe.addOrReplaceChild("blade", CubeListBuilder.create().texOffs(76, 0)
				.addBox(-1.1f, 14f, -4.5f, 1f, 5f, 4f), PartPose.ZERO);
		// The helmet in his hand while he kneels in his yield.
		leftArm.addOrReplaceChild("held_helmet", CubeListBuilder.create().texOffs(32, 0)
				.addBox(-3f, 9f, -4f, 8f, 8f, 8f, new CubeDeformation(-0.6f)), PartPose.ZERO);

		return LayerDefinition.create(mesh, 128, 64);
	}

	@Override
	public void setupAnim(HunterRenderState state) {
		super.setupAnim(state);
		rifle.visible = !state.axe;
		scope.visible = !state.axe;
		axe.visible = state.axe;
		hat.visible = !state.unmasked;
		rangefinder.visible = !state.unmasked;
		heldHelmet.visible = state.unmasked;

		float ease = Mth.clamp(state.tellTicks / TELL_EASE_TICKS, 0f, 1f);

		if (state.yielded || state.unmasked) {
			kneel(1f);
			rightArm.xRot = -0.25f;
			leftArm.xRot = state.unmasked ? -0.2f : 0.15f;
			return;
		}

		if (state.kneeling) {
			// Setting a mine: knelt, both hands down at the ground in front.
			kneel(1f);
			rightArm.xRot = -1.2f;
			leftArm.xRot = -1.1f;
			head.xRot = 0.7f;
			return;
		}

		if (state.staggered) {
			// Reeling: head thrown back, arms flung out.
			head.xRot = -0.45f;
			body.xRot = -0.15f;
			rightArm.zRot = 0.8f;
			leftArm.zRot = -0.8f;
			rightArm.xRot = -0.3f;
			leftArm.xRot = -0.3f;
			return;
		}

		if (state.jetting) {
			// In the air: legs trailing, arms out for balance.
			rightLeg.xRot = 0.35f;
			leftLeg.xRot = 0.05f;
			rightArm.zRot = 0.35f;
			leftArm.zRot = -0.35f;
		}

		if (state.axe) {
			axePose(state);
		} else if (state.aiming) {
			// Shouldered, sighting along the look; the left hand under the barrel.
			rightArm.xRot = head.xRot - Mth.HALF_PI;
			rightArm.yRot = head.yRot - 0.1f;
			leftArm.xRot = head.xRot - Mth.HALF_PI + 0.1f;
			leftArm.yRot = head.yRot + 0.55f;
		} else if (state.attackTime <= 0f) {
			// Carried low across the body.
			rightArm.xRot = rightArm.xRot * 0.3f - 0.45f;
			rightArm.yRot = -0.25f;
		}

		gadgetPose(state, ease);
	}

	/** Each gadget's tell, eased in over {@link #TELL_EASE_TICKS}; the flame holds the flamer pose. */
	private void gadgetPose(HunterRenderState state, float ease) {
		int gadget = state.flaming ? MandalorianHunter.GADGET_FLAMER : state.gadget;

		switch (gadget) {
			case MandalorianHunter.GADGET_JET -> {
				// Crouched to spring, hands to the pack's grips.
				body.xRot = Mth.lerp(ease, body.xRot, 0.3f);
				rightLeg.xRot = Mth.lerp(ease, rightLeg.xRot, -0.35f);
				leftLeg.xRot = Mth.lerp(ease, leftLeg.xRot, -0.35f);
				rightArm.xRot = Mth.lerp(ease, rightArm.xRot, 0.4f);
				leftArm.xRot = Mth.lerp(ease, leftArm.xRot, 0.4f);
			}
			case MandalorianHunter.GADGET_FLAMER -> {
				// The left wrist raised and pointed, palm down.
				leftArm.xRot = Mth.lerp(ease, leftArm.xRot, head.xRot - 1.35f);
				leftArm.yRot = Mth.lerp(ease, leftArm.yRot, head.yRot + 0.15f);
				leftArm.zRot = Mth.lerp(ease, leftArm.zRot, 0f);
			}
			case MandalorianHunter.GADGET_ROCKET -> {
				// The left arm levelled at the target, the right hand steadying the wrist.
				leftArm.xRot = Mth.lerp(ease, leftArm.xRot, head.xRot - Mth.HALF_PI);
				leftArm.yRot = Mth.lerp(ease, leftArm.yRot, head.yRot);
				rightArm.xRot = Mth.lerp(ease, rightArm.xRot, head.xRot - 1.2f);
				rightArm.yRot = Mth.lerp(ease, rightArm.yRot, head.yRot - 0.45f);
			}
			case MandalorianHunter.GADGET_NET -> {
				// The left arm drawn back and out, about to sling the net.
				leftArm.xRot = Mth.lerp(ease, leftArm.xRot, 0.9f);
				leftArm.zRot = Mth.lerp(ease, leftArm.zRot, -0.6f);
				body.yRot = Mth.lerp(ease, body.yRot, 0.35f);
			}
			default -> {
			}
		}
	}

	/** Kaast's axe: held ready across the body, raised high and back in the wind-up. */
	private void axePose(HunterRenderState state) {
		if (state.windup) {
			rightArm.xRot = -2.9f;
			rightArm.yRot = -0.2f;
			rightArm.zRot = 0.2f;
			leftArm.xRot = -0.6f;
			body.yRot = -0.3f;
			return;
		}

		if (state.attackTime <= 0f) {
			rightArm.xRot = -0.8f;
			rightArm.yRot = -0.35f;
			leftArm.xRot = -0.6f;
			leftArm.yRot = 0.4f;
		}
	}

	/** Down on one knee, head bowed (the renderer lowers the body by {@link #KNEEL_DROP}). */
	private void kneel(float amount) {
		rightLeg.xRot = Mth.lerp(amount, rightLeg.xRot, -1.45f);
		rightLeg.yRot = Mth.lerp(amount, rightLeg.yRot, 0.12f);
		leftLeg.xRot = Mth.lerp(amount, leftLeg.xRot, 1.3f);
		leftLeg.yRot = Mth.lerp(amount, leftLeg.yRot, -0.08f);
		head.xRot = Mth.lerp(amount, head.xRot, 0.45f);
		body.xRot = Mth.lerp(amount, body.xRot, 0.12f);
		rightArm.yRot = 0f;
		leftArm.yRot = 0f;
		rightArm.zRot = 0.1f;
		leftArm.zRot = -0.1f;
	}
}
