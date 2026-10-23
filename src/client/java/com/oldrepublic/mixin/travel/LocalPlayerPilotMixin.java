package com.oldrepublic.mixin.travel;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.oldrepublic.client.travel.FlightClient;

import net.minecraft.client.player.LocalPlayer;

/**
 * The pilot's controls reach their ship (spec MILESTONE_5.md 4.1, 4.3): at the end of the local
 * player's {@code rideTick}, after this tick's input was read and the vehicle packet sent, as vanilla
 * feeds a boat. The ship ticks before its riders, so it flies with them on its next tick.
 */
@Mixin(LocalPlayer.class)
abstract class LocalPlayerPilotMixin {
	@Inject(method = "rideTick", at = @At("TAIL"))
	private void oldrepublic$steerShip(CallbackInfo info) {
		FlightClient.steer((LocalPlayer) (Object) this);
	}
}
