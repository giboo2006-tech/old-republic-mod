package com.oldrepublic.client.saber;

import com.oldrepublic.client.config.ClientConfig;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * One entity's blade hum (a player or a saber NPC). Follows the entity and stops itself as soon as
 * it no longer holds a fully lit saber, dies, is removed or is not in the client's level: this
 * covers retract, throw, drop, item switch, death, disconnect and dimension change.
 */
final class SaberHumSound extends AbstractTickableSoundInstance {
	private static final float VOLUME = 0.6f;
	private final LivingEntity entity;

	SaberHumSound(LivingEntity entity) {
		super(ModSounds.SABER_HUM, entity instanceof Player ? SoundSource.PLAYERS : SoundSource.HOSTILE, SoundInstance.createUnseededRandom());
		this.entity = entity;
		this.looping = true;
		this.delay = 0;
		this.attenuation = SoundInstance.Attenuation.LINEAR;
		this.volume = VOLUME * ClientConfig.get().modSoundVolume;
		follow();
	}

	@Override
	public void tick() {
		Minecraft mc = Minecraft.getInstance();

		if (entity.isRemoved() || !entity.isAlive() || entity.level() != mc.level || !SaberAudio.humming(entity)) {
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
		this.x = entity.getX();
		this.y = entity.getY() + entity.getBbHeight() * 0.55;
		this.z = entity.getZ();
	}
}
