package com.oldrepublic.client.boss.kaast;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * One streamed Kaast fight loop in {@link SoundSource#MUSIC} (the vanilla music slider sets its
 * level), non-positional like vanilla music. It fades in from silence and out to silence over
 * {@link KaastMusic#FADE_TICKS} ticks, so two loops crossfade, and stops itself once faded out.
 */
final class KaastMusicSound extends AbstractTickableSoundInstance {
	private final SoundEvent track;
	/** 0 (silent) to 1 (full). */
	private float level;
	private float target = 1f;

	KaastMusicSound(SoundEvent track) {
		super(track, SoundSource.MUSIC, SoundInstance.createUnseededRandom());
		this.track = track;
		this.looping = true;
		this.delay = 0;
		this.relative = true;
		this.attenuation = SoundInstance.Attenuation.NONE;
		this.x = 0;
		this.y = 0;
		this.z = 0;
		this.volume = 0f;
	}

	/** It starts silent and fades in, so the engine must not skip it for its zero volume. */
	@Override
	public boolean canStartSilent() {
		return true;
	}

	@Override
	public void tick() {
		float step = 1f / KaastMusic.FADE_TICKS;
		level = target > level ? Math.min(target, level + step) : Math.max(target, level - step);
		this.volume = level;

		if (target <= 0f && level <= 0f) {
			stop();
		}
	}

	SoundEvent track() {
		return track;
	}

	void fadeIn() {
		target = 1f;
	}

	void fadeOut() {
		target = 0f;
	}

	/** Stops at once (disconnect, level change). */
	void end() {
		stop();
	}
}
