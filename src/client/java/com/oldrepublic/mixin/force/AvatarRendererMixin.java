package com.oldrepublic.mixin.force;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.oldrepublic.client.force.PushAnimations;
import com.oldrepublic.client.force.PowerAnimations;
import com.oldrepublic.client.force.PowerPose;
import com.oldrepublic.client.force.PushPose;

import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;

/**
 * Copies the Force power arm poses of a player into its render state ({@link PushPose},
 * {@link PowerPose}) and shows the player crouched while a Force Jump gathers.
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("RETURN"))
	private void oldrepublic$extractPushPose(Avatar entity, AvatarRenderState state, float partialTick, CallbackInfo ci) {
		// Null clears the pose on a reused state (Fabric stores data in a map that accepts null).
		state.setData(PushPose.KEY, PushAnimations.pose(entity, partialTick));
		state.setData(PowerPose.KEY, PowerAnimations.pose(entity, partialTick));

		if (PowerAnimations.isCrouching(entity.getId())) {
			state.isCrouching = true;
		}
	}
}
