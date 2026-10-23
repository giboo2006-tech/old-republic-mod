package com.oldrepublic.mixin.force;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.oldrepublic.client.force.FearFx;
import com.oldrepublic.client.force.MindTrickFx;
import com.oldrepublic.client.force.PowerAnimations;
import com.oldrepublic.client.force.PowerPose;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;

/**
 * Copies the Telekinetic Lift flail of any living entity (mobs and players held by a PvP Lift) into
 * its render state as {@link PowerPose#FLAIL}; the humanoid model swings the arms and legs by it.
 * Players' power poses come from {@code AvatarRendererMixin}; this covers every lifted creature, and
 * every frightened ({@link FearFx#SHIVER}) or tricked ({@link MindTrickFx#CALM}) one.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererLiftMixin {
	// The erased generic method, the same target as the compiling LivingEntityRendererSaberMixin; subclasses
	// call super, so RETURN here runs for every living renderer.
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("RETURN"))
	private void oldrepublic$extractLiftFlail(LivingEntity entity, LivingEntityRenderState state, float partialTick, CallbackInfo ci) {
		float flail = PowerAnimations.flail(entity, partialTick);
		// Null clears the flail on a reused state.
		state.setData(PowerPose.FLAIL, flail > 0f ? flail : null);
		float shiver = FearFx.shiver(entity.getId(), partialTick);
		state.setData(FearFx.SHIVER, shiver > 0f ? shiver : null);
		float calm = MindTrickFx.calm(entity.getId(), partialTick);
		state.setData(MindTrickFx.CALM, calm > 0f ? calm : null);
	}
}
