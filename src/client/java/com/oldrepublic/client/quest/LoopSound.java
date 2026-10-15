package com.oldrepublic.client.quest;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.config.ClientConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

/**
 * A looping sound that fades in, follows an entity (or plays non-positionally when there is none)
 * and fades out when released. It also stops by itself when the entity is removed or the level
 * changes, so a loop can never outlive its cause.
 */
public final class LoopSound extends AbstractTickableSoundInstance {
	private static final float FADE_STEP = 0.08f;

	private final @Nullable Entity follow;
	private final Object level;
	private final float targetVolume;
	private boolean released;

	private LoopSound(SoundEvent event, float volume, @Nullable Entity follow) {
		super(event, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
		this.follow = follow;
		this.level = Minecraft.getInstance().level;
		this.targetVolume = volume * ClientConfig.get().modSoundVolume;
		this.looping = true;
		this.delay = 0;
		this.volume = 0.01f;

		if (follow == null) {
			this.relative = true;
			this.attenuation = SoundInstance.Attenuation.NONE;
			this.x = 0;
			this.y = 0;
			this.z = 0;
		} else {
			this.x = follow.getX();
			this.y = follow.getY();
			this.z = follow.getZ();
		}
	}

	/** Starts a loop that follows {@code follow}, or a non-positional one when it is null. */
	public static LoopSound play(SoundEvent event, float volume, @Nullable Entity follow) {
		LoopSound sound = new LoopSound(event, volume, follow);
		Minecraft.getInstance().getSoundManager().play(sound);
		return sound;
	}

	/** Fades the loop out and stops it. */
	public void release() {
		released = true;
	}

	/** Stops the loop at once (disconnect, level change). */
	public void kill() {
		released = true;
		stop();
	}

	@Override
	public void tick() {
		Minecraft mc = Minecraft.getInstance();

		if (mc.level != level || (follow != null && follow.isRemoved())) {
			stop();
			return;
		}

		if (released) {
			volume -= FADE_STEP * Math.max(targetVolume, 0.1f);

			if (volume <= 0.01f) {
				stop();
				return;
			}
		} else if (volume < targetVolume) {
			volume = Math.min(targetVolume, volume + FADE_STEP * targetVolume);
		}

		if (follow != null) {
			x = follow.getX();
			y = follow.getY();
			z = follow.getZ();
		}
	}
}
