package com.oldrepublic.client.force.speed;

import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;

import com.oldrepublic.client.config.ClientConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

/**
 * A looped Milestone 4 power sound that follows its entity and stops itself as soon as
 * {@code active} turns false or the entity dies, is removed or leaves the client's level: the power
 * ending on any path, death, disconnect and dimension change (spec 13.4). {@code strain} (0 to 1)
 * lowers the pitch and roughens the volume, for the Barrier hum as Force Energy runs low.
 */
public final class PowerLoop extends AbstractTickableSoundInstance {
	private static final int FADE_IN_TICKS = 4;

	private final Entity entity;
	private final BooleanSupplier active;
	private final DoubleSupplier strain;
	private final float baseVolume;
	private final float basePitch;
	private int age;

	private PowerLoop(SoundEvent sound, SoundSource source, Entity entity, float volume, float pitch, BooleanSupplier active, DoubleSupplier strain) {
		super(sound, source, SoundInstance.createUnseededRandom());
		this.entity = entity;
		this.active = active;
		this.strain = strain;
		this.baseVolume = volume;
		this.basePitch = pitch;
		this.looping = true;
		this.delay = 0;
		this.attenuation = SoundInstance.Attenuation.LINEAR;
		this.pitch = pitch;
		this.volume = 0.01f;
		follow();
	}

	/** Starts {@code sound} on {@code entity} until {@code active} is false (a player's power: {@code SoundSource.PLAYERS}). */
	public static PowerLoop start(SoundEvent sound, Entity entity, float volume, float pitch, BooleanSupplier active, DoubleSupplier strain) {
		return start(sound, SoundSource.PLAYERS, entity, volume, pitch, active, strain);
	}

	/** Starts {@code sound} in {@code source} on {@code entity} until {@code active} is false (a foe's loop: {@code HOSTILE}). */
	public static PowerLoop start(SoundEvent sound, SoundSource source, Entity entity, float volume, float pitch, BooleanSupplier active, DoubleSupplier strain) {
		PowerLoop loop = new PowerLoop(sound, source, entity, volume, pitch, active, strain);
		Minecraft.getInstance().getSoundManager().play(loop);
		return loop;
	}

	@Override
	public void tick() {
		Minecraft mc = Minecraft.getInstance();

		if (entity.isRemoved() || !entity.isAlive() || entity.level() != mc.level || !active.getAsBoolean()) {
			stop();
			return;
		}

		age++;
		float s = (float) Math.max(0, Math.min(1, strain.getAsDouble()));
		// Strain: the hum sags and wavers.
		float waver = s > 0f ? 1f - s * 0.25f * (0.5f + 0.5f * (float) Math.sin(age * 0.9)) : 1f;
		this.pitch = basePitch * (1f - 0.18f * s);
		this.volume = Math.max(0.01f, baseVolume * waver * Math.min(1f, age / (float) FADE_IN_TICKS) * ClientConfig.get().modSoundVolume);
		follow();
	}

	/** Ends the loop now. */
	public void end() {
		stop();
	}

	private void follow() {
		this.x = entity.getX();
		this.y = entity.getY() + entity.getBbHeight() * 0.6;
		this.z = entity.getZ();
	}
}
