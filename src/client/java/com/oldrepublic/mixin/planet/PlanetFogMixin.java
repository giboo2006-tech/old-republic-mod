package com.oldrepublic.mixin.planet;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.oldrepublic.client.planet.PlanetFog;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;

/**
 * Ghessar's sandstorm fog and the Tyrvann mire's ({@link PlanetFog}): applied at the tail of the
 * atmospheric fog, the environment that runs while the camera is in air, after vanilla has set the
 * distances (rain, a boss bar's world fog) and the colour. Water, lava, powder snow, blindness and
 * darkness keep their own fog.
 */
@Mixin(targets = "net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment")
abstract class PlanetFogMixin {
	@Inject(method = "setupFog", at = @At("TAIL"))
	private void oldrepublic$planetFog(FogData fog, Camera camera, ClientLevel level, float renderDistance, DeltaTracker deltaTracker, CallbackInfo ci) {
		PlanetFog.apply(fog, level, deltaTracker.getGameTimeDeltaPartialTick(false));
	}
}
