package com.oldrepublic.mixin.force;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.oldrepublic.force.stasis.StasisHolds;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Force Stasis on creatures (MILESTONE_5.md 9.2): while one has the {@code oldrepublic:stasis} effect,
 * {@code LivingEntity#isImmobile} is true, so {@code aiStep} zeroes {@code xxa}, {@code zza} and {@code jumping} and
 * skips {@code serverAiStep} (goals, navigation, controls) while {@code travel} still runs: no AI and no input, but
 * gravity and knockback stay. Vanilla's body is only {@code isDeadOrDying()}, so the cancel hides nothing. Players are
 * left alone: a player in stasis (PvP) is held through the M3 {@code Holds} ({@code Player#isImmobile} overrides this
 * method and calls it first; that override was not probed, so players never depend on it).
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityStasisMixin {
	@Inject(method = "isImmobile", at = @At("HEAD"), cancellable = true)
	private void oldrepublic$stasis(CallbackInfoReturnable<Boolean> info) {
		LivingEntity self = (LivingEntity) (Object) this;

		if (!(self instanceof Player) && StasisHolds.isFrozen(self)) {
			info.setReturnValue(true);
		}
	}
}
