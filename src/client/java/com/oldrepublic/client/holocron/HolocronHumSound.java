package com.oldrepublic.client.holocron;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.config.ClientConfig;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * The {@code holocron.hum} loop of a projected gatekeeper (spec 4.4, 16): a low crystal hum at the
 * risen holocron that fades in as the figure forms and out when it folds. Its own watchdog stops it
 * when the figure folds, the dialogue closes, the player dies, disconnects or changes level, walks
 * more than 16 blocks away, or after {@link RisingHolocron#MAX_TICKS}, so it never outlives the
 * moment even if an event is lost. At most one plays.
 */
final class HolocronHumSound extends AbstractTickableSoundInstance {
	private static final float VOLUME = 0.55f;
	private static final float FADE_IN = 0.04f;
	private static final float FADE_OUT = 0.06f;
	private static final double MAX_DISTANCE = 16.0;

	private static @Nullable HolocronHumSound current;

	private final Object level;
	private final float targetVolume;
	private int age;
	private boolean released;

	private HolocronHumSound(Vec3 pos) {
		super(ModSounds.HOLOCRON_HUM, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
		this.level = Minecraft.getInstance().level;
		this.targetVolume = VOLUME * ClientConfig.get().modSoundVolume;
		this.looping = true;
		this.delay = 0;
		this.volume = 0.01f;
		this.x = pos.x;
		this.y = pos.y;
		this.z = pos.z;
	}

	/** Starts the hum at {@code pos}, replacing (and fading) any hum already playing. */
	static void play(Vec3 pos) {
		fadeOut();
		HolocronHumSound sound = new HolocronHumSound(pos);
		current = sound;
		Minecraft.getInstance().getSoundManager().play(sound);
	}

	/** Fades the hum out. */
	static void fadeOut() {
		if (current != null) {
			current.released = true;
			current = null;
		}
	}

	/** Stops the hum at once (disconnect). */
	static void silence() {
		HolocronHumSound sound = current;
		current = null;

		if (sound != null) {
			sound.released = true;
			Minecraft.getInstance().getSoundManager().stop(sound);
		}
	}

	@Override
	public void tick() {
		Minecraft mc = Minecraft.getInstance();
		age++;

		if (mc.level != level || mc.player == null || mc.player.isDeadOrDying()) {
			finish();
			return;
		}

		if (age > RisingHolocron.MAX_TICKS || mc.player.position().distanceToSqr(x, y, z) > MAX_DISTANCE * MAX_DISTANCE) {
			released = true;
		}

		if (released) {
			volume -= FADE_OUT * Math.max(targetVolume, 0.1f);

			if (volume <= 0.01f) {
				finish();
			}
		} else if (volume < targetVolume) {
			volume = Math.min(targetVolume, volume + FADE_IN * targetVolume);
		}
	}

	private void finish() {
		if (current == this) {
			current = null;
		}

		released = true;
		stop();
	}
}
