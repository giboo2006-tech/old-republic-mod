package com.oldrepublic.mixin.force;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.oldrepublic.client.force.PushPose;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.util.Mth;

/**
 * Applies a {@link PushPose} after the vanilla humanoid animation: the push arm draws back while
 * charging and thrusts forward, palm open, along the look direction on release. Player sleeves are
 * children of the arms, so they follow.
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
	private void oldrepublic$applyPushPose(HumanoidRenderState state, CallbackInfo ci) {
		PushPose pose = state.getData(PushPose.KEY);

		if (pose == null) {
			return;
		}

		HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
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
}
