package com.oldrepublic.mixin.foes;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.oldrepublic.force.choke.ForceChoke;

import net.minecraft.world.entity.LivingEntity;

/**
 * Muffles a Force-choked creature's own voice, its hurt sound most of all (spec MILESTONE_4.md
 * 9.3): its sound volume is cut to {@link #MUFFLED} while a Choke holds it. Server side only (the
 * choke state lives there, and the server plays hurt and ambient sounds); creatures that override
 * the volume keep their own.
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityChokeSoundMixin {
	private static final float MUFFLED = 0.3f;

	// API-CHECK: LivingEntity#getSoundVolume() (protected float, no arguments) exists in 26.2 and is the volume playHurtSound uses.
	@Inject(method = "getSoundVolume", at = @At("RETURN"), cancellable = true)
	private void oldrepublic$muffleWhileChoked(CallbackInfoReturnable<Float> info) {
		LivingEntity self = (LivingEntity) (Object) this;

		if (!self.level().isClientSide() && ForceChoke.chokerOf(self) != null) {
			info.setReturnValue(info.getReturnValue() * MUFFLED);
		}
	}
}
