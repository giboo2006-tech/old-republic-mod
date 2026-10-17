package com.oldrepublic.mixin.boss;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.oldrepublic.client.boss.music.BossMusic;

import net.minecraft.client.sounds.MusicManager;

/**
 * Holds off vanilla background music while a boss music loop plays or fades (spec 8.5). The
 * vanilla track that was playing is stopped once when the boss loop starts ({@link BossMusic});
 * skipping the music manager's tick keeps a new one from starting until the loop has ended, without
 * piling up the delay that repeated {@code stopPlaying()} calls would.
 */
@Mixin(MusicManager.class)
public abstract class MusicManagerMixin {
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void oldrepublic$holdForBossMusic(CallbackInfo ci) {
		if (BossMusic.holdsVanillaMusic()) {
			ci.cancel();
		}
	}
}
