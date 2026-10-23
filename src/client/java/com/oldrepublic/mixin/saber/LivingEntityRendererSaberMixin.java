package com.oldrepublic.mixin.saber;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.oldrepublic.client.saber.SaberAnimations;
import com.oldrepublic.client.saber.SaberPose;
import com.oldrepublic.client.saber.SaberStance;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;

/**
 * Copies the saber technique pose of any living entity (players and saber NPCs) into its render
 * state as a {@link SaberPose}, and its idle {@link SaberStance}; the humanoid model applies them.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererSaberMixin {
	// API-CHECK: LivingEntityRenderer#extractRenderState(LivingEntity, LivingEntityRenderState, float) is the erased
	// generic method (javap lists it); subclasses call super, so RETURN here runs for every living renderer.
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("RETURN"))
	private void oldrepublic$extractSaberPose(LivingEntity entity, LivingEntityRenderState state, float partialTick, CallbackInfo ci) {
		// Null clears the pose on a reused state.
		state.setData(SaberPose.KEY, SaberAnimations.pose(entity, partialTick));
		// The idle stance and a dual-wielder's off-arm guard (MILESTONE_5.md 10.1, 10.2).
		state.setData(SaberStance.KEY, SaberStance.of(entity, partialTick));
	}
}
