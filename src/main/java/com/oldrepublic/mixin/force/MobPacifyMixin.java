package com.oldrepublic.mixin.force;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.oldrepublic.force.mindtrick.Pacified;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * Mind Trick's pacify (MILESTONE_5.md 9.2, 9.8): while a mob is pacified it may not take a new target, so its target
 * goals and hurt reactions cannot turn it back to the fight. Clearing the target always passes, and so does the ally a
 * confused mob is turned on ({@link Pacified#blocksTarget}). The mod's mobs that override {@code setTarget} all call
 * {@code super.setTarget}, so they pass through here too.
 */
@Mixin(Mob.class)
abstract class MobPacifyMixin {
	// API-CHECK: Mob#setTarget(LivingEntity) (public, void) is where every target change goes; its body was not probed
	@Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
	private void oldrepublic$pacified(@Nullable LivingEntity target, CallbackInfo info) {
		if (Pacified.blocksTarget((Mob) (Object) this, target)) {
			info.cancel();
		}
	}
}
