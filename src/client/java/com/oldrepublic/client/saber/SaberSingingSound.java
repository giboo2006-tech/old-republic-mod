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
 * The singing crystal's hum layer ({@code saber.hum.singing}, spec 8.8): a high, slowly beating
 * crystal tone laid over the normal {@link SaberHumSound} of a saber built around a singing crystal.
 * It follows the entity and stops on exactly the hum's rules (retract, throw, drop, item or slot
 * switch, death, removal, level change), because it stops as soon as the entity no longer holds a
 * fully lit singing saber.
 */
final class SaberSingingSound extends AbstractTickableSoundInstance {
	private static final float VOLUME = 0.45f;
	private final LivingEntity entity;

	SaberSingingSound(LivingEntity entity) {
		super(ModSounds.SABER_HUM_SINGING, entity instanceof Player ? SoundSource.PLAYERS : SoundSource.HOSTILE,
				SoundInstance.createUnseededRandom());
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

		if (entity.isRemoved() || !entity.isAlive() || entity.level() != mc.level || !SaberAudio.singing(entity)) {
			stop();
			return;
		}

		this.volume = VOLUME * ClientConfig.get().modSoundVolume;
		follow();
	}

	/** Stops the layer now (client reset). */
	void end() {
		stop();
	}

	private void follow() {
		this.x = entity.getX();
		this.y = entity.getY() + entity.getBbHeight() * 0.55;
		this.z = entity.getZ();
	}
}
