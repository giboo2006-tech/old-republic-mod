package com.oldrepublic.client.saber;

import com.oldrepublic.client.config.ClientConfig;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/**
 * One player's blade hum. Follows the player and stops itself as soon as the player no longer
 * holds a fully lit saber, dies, is removed or is not in the client's level: this covers retract,
 * drop, item switch, death, disconnect and dimension change.
 */
final class SaberHumSound extends AbstractTickableSoundInstance {
	private static final float VOLUME = 0.6f;
	private final Player player;

	SaberHumSound(Player player) {
		super(ModSounds.SABER_HUM, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
		this.player = player;
		this.looping = true;
		this.delay = 0;
		this.attenuation = SoundInstance.Attenuation.LINEAR;
		this.volume = VOLUME * ClientConfig.get().modSoundVolume;
		follow();
	}

	@Override
	public void tick() {
		Minecraft mc = Minecraft.getInstance();

		if (player.isRemoved() || !player.isAlive() || player.level() != mc.level || !SaberAudio.humming(player)) {
			stop();
			return;
		}

		this.volume = VOLUME * ClientConfig.get().modSoundVolume;
		follow();
	}

	/** Stops the hum now (client reset). */
	void end() {
		stop();
	}

	private void follow() {
		this.x = player.getX();
		this.y = player.getY() + player.getBbHeight() * 0.55;
		this.z = player.getZ();
	}
}
