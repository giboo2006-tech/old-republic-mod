package com.oldrepublic.mixin.foes;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.oldrepublic.client.boss.kaast.KaastMusic;

import net.minecraft.client.sounds.MusicManager;

/**
 * Holds off vanilla background music while a Kaast fight loop plays or fades (spec MILESTONE_4.md
 * 13.4), as the M3 boss music does: {@link KaastMusic} stops the vanilla track once when its first
 * loop starts, and skipping the music manager's tick keeps a new one from starting until the loop
 * has ended.
 */
@Mixin(MusicManager.class)
public abstract class MusicManagerKaastMixin {
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void oldrepublic$holdForKaastMusic(CallbackInfo ci) {
		if (KaastMusic.holdsVanillaMusic()) {
			ci.cancel();
		}
	}
}
