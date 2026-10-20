package com.oldrepublic.mixin.planet;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.oldrepublic.client.planet.StormFlash;

import net.minecraft.client.multiplayer.ClientLevel;

/**
 * The storm-flash setting (spec MILESTONE_5.md 15, 14.4): lightning flashes the sky through
 * {@code setSkyFlashTime(2)} on each tick a bolt is lit. With {@code ClientConfig.stormFlashes} or
 * {@code flashingEffects} off those flashes are dropped and {@link StormFlash} draws a steady
 * brightening instead. The level's own countdown (values below 2) is left alone.
 */
@Mixin(ClientLevel.class)
abstract class StormFlashMixin {
	@Inject(method = "setSkyFlashTime", at = @At("HEAD"), cancellable = true)
	private void oldrepublic$steadyFlash(int time, CallbackInfo ci) {
		if (time >= 2 && StormFlash.dimmed()) {
			StormFlash.flash();
			ci.cancel();
		}
	}
}
