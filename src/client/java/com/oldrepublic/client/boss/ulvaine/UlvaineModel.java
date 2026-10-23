package com.oldrepublic.client.boss.ulvaine;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.boss.ulvaine.DarthUlvaine;

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
 * Darth Ulvaine's model (spec MILESTONE_5.md 12.2), also her hologram's and her echo's: a tall Sith in
 * black and violet. A high, stiff collar rises behind her head; a veil of fine chain hangs across the
 * lower face; a fitted robe falls to the ankles, with long sleeves and a short cape behind. Her saber
 * (a violet-red blade on a curved hilt) is the item in her hand, so the saber feature draws the blade,
 * the guard and the strikes.
 *
 * <p>UV layout (64x64; the upper half is the classic humanoid layout), mirrored in
 * {@code tools/assets/gen_textures_boss.py}: robe 0,32; sleeves 24,32; veil 40,32; collar 40,38;
 * skirt 0,48; cape 24,48.
 *
 * <p>Poses, from the synced action: Force lightning (the off hand outstretched, fingers spread); the
 * Hymn step's tell (drawn in on herself, arms crossed); the Push (the palm thrust out); the storm (both
 * arms raised); the song (head back, arms open and low); bound (wrists together before her); and the
 * kneel of her yield (the renderer lowers the body by {@link #KNEEL_DROP}).
 */
public class UlvaineModel extends HumanoidModel<UlvaineRenderer.State> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(OldRepublicMod.id("darth_ulvaine"), "main");
	/** How far the body is lowered in a full kneel, in blocks (the renderer applies it). */
	public static final float KNEEL_DROP = 0.55f;
	private static final float EASE_TICKS = 6f;

	private final ModelPart cape;

	public UlvaineModel(ModelPart root) {
		super(root);
		this.cape = body.getChild("cape");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0f);
		PartDefinition root = mesh.getRoot();
		PartDefinition head = root.getChild("head");
		PartDefinition body = root.getChild("body");
		PartDefinition rightArm = root.getChild("right_arm");
		PartDefinition leftArm = root.getChild("left_arm");

		head.addOrReplaceChild("veil", CubeListBuilder.create().texOffs(40, 32)
				.addBox(-4f, -4.5f, -4.7f, 8f, 5f, 1f), PartPose.ZERO);

		body.addOrReplaceChild("collar", CubeListBuilder.create().texOffs(40, 38)
				.addBox(-5f, -6f, 0f, 10f, 6f, 1f), PartPose.offsetAndRotation(0f, 0.5f, 2.2f, -0.18f, 0f, 0f));
		body.addOrReplaceChild("robe", CubeListBuilder.create().texOffs(0, 32)
				.addBox(-4f, 0f, -2f, 8f, 12f, 4f, new CubeDeformation(0.3f)), PartPose.ZERO);
		body.addOrReplaceChild("skirt", CubeListBuilder.create().texOffs(0, 48)
				.addBox(-4f, 0f, -2f, 8f, 9f, 4f, new CubeDeformation(0.45f)), PartPose.offset(0f, 12f, 0f));
		body.addOrReplaceChild("cape", CubeListBuilder.create().texOffs(24, 48)
				.addBox(-4f, 0f, 0f, 8f, 15f, 1f), PartPose.offsetAndRotation(0f, 0.2f, 2.4f, 0.06f, 0f, 0f));

		rightArm.addOrReplaceChild("sleeve", CubeListBuilder.create().texOffs(24, 32)
				.addBox(-3f, -2f, -2f, 4f, 12f, 4f, new CubeDeformation(0.3f)), PartPose.ZERO);
		leftArm.addOrReplaceChild("sleeve", CubeListBuilder.create().texOffs(24, 32).mirror()
				.addBox(-1f, -2f, -2f, 4f, 12f, 4f, new CubeDeformation(0.3f)), PartPose.ZERO);

		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	public void setupAnim(UlvaineRenderer.State state) {
		super.setupAnim(state);
		// The cape stirs with her stride and the hall's air.
		cape.xRot = 0.06f + state.walkAnimationSpeed * 0.35f + Mth.sin(state.ageInTicks * 0.06f) * 0.03f;

		if (state.kneelAmount > 0f) {
			kneel(state.kneelAmount);
			return;
		}

		float ease = Mth.clamp(state.actionTicks / EASE_TICKS, 0f, 1f);

		switch (state.action) {
			case LIGHTNING -> {
				// The off hand outstretched along the look, fingers spread; the blade held back.
				leftArm.xRot = head.xRot - Mth.HALF_PI;
				leftArm.yRot = head.yRot + 0.08f;
				leftArm.zRot = 0f;
				body.yRot = -0.15f;
			}
			case STEP -> {
				// Drawn in on herself before she vanishes: arms crossed, shoulders hunched.
				rightArm.xRot = Mth.lerp(ease, rightArm.xRot, -1.1f);
				rightArm.yRot = Mth.lerp(ease, rightArm.yRot, -0.7f);
				leftArm.xRot = Mth.lerp(ease, leftArm.xRot, -1.1f);
				leftArm.yRot = Mth.lerp(ease, leftArm.yRot, 0.7f);
				head.xRot = Mth.lerp(ease, head.xRot, 0.35f);
				body.xRot = Mth.lerp(ease, body.xRot, 0.15f);
			}
			case PUSH -> {
				// The palm thrust out, the body turned into it.
				leftArm.xRot = Mth.lerp(ease, leftArm.xRot, head.xRot - Mth.HALF_PI);
				leftArm.yRot = Mth.lerp(ease, leftArm.yRot, head.yRot);
				leftArm.zRot = 0f;
				body.yRot = Mth.lerp(ease, body.yRot, -0.25f);
			}
			case STORM -> {
				// Both arms raised to the storm, head back.
				float tremble = Mth.sin(state.ageInTicks * 1.2f) * 0.03f;
				rightArm.xRot = Mth.lerp(ease, rightArm.xRot, -2.8f + tremble);
				rightArm.zRot = Mth.lerp(ease, rightArm.zRot, 0.25f);
				leftArm.xRot = Mth.lerp(ease, leftArm.xRot, -2.8f - tremble);
				leftArm.zRot = Mth.lerp(ease, leftArm.zRot, -0.25f);
				head.xRot = Mth.lerp(ease, head.xRot, -0.45f);
			}
			case SING -> {
				// Singing: head back, arms open and low, a slow sway.
				float sway = Mth.sin(state.ageInTicks * 0.08f) * 0.06f;
				rightArm.xRot = Mth.lerp(ease, rightArm.xRot, -0.35f);
				rightArm.zRot = Mth.lerp(ease, rightArm.zRot, 0.65f + sway);
				leftArm.xRot = Mth.lerp(ease, leftArm.xRot, -0.35f);
				leftArm.zRot = Mth.lerp(ease, leftArm.zRot, -0.65f + sway);
				head.xRot = Mth.lerp(ease, head.xRot, -0.5f);
			}
			case BOUND -> {
				// Wrists bound together before her.
				rightArm.xRot = -0.75f;
				rightArm.yRot = -0.45f;
				rightArm.zRot = 0f;
				leftArm.xRot = -0.75f;
				leftArm.yRot = 0.45f;
				leftArm.zRot = 0f;
				head.xRot = 0.25f;
			}
			default -> {
			}
		}
	}

	/** Down on one knee, head bowed, the blade laid down; {@code amount} 0 to 1 eases it in (the renderer lowers the body). */
	private void kneel(float amount) {
		rightLeg.xRot = Mth.lerp(amount, rightLeg.xRot, -1.45f);
		rightLeg.yRot = Mth.lerp(amount, rightLeg.yRot, 0.12f);
		leftLeg.xRot = Mth.lerp(amount, leftLeg.xRot, 1.3f);
		leftLeg.yRot = Mth.lerp(amount, leftLeg.yRot, -0.08f);
		head.xRot = Mth.lerp(amount, head.xRot, 0.45f);
		body.xRot = Mth.lerp(amount, body.xRot, 0.12f);
		rightArm.xRot = Mth.lerp(amount, rightArm.xRot, 0.15f);
		rightArm.yRot = Mth.lerp(amount, rightArm.yRot, 0f);
		rightArm.zRot = Mth.lerp(amount, rightArm.zRot, 0.1f);
		leftArm.xRot = Mth.lerp(amount, leftArm.xRot, -0.1f);
		leftArm.yRot = Mth.lerp(amount, leftArm.yRot, 0f);
		leftArm.zRot = Mth.lerp(amount, leftArm.zRot, -0.1f);
	}
}
