package com.oldrepublic.mixin.equipment;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.oldrepublic.client.equipment.BlasterPoses;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;

/** First person: the blaster's recoil kick on the view model ({@link BlasterPoses#applyFirstPerson}). */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererBlasterMixin {
	@Inject(method = "submitHandsWithItems", at = @At("HEAD"))
	private void oldrepublic$recoilStart(float partialTick, PoseStack poseStack, SubmitNodeCollector collector, LocalPlayer player, int light, CallbackInfo ci) {
		poseStack.pushPose();
		BlasterPoses.applyFirstPerson(poseStack, player, partialTick);
	}

	@Inject(method = "submitHandsWithItems", at = @At("RETURN"))
	private void oldrepublic$recoilEnd(float partialTick, PoseStack poseStack, SubmitNodeCollector collector, LocalPlayer player, int light, CallbackInfo ci) {
		poseStack.popPose();
	}
}
