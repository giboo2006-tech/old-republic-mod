package com.oldrepublic.client.force;

import java.util.function.BooleanSupplier;

import com.oldrepublic.client.config.ClientConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * A looped power sound that follows its entity and stops itself as soon as {@code active} turns
 * false or the entity dies, is removed or leaves the client's level (release, cancel, death,
 * disconnect, dimension change). Fades in over the first ticks.
 */
final class PowerLoopSound extends AbstractTickableSoundInstance {
	private static final int FADE_IN_TICKS = 4;

	private final LivingEntity entity;
	private final BooleanSupplier active;
	private final float baseVolume;
	private int age;

	PowerLoopSound(SoundEvent sound, LivingEntity entity, float volume, float pitch, BooleanSupplier active) {
		super(sound, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
		this.entity = entity;
		this.active = active;
		this.baseVolume = volume;
		this.looping = true;
		this.delay = 0;
		this.attenuation = SoundInstance.Attenuation.LINEAR;
		this.pitch = pitch;
		this.volume = 0.01f;
		follow();
	}

	@Override
	public void tick() {
		Minecraft mc = Minecraft.getInstance();

		if (entity.isRemoved() || !entity.isAlive() || entity.level() != mc.level || !active.getAsBoolean()) {
			stop();
			return;
		}

		age++;
		this.volume = Math.max(0.01f, baseVolume * Math.min(1f, age / (float) FADE_IN_TICKS) * ClientConfig.get().modSoundVolume);
		follow();
	}

	/** Ends the loop now. */
	void end() {
		stop();
	}

	private void follow() {
		this.x = entity.getX();
		this.y = entity.getY() + entity.getBbHeight() * 0.6;
		this.z = entity.getZ();
	}
}
