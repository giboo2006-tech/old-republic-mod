package com.oldrepublic.client.equipment;

import java.util.function.Predicate;

import com.oldrepublic.client.config.ClientConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * A sound tied to what an entity holds: the rifle's charge whine, the electrostaff's rising crackle.
 * It follows the entity and stops by itself when the entity dies or is removed, the level changes,
 * the item it belongs to leaves the hand ({@code holding} fails), after {@code maxTicks}, or when
 * {@link #cut} is called (the charge was spent, dropped or released). So it can never outlive its
 * cause.
 */
final class HeldSound extends AbstractTickableSoundInstance {
	private final LivingEntity holder;
	private final Predicate<LivingEntity> holding;
	private final Object level;
	private final int maxTicks;
	private int age;

	private HeldSound(SoundEvent event, float volume, float pitch, LivingEntity holder, Predicate<LivingEntity> holding, int maxTicks) {
		super(event, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
		this.holder = holder;
		this.holding = holding;
		this.level = holder.level();
		this.maxTicks = maxTicks;
		this.volume = volume * ClientConfig.get().modSoundVolume;
		this.pitch = pitch;
		this.looping = false;
		this.x = holder.getX();
		this.y = holder.getEyeY() - 0.3;
		this.z = holder.getZ();
	}

	/** Plays {@code event} on {@code holder} while {@code holding} holds, at most {@code maxTicks}. */
	static HeldSound play(SoundEvent event, float volume, float pitch, LivingEntity holder, Predicate<LivingEntity> holding, int maxTicks) {
		HeldSound sound = new HeldSound(event, volume, pitch, holder, holding, maxTicks);
		Minecraft.getInstance().getSoundManager().play(sound);
		return sound;
	}

	/** Stops it now. */
	void cut() {
		stop();
	}

	@Override
	public void tick() {
		age++;

		if (Minecraft.getInstance().level != level || holder.isRemoved() || !holder.isAlive() || age > maxTicks || !holding.test(holder)) {
			stop();
			return;
		}

		x = holder.getX();
		y = holder.getEyeY() - 0.3;
		z = holder.getZ();
	}
}
