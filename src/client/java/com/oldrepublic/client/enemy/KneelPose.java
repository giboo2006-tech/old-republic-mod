package com.oldrepublic.client.enemy;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;

/**
 * The yield and spar-loss kneel shared by the Sith Acolyte and the Poacher Captain: down on one
 * knee, head bowed, arms hanging with the weapon lowered. The model bends the limbs; the renderer
 * lowers the whole body by {@link #DROP} blocks.
 */
final class KneelPose {
	/** How far the body is lowered, in blocks, when fully knelt. */
	static final float DROP = 0.55f;

	/** Bends the limbs into the kneel, blended by {@code amount} (0 to 1). */
	static void apply(HumanoidModel<?> model, float amount) {
		if (amount <= 0f) {
			return;
		}

		// Right thigh forward, left leg folded back onto the knee.
		model.rightLeg.xRot = Mth.lerp(amount, model.rightLeg.xRot, -1.45f);
		model.rightLeg.yRot = Mth.lerp(amount, model.rightLeg.yRot, 0.12f);
		model.leftLeg.xRot = Mth.lerp(amount, model.leftLeg.xRot, 1.3f);
		model.leftLeg.yRot = Mth.lerp(amount, model.leftLeg.yRot, -0.08f);
		model.head.xRot = Mth.lerp(amount, model.head.xRot, 0.45f);
		model.body.xRot = Mth.lerp(amount, model.body.xRot, 0.12f);
		model.rightArm.xRot = Mth.lerp(amount, model.rightArm.xRot, 0.15f);
		model.rightArm.yRot = Mth.lerp(amount, model.rightArm.yRot, 0f);
		model.rightArm.zRot = Mth.lerp(amount, model.rightArm.zRot, 0.1f);
		model.leftArm.xRot = Mth.lerp(amount, model.leftArm.xRot, 0.15f);
		model.leftArm.yRot = Mth.lerp(amount, model.leftArm.yRot, 0f);
		model.leftArm.zRot = Mth.lerp(amount, model.leftArm.zRot, -0.1f);
	}

	/**
	 * Lowers the model; call from the renderer's {@code scale}, where the pose stack is in the
	 * flipped model space (positive y is down).
	 */
	static void lower(PoseStack poseStack, float amount) {
		if (amount > 0f) {
			poseStack.translate(0f, DROP * amount, 0f);
		}
	}

	private KneelPose() {
	}
}
