package com.oldrepublic.client.force;

import com.oldrepublic.client.config.ClientConfig;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * The rising air rush while a push charges. Follows the charging entity and stops itself as soon
 * as the entity stops charging, dies, is removed or is no longer in the client's level (cancel,
 * release, death, disconnect, dimension change).
 */
final class ChargeSoundInstance extends AbstractTickableSoundInstance {
	private final LivingEntity entity;

	ChargeSoundInstance(LivingEntity entity) {
		super(ModSounds.FORCE_PUSH_CHARGE, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
		this.entity = entity;
		this.looping = true;
		this.delay = 0;
		this.attenuation = SoundInstance.Attenuation.LINEAR;
		this.volume = 0.3f * ClientConfig.get().modSoundVolume;
		this.pitch = 0.85f;
		follow();
	}

	@Override
	public void tick() {
		Minecraft mc = Minecraft.getInstance();

		if (entity.isRemoved() || !entity.isAlive() || entity.level() != mc.level || !PushAnimations.isCharging(entity.getId())) {
			stop();
			return;
		}

		float charge = PushAnimations.charge(entity.getId(), 0f);
		this.volume = (0.3f + 0.7f * charge) * ClientConfig.get().modSoundVolume;
		this.pitch = 0.85f + 0.35f * charge;
		follow();
	}

	/** Ends the loop now (used when all client state is reset). */
	void end() {
		stop();
	}

	private void follow() {
		this.x = entity.getX();
		this.y = entity.getY() + entity.getBbHeight() * 0.7;
		this.z = entity.getZ();
	}
}
