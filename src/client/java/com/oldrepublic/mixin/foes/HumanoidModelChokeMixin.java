package com.oldrepublic.mixin.foes;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.oldrepublic.client.force.PowerAnimations;
import com.oldrepublic.client.force.PowerPose;
import com.oldrepublic.client.force.choke.ChokeFx;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.util.Mth;

/**
 * A humanoid held by Force Choke ({@link ChokeFx#THROAT}): both hands go to its throat, the head
 * tips back a little and the feet hang, toes down, with a weak kick (spec MILESTONE_4.md 9.3). Also
 * the casters' arm poses for the M4 powers ({@link PowerPose} kinds {@code BARRIER} and
 * {@code CHOKE}, which the lead's {@code HumanoidModelMixin} leaves alone): Barrier holds both palms
 * out at chest height; Choke raises the free arm toward the target, straining. Applied after the
 * vanilla animation; player sleeves and trousers follow their limbs.
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelChokeMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
	private void oldrepublic$applyCasterPose(HumanoidRenderState state, CallbackInfo ci) {
		PowerPose pose = state.getData(PowerPose.KEY);

		if (pose == null || pose.amount() <= 0f) {
			return;
		}

		HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
		float a = pose.amount();

		switch (pose.kind()) {
			case BARRIER -> {
				// Both arms forward at chest height, a little apart, palms out; a slow breathing sway.
				float sway = Mth.sin(pose.phase() * 0.12f) * 0.04f;
				model.rightArm.xRot = Mth.lerp(a, model.rightArm.xRot, -1.4f + sway);
				model.rightArm.yRot = Mth.lerp(a, model.rightArm.yRot, -0.18f);
				model.rightArm.zRot = Mth.lerp(a, model.rightArm.zRot, 0f);
				model.leftArm.xRot = Mth.lerp(a, model.leftArm.xRot, -1.4f - sway);
				model.leftArm.yRot = Mth.lerp(a, model.leftArm.yRot, 0.18f);
				model.leftArm.zRot = Mth.lerp(a, model.leftArm.zRot, 0f);
			}
			case CHOKE -> {
				// The free arm raised along the look toward the target, the clawed hand straining.
				ModelPart arm = pose.leftArm() ? model.leftArm : model.rightArm;
				float strain = PowerAnimations.tremble(pose.phase() * 1.6f);
				arm.xRot = Mth.lerp(a, arm.xRot, model.head.xRot - Mth.HALF_PI - 0.25f + 0.03f * strain);
				arm.yRot = Mth.lerp(a, arm.yRot, model.head.yRot);
				arm.zRot = Mth.lerp(a, arm.zRot, 0.025f * PowerAnimations.tremble(pose.phase() * 1.6f + 0.9f));
			}
			default -> {
			}
		}
	}

	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
	private void oldrepublic$applyChokeThroat(HumanoidRenderState state, CallbackInfo ci) {
		Float throat = state.getData(ChokeFx.THROAT);

		if (throat == null || throat <= 0f) {
			return;
		}

		HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
		float a = Mth.clamp(throat, 0f, 1f);
		float t = state.ageInTicks;
		float shake = Mth.sin(t * 1.7f) * 0.04f;
		// Hands up to the throat: arms raised forward and folded inward under the chin.
		model.rightArm.xRot = Mth.lerp(a, model.rightArm.xRot, -2.0f + shake);
		model.rightArm.yRot = Mth.lerp(a, model.rightArm.yRot, -0.55f);
		model.rightArm.zRot = Mth.lerp(a, model.rightArm.zRot, 0.25f);
		model.leftArm.xRot = Mth.lerp(a, model.leftArm.xRot, -2.0f - shake);
		model.leftArm.yRot = Mth.lerp(a, model.leftArm.yRot, 0.55f);
		model.leftArm.zRot = Mth.lerp(a, model.leftArm.zRot, -0.25f);
		model.head.xRot = Mth.lerp(a, model.head.xRot, -0.35f);
		// Feet hanging, toes down, a weak kick out of step.
		model.rightLeg.xRot = Mth.lerp(a, model.rightLeg.xRot, 0.12f + Mth.sin(t * 0.6f) * 0.18f);
		model.leftLeg.xRot = Mth.lerp(a, model.leftLeg.xRot, 0.12f + Mth.sin(t * 0.6f + 2.4f) * 0.18f);
		model.rightLeg.zRot = Mth.lerp(a, model.rightLeg.zRot, 0.04f);
		model.leftLeg.zRot = Mth.lerp(a, model.leftLeg.zRot, -0.04f);
	}
}
