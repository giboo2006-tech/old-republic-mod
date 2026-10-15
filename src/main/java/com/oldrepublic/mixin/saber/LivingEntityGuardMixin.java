package com.oldrepublic.mixin.saber;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.oldrepublic.saber.SaberGuard;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Lets the saber guard reduce incoming damage before anything else in
 * {@code LivingEntity#hurtServer} uses the amount (invulnerability frames, armour, the Fabric
 * damage events). Player difficulty scaling has already happened in {@code Player#hurtServer},
 * which calls this method afterwards, so the reduction is applied exactly once.
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityGuardMixin {
	// API-CHECK: capturing the target method's arguments (level, source) after the modified
	// variable in a @ModifyVariable handler (supported since Mixin 0.8).
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float oldrepublic$saberGuard(float amount, ServerLevel level, DamageSource source) {
		return SaberGuard.modifyIncomingDamage((LivingEntity) (Object) this, level, source, amount);
	}
}
