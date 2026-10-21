package com.oldrepublic.mixin.saber;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.oldrepublic.client.saber.SaberFirstPerson;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * First person: a lightsaber is held, swung and raised to guard by {@link SaberFirstPerson} instead of the vanilla held-item
 * transforms (the arm offset, the swing bob and the sword-block turn), then drawn as vanilla draws any held item.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererSaberMixin {
	// API-CHECK: ItemInHandRenderer#submitArmWithItem(AbstractClientPlayer, float partialTick, float pitch, InteractionHand,
	// float swing, ItemStack, float equip, PoseStack, SubmitNodeCollector, int light) (javap, tools/api-probe/held.txt);
	// it draws one hand, between its own push and pop of the pose stack.
	@Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
	private void oldrepublic$holdSaber(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand, float swing, ItemStack stack,
			float equip, PoseStack poseStack, SubmitNodeCollector collector, int light, CallbackInfo ci) {
		if (!SaberFirstPerson.handles(player, stack)) {
			return;
		}

		HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
		poseStack.pushPose();
		SaberFirstPerson.apply(player, hand, arm, stack, partialTick, swing, equip, poseStack);
		((ItemInHandRenderer) (Object) this).renderItem(player, stack,
				arm == HumanoidArm.RIGHT ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND, poseStack, collector, light);
		poseStack.popPose();
		ci.cancel();
	}
}
