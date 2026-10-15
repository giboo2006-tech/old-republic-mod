package com.oldrepublic.mixin.force;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.oldrepublic.client.force.PushAnimations;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;

/** First-person: the hands pull back while a push charges and jab forward on release. */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
	@Inject(method = "submitHandsWithItems", at = @At("HEAD"))
	private void oldrepublic$pushOffsetStart(float partialTick, PoseStack poseStack, SubmitNodeCollector collector, LocalPlayer player, int light, CallbackInfo ci) {
		poseStack.pushPose();
		PushAnimations.applyFirstPerson(poseStack, player, partialTick);
	}

	@Inject(method = "submitHandsWithItems", at = @At("RETURN"))
	private void oldrepublic$pushOffsetEnd(float partialTick, PoseStack poseStack, SubmitNodeCollector collector, LocalPlayer player, int light, CallbackInfo ci) {
		poseStack.popPose();
	}
}
