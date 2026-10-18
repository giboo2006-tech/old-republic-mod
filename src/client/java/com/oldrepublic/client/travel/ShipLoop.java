package com.oldrepublic.client.travel;

import java.util.function.BooleanSupplier;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.config.ClientConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

/**
 * A ship's looping sound (spec MILESTONE_4.md 13.4): the engines' idle hum and the damaged crackle
 * follow their ship; the hyperspace hum is heard by the rider alone. It fades in, fades out once
 * its condition fails (the ship leaves the state, the rider leaves hyperspace) and stops at once
 * when its ship is gone or the client's level changes.
 */
final class ShipLoop extends AbstractTickableSoundInstance {
	private final Object level;
	private final @Nullable Entity follow;
	private final BooleanSupplier keep;
	private float target;
	private float faded = 0.001f;
	private final float fadeStep;
	private boolean released;

	private ShipLoop(SoundEvent event, @Nullable Entity follow, float volume, int fadeTicks, BooleanSupplier keep) {
		super(event, SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
		this.level = Minecraft.getInstance().level;
		this.follow = follow;
		this.keep = keep;
		this.target = volume;
		this.fadeStep = Math.max(volume, 0.05f) / Math.max(1, fadeTicks);
		this.looping = true;
		this.delay = 0;
		this.volume = 0.001f;

		if (follow == null) {
			this.relative = true;
			this.attenuation = SoundInstance.Attenuation.NONE;
			this.x = 0;
			this.y = 0;
			this.z = 0;
		} else {
			this.attenuation = SoundInstance.Attenuation.LINEAR;
			this.x = follow.getX();
			this.y = follow.getY() + 1.5;
			this.z = follow.getZ();
		}
	}

	/** Starts a loop that follows {@code follow} (null: heard by this player alone) while {@code keep} holds. */
	static ShipLoop play(SoundEvent event, @Nullable Entity follow, float volume, int fadeTicks, BooleanSupplier keep) {
		ShipLoop loop = new ShipLoop(event, follow, volume, fadeTicks, keep);
		Minecraft.getInstance().getSoundManager().play(loop);
		return loop;
	}

	/** Changes the loudness it fades toward (the engines working harder in flight). */
	void setTarget(float volume) {
		this.target = volume;
	}

	/** Fades out and stops. */
	void release() {
		released = true;
	}

	/** Whether it has stopped or is fading out for good. */
	boolean finished() {
		return released || isStopped();
	}

	@Override
	public void tick() {
		if (Minecraft.getInstance().level != level || follow != null && follow.isRemoved()) {
			stop();
			return;
		}

		if (!released && !keep.getAsBoolean()) {
			released = true;
		}

		if (follow != null) {
			this.x = follow.getX();
			this.y = follow.getY() + 1.5;
			this.z = follow.getZ();
		}

		float goal = released ? 0f : target * ClientConfig.get().modSoundVolume;

		if (faded < goal) {
			faded = Math.min(goal, faded + fadeStep);
		} else if (faded > goal) {
			faded = Math.max(goal, faded - fadeStep);
		}

		volume = Math.max(0.001f, faded);

		if (released && faded <= 0.002f) {
			stop();
		}
	}
}
