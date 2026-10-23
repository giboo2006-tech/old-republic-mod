package com.oldrepublic.mixin.equipment;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.oldrepublic.client.equipment.BlasterPose;
import com.oldrepublic.client.equipment.BlasterPoses;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

/** Applies a {@link BlasterPose} (the pistol aim, the shot's kick) after the vanilla humanoid animation. */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelBlasterMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
	private void oldrepublic$applyBlasterPose(HumanoidRenderState state, CallbackInfo ci) {
		BlasterPose pose = state.getData(BlasterPose.KEY);

		if (pose != null) {
			BlasterPoses.apply((HumanoidModel<?>) (Object) this, pose);
		}
	}
}
