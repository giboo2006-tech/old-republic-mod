package com.oldrepublic.mixin.space;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.oldrepublic.client.space.SpaceCrew;

import net.minecraft.client.Minecraft;

/**
 * No vanilla clicks in the gunner seat (spec MILESTONE_5.md 5.2): the gunner's attack fires the
 * dorsal turret, so attacking, breaking and using are cancelled while they man it, the same way as
 * the pilot's at the helm (W1's {@code MinecraftPilotMixin}). The attack key is read for the turret
 * instead ({@code ShipWeaponInput}).
 */
@Mixin(Minecraft.class)
abstract class MinecraftGunnerMixin {
	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void oldrepublic$noAttackAtTurret(CallbackInfoReturnable<Boolean> info) {
		if (SpaceCrew.isGunner()) {
			info.setReturnValue(false);
		}
	}

	@Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
	private void oldrepublic$noBreakingAtTurret(boolean down, CallbackInfo info) {
		if (SpaceCrew.isGunner()) {
			info.cancel();
		}
	}

	@Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
	private void oldrepublic$noUseAtTurret(CallbackInfo info) {
		if (SpaceCrew.isGunner()) {
			info.cancel();
		}
	}
}
