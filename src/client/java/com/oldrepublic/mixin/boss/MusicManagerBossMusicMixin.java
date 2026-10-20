package com.oldrepublic.mixin.boss;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.oldrepublic.client.boss.music.BossMusicService;

import net.minecraft.client.sounds.MusicManager;

/**
 * Holds off vanilla background music while any boss loop plays or fades (spec MILESTONE_5.md 12.5),
 * the one {@code MusicManager} mixin of the generic boss music service. The vanilla track that was
 * playing is stopped once when the first boss loop starts ({@link BossMusicService}); skipping the
 * music manager's tick keeps a new one from starting until the loop has ended, without piling up the
 * delay that repeated {@code stopPlaying()} calls would. It replaces the M3 {@code MusicManagerMixin}
 * and the M4 {@code MusicManagerKaastMixin} (same injection).
 */
@Mixin(MusicManager.class)
public abstract class MusicManagerBossMusicMixin {
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void oldrepublic$holdForBossMusic(CallbackInfo ci) {
		if (BossMusicService.holdsVanillaMusic()) {
			ci.cancel();
		}
	}
}
