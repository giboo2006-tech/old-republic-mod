package com.oldrepublic.mixin.foes;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.oldrepublic.client.force.choke.ChokeFx;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;

/**
 * Copies how far a Force-choked creature's hands are at its throat ({@link ChokeFx#throat}) into its
 * render state as {@link ChokeFx#THROAT}; {@code HumanoidModelChokeMixin} draws it. The same target
 * as the lead's {@code LivingEntityRendererLiftMixin}.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererChokeMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("RETURN"))
	private void oldrepublic$extractChokeThroat(LivingEntity entity, LivingEntityRenderState state, float partialTick, CallbackInfo ci) {
		float throat = ChokeFx.throat(entity.getId(), partialTick);
		// Null clears it on a reused state.
		state.setData(ChokeFx.THROAT, throat > 0f ? throat : null);
	}
}
