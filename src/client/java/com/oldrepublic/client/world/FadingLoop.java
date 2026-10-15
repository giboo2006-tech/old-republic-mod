package com.oldrepublic.client.world;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.config.ClientConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * A looping world sound that fades in, can be moved and ducked, and fades out when released.
 * Non-positional when created without a position. It stops by itself when the client level
 * changes, so a loop can never outlive the level it belongs to.
 */
final class FadingLoop extends AbstractTickableSoundInstance {
	private final Object level;
	private final float baseVolume;
	private final float fadeStep;
	private float duck = 1f;
	private boolean released;

	private FadingLoop(SoundEvent event, @Nullable Vec3 pos, float volume, int fadeTicks) {
		super(event, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
		this.level = Minecraft.getInstance().level;
		this.baseVolume = volume;
		this.fadeStep = 1f / Math.max(1, fadeTicks);
		this.looping = true;
		this.delay = 0;
		this.volume = 0.001f;

		if (pos == null) {
			this.relative = true;
			this.attenuation = SoundInstance.Attenuation.NONE;
			this.x = 0;
			this.y = 0;
			this.z = 0;
		} else {
			this.attenuation = SoundInstance.Attenuation.LINEAR;
			this.x = pos.x;
			this.y = pos.y;
			this.z = pos.z;
		}
	}

	static FadingLoop play(SoundEvent event, @Nullable Vec3 pos, float volume, int fadeTicks) {
		FadingLoop loop = new FadingLoop(event, pos, volume, fadeTicks);
		Minecraft.getInstance().getSoundManager().play(loop);
		return loop;
	}

	void moveTo(Vec3 pos) {
		this.x = pos.x;
		this.y = pos.y;
		this.z = pos.z;
	}

	Vec3 position() {
		return new Vec3(x, y, z);
	}

	/** Volume multiplier (e.g. 0.3 while a trial runs nearby). */
	void duck(float factor) {
		this.duck = factor;
	}

	void release() {
		released = true;
	}

	boolean isReleased() {
		return released;
	}

	/** Stops at once (disconnect). */
	void kill() {
		released = true;
		stop();
	}

	@Override
	public void tick() {
		if (Minecraft.getInstance().level != level) {
			stop();
			return;
		}

		float target = released ? 0f : baseVolume * duck * ClientConfig.get().modSoundVolume;
		float step = fadeStep * Math.max(baseVolume, 0.05f);

		if (volume < target) {
			volume = Math.min(target, volume + step);
		} else if (volume > target) {
			volume = Math.max(target, volume - step);
		}

		if (released && volume <= 0.002f) {
			stop();
		}
	}
}
