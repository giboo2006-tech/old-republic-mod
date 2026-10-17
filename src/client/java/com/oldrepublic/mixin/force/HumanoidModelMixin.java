package com.oldrepublic.mixin.force;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.oldrepublic.client.force.PowerAnimations;
import com.oldrepublic.client.force.PowerPose;
import com.oldrepublic.client.force.PushPose;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.util.Mth;

/**
 * Applies a {@link PushPose} or {@link PowerPose} after the vanilla humanoid animation: the push arm
 * draws back while charging and thrusts forward, palm open, along the look direction on release;
 * the other powers' arm poses are described on {@code PowerAnimations.Kind}. A creature held by
 * Telekinetic Lift ({@link PowerPose#FLAIL}) swings its arms out and kicks for balance; there is no
 * throat or chest pose (reserved for Choke and the Archive Grip). Player sleeves and trousers are
 * children of the arms and legs, so they follow.
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
	private void oldrepublic$applyPushPose(HumanoidRenderState state, CallbackInfo ci) {
		HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
		Float flail = state.getData(PowerPose.FLAIL);

		if (flail != null) {
			applyFlail(model, flail, state.ageInTicks);
		}

		PowerPose power = state.getData(PowerPose.KEY);

		if (power != null) {
			applyPowerPose(model, power);
		}

		PushPose pose = state.getData(PushPose.KEY);

		if (pose == null) {
			return;
		}

		ModelPart arm = pose.leftArm() ? model.leftArm : model.rightArm;
		// Outward roll is positive for the right arm and negative for the left.
		float outward = pose.leftArm() ? -1f : 1f;

		if (pose.draw() > 0f) {
			float d = pose.draw();
			arm.xRot = Mth.lerp(d, arm.xRot, 0.55f);
			arm.yRot = Mth.lerp(d, arm.yRot, outward * 0.35f);
			arm.zRot = Mth.lerp(d, arm.zRot, outward * 0.3f);
		}

		if (pose.thrust() > 0f) {
			float t = pose.thrust();
			arm.xRot = Mth.lerp(t, arm.xRot, model.head.xRot - Mth.HALF_PI);
			arm.yRot = Mth.lerp(t, arm.yRot, model.head.yRot);
			arm.zRot = Mth.lerp(t, arm.zRot, 0f);
		}
	}

	private static void applyPowerPose(HumanoidModel<?> model, PowerPose pose) {
		float a = pose.amount();

		if (a <= 0f) {
			return;
		}

		switch (pose.kind()) {
			case PULL -> {
				// Reach out along the look, then snap the hand back toward the chest.
				ModelPart arm = pose.leftArm() ? model.leftArm : model.rightArm;
				float inward = pose.leftArm() ? 1f : -1f;
				float reachX = model.head.xRot - Mth.HALF_PI;
				float x = Mth.lerp(pose.phase(), reachX, -1.0f);
				float y = Mth.lerp(pose.phase(), model.head.yRot, inward * 0.6f);
				arm.xRot = Mth.lerp(a, arm.xRot, x);
				arm.yRot = Mth.lerp(a, arm.yRot, y);
				arm.zRot = Mth.lerp(a, arm.zRot, 0f);
			}
			case JUMP_GATHER -> {
				both(model, a, 0.7f, 0f, 0.15f);
			}
			case JUMP_LAUNCH -> {
				both(model, a, -2.7f, 0f, 0.25f);
			}
			case GLIDE -> {
				both(model, a, -0.2f, 0f, 1.3f);
			}
			case HEAL -> {
				// Hands drawn together at the chest, head slightly bowed.
				model.rightArm.xRot = Mth.lerp(a, model.rightArm.xRot, -1.05f);
				model.rightArm.yRot = Mth.lerp(a, model.rightArm.yRot, -0.6f);
				model.rightArm.zRot = Mth.lerp(a, model.rightArm.zRot, 0f);
				model.leftArm.xRot = Mth.lerp(a, model.leftArm.xRot, -1.05f);
				model.leftArm.yRot = Mth.lerp(a, model.leftArm.yRot, 0.6f);
				model.leftArm.zRot = Mth.lerp(a, model.leftArm.zRot, 0f);
				model.head.xRot = model.head.xRot + 0.3f * a;
			}
			case LIGHTNING -> {
				// Arm(s) thrust along the look, the pair slightly spread.
				float x = model.head.xRot - Mth.HALF_PI;

				if (pose.bothArms() || !pose.leftArm()) {
					model.rightArm.xRot = Mth.lerp(a, model.rightArm.xRot, x);
					model.rightArm.yRot = Mth.lerp(a, model.rightArm.yRot, model.head.yRot - (pose.bothArms() ? 0.15f : 0f));
					model.rightArm.zRot = Mth.lerp(a, model.rightArm.zRot, 0f);
				}

				if (pose.bothArms() || pose.leftArm()) {
					model.leftArm.xRot = Mth.lerp(a, model.leftArm.xRot, x);
					model.leftArm.yRot = Mth.lerp(a, model.leftArm.yRot, model.head.yRot + (pose.bothArms() ? 0.15f : 0f));
					model.leftArm.zRot = Mth.lerp(a, model.leftArm.zRot, 0f);
				}
			}
			case SENSE -> {
				// The free hand up to the temple, two fingers resting there; the head tilts toward it.
				ModelPart arm = pose.leftArm() ? model.leftArm : model.rightArm;
				float inward = pose.leftArm() ? 1f : -1f;
				arm.xRot = Mth.lerp(a, arm.xRot, -2.3f);
				arm.yRot = Mth.lerp(a, arm.yRot, inward * 0.55f);
				arm.zRot = Mth.lerp(a, arm.zRot, inward * 0.35f);
				model.head.zRot = model.head.zRot - inward * 0.2f * a;
				model.head.xRot = model.head.xRot + 0.12f * a;
			}
			case LIFT -> {
				// The free arm raised along the look, palm up, with a slight uneven tremble.
				ModelPart arm = pose.leftArm() ? model.leftArm : model.rightArm;
				float tremble = PowerAnimations.tremble(pose.phase());
				arm.xRot = Mth.lerp(a, arm.xRot, model.head.xRot - Mth.HALF_PI - 0.35f + 0.035f * tremble);
				arm.yRot = Mth.lerp(a, arm.yRot, model.head.yRot);
				arm.zRot = Mth.lerp(a, arm.zRot, 0.03f * PowerAnimations.tremble(pose.phase() + 0.7f));
			}
		}
	}

	/** Lifted: arms thrown out to the sides and waving, legs kicking, each limb out of step. */
	private static void applyFlail(HumanoidModel<?> model, float amount, float time) {
		float a = Mth.clamp(amount, 0f, 1f);
		model.rightArm.xRot = Mth.lerp(a, model.rightArm.xRot, -0.7f + Mth.sin(time * 0.45f) * 0.55f);
		model.rightArm.yRot = Mth.lerp(a, model.rightArm.yRot, 0f);
		model.rightArm.zRot = Mth.lerp(a, model.rightArm.zRot, 1.0f + Mth.sin(time * 0.37f + 0.6f) * 0.35f);
		model.leftArm.xRot = Mth.lerp(a, model.leftArm.xRot, -0.7f + Mth.sin(time * 0.45f + 2.1f) * 0.55f);
		model.leftArm.yRot = Mth.lerp(a, model.leftArm.yRot, 0f);
		model.leftArm.zRot = Mth.lerp(a, model.leftArm.zRot, -1.0f - Mth.sin(time * 0.41f + 1.3f) * 0.35f);
		model.rightLeg.xRot = Mth.lerp(a, model.rightLeg.xRot, Mth.sin(time * 0.5f) * 0.5f);
		model.rightLeg.zRot = Mth.lerp(a, model.rightLeg.zRot, 0.1f + Mth.sin(time * 0.33f) * 0.08f);
		model.leftLeg.xRot = Mth.lerp(a, model.leftLeg.xRot, Mth.sin(time * 0.5f + Mth.PI) * 0.5f);
		model.leftLeg.zRot = Mth.lerp(a, model.leftLeg.zRot, -0.1f - Mth.sin(time * 0.33f + 1f) * 0.08f);
	}

	/** Both arms to the same pitch and mirrored outward roll. */
	private static void both(HumanoidModel<?> model, float a, float xRot, float yRot, float outwardRoll) {
		model.rightArm.xRot = Mth.lerp(a, model.rightArm.xRot, xRot);
		model.rightArm.yRot = Mth.lerp(a, model.rightArm.yRot, yRot);
		model.rightArm.zRot = Mth.lerp(a, model.rightArm.zRot, outwardRoll);
		model.leftArm.xRot = Mth.lerp(a, model.leftArm.xRot, xRot);
		model.leftArm.yRot = Mth.lerp(a, model.leftArm.yRot, -yRot);
		model.leftArm.zRot = Mth.lerp(a, model.leftArm.zRot, -outwardRoll);
	}
}
