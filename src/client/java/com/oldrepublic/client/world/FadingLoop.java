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
 * changes, so a loop can never outlive the level it belongs to. Public since M3 so the site
 * ambience ({@code client.world.site}) plays its beds through it.
 */
public final class FadingLoop extends AbstractTickableSoundInstance {
	private final Object level;
	private final float baseVolume;
	private final float fadeStep;
	private float duck = 1f;
	private float dip = 1f;
	/** The faded volume the loop is at, before {@link #dip}. */
	private float faded = 0.001f;
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

	/** Starts a loop at {@code pos} (null: non-positional) fading in to {@code volume} over {@code fadeTicks}. */
	public static FadingLoop play(SoundEvent event, @Nullable Vec3 pos, float volume, int fadeTicks) {
		FadingLoop loop = new FadingLoop(event, pos, volume, fadeTicks);
		Minecraft.getInstance().getSoundManager().play(loop);
		return loop;
	}

	/** Moves a positional loop. */
	public void moveTo(Vec3 pos) {
		this.x = pos.x;
		this.y = pos.y;
		this.z = pos.z;
	}

	/** The loop's position (zero for a non-positional loop). */
	public Vec3 position() {
		return new Vec3(x, y, z);
	}

	/** Volume multiplier (e.g. 0.3 while a trial runs nearby). */
	public void duck(float factor) {
		this.duck = factor;
	}

	/**
	 * Instant volume multiplier on top of the faded level, applied the same tick (the Force Sense
	 * ambience dip, which must follow its 20-tick curve and so cannot wait for the fade).
	 */
	public void dip(float factor) {
		this.dip = factor;
	}

	/** Fades the loop out; it stops once silent. */
	public void release() {
		released = true;
	}

	/** Whether {@link #release} or {@link #kill} was called. */
	public boolean isReleased() {
		return released;
	}

	/** Stops at once (disconnect). */
	public void kill() {
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

		if (faded < target) {
			faded = Math.min(target, faded + step);
		} else if (faded > target) {
			faded = Math.max(target, faded - step);
		}

		volume = Math.max(0.001f, faded * dip);

		if (released && faded <= 0.002f) {
			stop();
		}
	}
}
