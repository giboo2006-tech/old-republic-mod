package com.oldrepublic.mixin.skill;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.oldrepublic.skill.SkillPassives;

import net.minecraft.world.entity.player.Player;

/**
 * {@code trailwise} (spec 10.3): scales the food exhaustion a player takes while sprinting
 * ({@link SkillPassives#sprintExhaustion}). Every other source of exhaustion is unchanged.
 */
@Mixin(Player.class)
abstract class PlayerExhaustionMixin {
	@ModifyVariable(method = "causeFoodExhaustion", at = @At("HEAD"), argsOnly = true)
	private float oldrepublic$trailwiseSprint(float amount) {
		return SkillPassives.sprintExhaustion((Player) (Object) this, amount);
	}
}
