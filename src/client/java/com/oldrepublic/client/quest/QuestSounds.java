package com.oldrepublic.client.quest;

import com.oldrepublic.client.config.ClientConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/** Small helpers for playing the quest and HUD sounds with the mod volume applied. */
public final class QuestSounds {
	/** A personal, non-positional sound (UI category). */
	public static SimpleSoundInstance ui(SoundEvent event, float pitch, float volume) {
		SimpleSoundInstance sound = SimpleSoundInstance.forUI(event, pitch, volume * ClientConfig.get().modSoundVolume);
		Minecraft.getInstance().getSoundManager().play(sound);
		return sound;
	}

	/** A positional one-shot heard by this client only. */
	public static void at(ClientLevel level, Vec3 pos, SoundEvent event, float volume, float pitch) {
		level.playLocalSound(pos.x, pos.y, pos.z, event, SoundSource.PLAYERS, volume * ClientConfig.get().modSoundVolume, pitch, false);
	}

	private QuestSounds() {
	}
}
