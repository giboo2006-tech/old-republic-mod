package com.oldrepublic.mixin.force;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.oldrepublic.force.ForceMobEffects;

import net.minecraft.client.player.LocalPlayer;

/**
 * A frightened player cannot sprint (MILESTONE_5.md 9.2): while the local player has the {@code oldrepublic:fear}
 * effect (a player's own effects are synced to them), a sprint never starts. A sprint already running is stopped by
 * {@code client.force.ForceM5Client}'s tick and by the server.
 */
@Mixin(LocalPlayer.class)
abstract class LocalPlayerFearMixin {
	// API-CHECK: private boolean LocalPlayer#canStartSprinting() (decoded in the M5 follow-up probe, 3.8)
	@Inject(method = "canStartSprinting", at = @At("HEAD"), cancellable = true)
	private void oldrepublic$afraid(CallbackInfoReturnable<Boolean> info) {
		if (((LocalPlayer) (Object) this).hasEffect(ForceMobEffects.FEAR)) {
			info.setReturnValue(false);
		}
	}
}
