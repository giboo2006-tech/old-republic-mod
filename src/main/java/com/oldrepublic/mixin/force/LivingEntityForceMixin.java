package com.oldrepublic.mixin.force;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.oldrepublic.force.jump.ForceJump;
import com.oldrepublic.force.pull.ForcePull;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Server hooks of the Force powers on every living entity: the Reeling Pull bonus on the next saber
 * hit ({@link ForcePull#modifyDamage}) and the fall allowance of a Force Jump
 * ({@link ForceJump#adjustFall}). {@code Player} overrides both methods and calls these afterwards.
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityForceMixin {
	// API-CHECK: capturing the target method's arguments (level, source) after the modified
	// variable in a @ModifyVariable handler (supported since Mixin 0.8; same pattern as the saber mixin).
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float oldrepublic$reelBonus(float amount, ServerLevel level, DamageSource source) {
		return ForcePull.modifyDamage((LivingEntity) (Object) this, source, amount);
	}

	@ModifyVariable(method = "causeFallDamage", at = @At("HEAD"), argsOnly = true)
	private double oldrepublic$jumpFall(double fallDistance) {
		return ForceJump.adjustFall((LivingEntity) (Object) this, fallDistance);
	}
}
