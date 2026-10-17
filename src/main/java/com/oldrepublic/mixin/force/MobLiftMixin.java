package com.oldrepublic.mixin.force;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.oldrepublic.force.lift.LiftedTargets;

import net.minecraft.world.entity.Mob;

/**
 * Pauses a mob's AI while Telekinetic Lift holds it ({@link LiftedTargets#isLifted}): no goals,
 * no navigation and no targeting run, so a lifted creature only flails in the air. Physics and
 * damage still tick; the AI resumes on the first tick after the lift releases it.
 */
@Mixin(Mob.class)
abstract class MobLiftMixin {
	@Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
	private void oldrepublic$pauseWhileLifted(CallbackInfo info) {
		if (LiftedTargets.isLifted((Mob) (Object) this)) {
			info.cancel();
		}
	}
}
