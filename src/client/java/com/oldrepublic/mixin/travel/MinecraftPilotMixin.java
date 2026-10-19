package com.oldrepublic.mixin.travel;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.oldrepublic.client.travel.Piloting;

import net.minecraft.client.Minecraft;

/**
 * No vanilla clicks at the helm (spec MILESTONE_5.md 4.1, 4.3): the pilot's crosshair would hit
 * their own ship (pickable from inside), so attacking, breaking and using are cancelled while they
 * fly by hand. The attack and use keys are read for the ship's weapons instead (5.3).
 */
@Mixin(Minecraft.class)
abstract class MinecraftPilotMixin {
	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void oldrepublic$noAttackAtHelm(CallbackInfoReturnable<Boolean> info) {
		if (Piloting.isPiloting()) {
			info.setReturnValue(false);
		}
	}

	@Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
	private void oldrepublic$noBreakingAtHelm(boolean down, CallbackInfo info) {
		if (Piloting.isPiloting()) {
			info.cancel();
		}
	}

	@Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
	private void oldrepublic$noUseAtHelm(CallbackInfo info) {
		if (Piloting.isPiloting()) {
			info.cancel();
		}
	}
}
