package com.oldrepublic.mixin.equipment;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.oldrepublic.client.equipment.BlasterPoses;

import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;

/**
 * Copies a player's blaster pose into its render state after the vanilla arm poses are set, so a
 * shouldered long gun can take the two-handed hold ({@link BlasterPoses}).
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererBlasterMixin {
	// Same target as the Force package's AvatarRendererMixin (a second RETURN inject on the same method is fine).
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("RETURN"))
	private void oldrepublic$extractBlasterPose(Avatar entity, AvatarRenderState state, float partialTick, CallbackInfo ci) {
		BlasterPoses.extract(entity, state, partialTick);
	}
}
