package com.oldrepublic.mixin.planet;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.oldrepublic.planet.RidgeLightning;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LightningBolt;

/**
 * The strike hook of Tyrvann's stormglass (spec MILESTONE_5.md 3.3): on the server tick a bolt
 * strikes (where vanilla powers lightning rods), real lightning hands itself to
 * {@link RidgeLightning#onStrike}. Cosmetic bolts ({@code visualOnly}, such as the Hymnal's arch)
 * grow nothing.
 */
@Mixin(LightningBolt.class)
abstract class LightningStrikeMixin {
	@Shadow
	private boolean visualOnly;

	@Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LightningBolt;powerLightningRod()V"))
	private void oldrepublic$stormglass(CallbackInfo ci) {
		LightningBolt bolt = (LightningBolt) (Object) this;

		if (!visualOnly && bolt.level() instanceof ServerLevel level) {
			RidgeLightning.onStrike(level, bolt);
		}
	}
}
