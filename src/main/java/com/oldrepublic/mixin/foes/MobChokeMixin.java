package com.oldrepublic.mixin.foes;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.oldrepublic.force.choke.ChokedTargets;

import net.minecraft.world.entity.Mob;

/**
 * Pauses a mob's AI while Force Choke holds it ({@link ChokedTargets#isChoked}): no goals, no
 * navigation and no targeting run, so a choked creature cannot attack or move. Physics and damage
 * still tick; the AI resumes on the first tick after the release. The same injection as the
 * lead's {@code MobLiftMixin}, which it sits beside.
 */
@Mixin(Mob.class)
abstract class MobChokeMixin {
	@Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
	private void oldrepublic$pauseWhileChoked(CallbackInfo info) {
		if (ChokedTargets.isChoked((Mob) (Object) this)) {
			info.cancel();
		}
	}
}
