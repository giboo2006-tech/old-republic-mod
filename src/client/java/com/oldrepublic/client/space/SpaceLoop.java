package com.oldrepublic.client.space;

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
 * One of space's looping sounds (spec MILESTONE_5.md 15.4): an engine or the gunship's hum bound to
 * its entity, or a cockpit tone heard by this player alone (the lock tone, the missile warning, the
 * scan). It fades in, follows its entity, takes a new loudness and pitch smoothly, fades out once
 * its condition fails, and stops at once when its entity is removed or the client's level changes
 * (a dimension change, a disconnect).
 */
final class SpaceLoop extends AbstractTickableSoundInstance {
	private final @Nullable Object level;
	private final @Nullable Entity follow;
	private final BooleanSupplier keep;
	private float target;
	private float targetPitch;
	private float faded = 0.001f;
	private final float fadeStep;
	private boolean released;

	private SpaceLoop(SoundEvent event, SoundSource source, @Nullable Entity follow, float volume, float pitch, int fadeTicks, BooleanSupplier keep) {
		super(event, source, SoundInstance.createUnseededRandom());
		this.level = Minecraft.getInstance().level;
		this.follow = follow;
		this.keep = keep;
		this.target = volume;
		this.targetPitch = pitch;
		this.pitch = pitch;
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
			this.y = follow.getY() + follow.getBbHeight() / 2;
			this.z = follow.getZ();
		}
	}

	/**
	 * Starts a loop that follows {@code follow} (null: heard by this player alone) while {@code keep}
	 * holds. A loudness above 1 carries farther (vanilla's linear attenuation), never louder.
	 */
	static SpaceLoop play(SoundEvent event, SoundSource source, @Nullable Entity follow, float volume, float pitch, int fadeTicks, BooleanSupplier keep) {
		SpaceLoop loop = new SpaceLoop(event, source, follow, volume, pitch, fadeTicks, keep);
		Minecraft.getInstance().getSoundManager().play(loop);
		return loop;
	}

	/** The loudness and pitch it moves toward. */
	void setTarget(float volume, float pitch) {
		this.target = volume;
		this.targetPitch = pitch;
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
			this.y = follow.getY() + follow.getBbHeight() / 2;
			this.z = follow.getZ();
		}

		float goal = released ? 0f : target * ClientConfig.get().modSoundVolume;

		if (faded < goal) {
			faded = Math.min(goal, faded + fadeStep);
		} else if (faded > goal) {
			faded = Math.max(goal, faded - fadeStep);
		}

		volume = Math.max(0.001f, faded);
		pitch += (targetPitch - pitch) * 0.25f;

		if (released && faded <= 0.002f) {
			stop();
		}
	}
}
