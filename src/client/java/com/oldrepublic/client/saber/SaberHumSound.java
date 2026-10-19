package com.oldrepublic.client.saber;

import com.oldrepublic.client.config.ClientConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * The hum of one lit blade in one hand of an entity (a player or a saber NPC), in its emitter's
 * profile (MILESTONE_5.md 10.5). Follows the entity and stops itself as soon as that hand no longer
 * holds a fully lit saber with this hum, or the entity dies, is removed or is not in the client's
 * level: this covers retract, throw, drop, item switch, death, disconnect and dimension change. The
 * off hand's hum is a little quieter, so two blades do not double the volume.
 */
final class SaberHumSound extends AbstractTickableSoundInstance {
	private static final float VOLUME = 0.6f;
	private static final float OFF_HAND_VOLUME = 0.45f;
	private final LivingEntity entity;
	private final InteractionHand hand;
	private final SoundEvent hum;

	SaberHumSound(LivingEntity entity, InteractionHand hand, SoundEvent hum) {
		super(hum, entity instanceof Player ? SoundSource.PLAYERS : SoundSource.HOSTILE, SoundInstance.createUnseededRandom());
		this.entity = entity;
		this.hand = hand;
		this.hum = hum;
		this.looping = true;
		this.delay = 0;
		this.attenuation = SoundInstance.Attenuation.LINEAR;
		this.volume = volume();
		follow();
	}

	@Override
	public void tick() {
		Minecraft mc = Minecraft.getInstance();

		if (entity.isRemoved() || !entity.isAlive() || entity.level() != mc.level || !SaberAudio.humming(entity, hand, hum)) {
			stop();
			return;
		}

		this.volume = volume();
		follow();
	}

	private float volume() {
		return (hand == InteractionHand.MAIN_HAND ? VOLUME : OFF_HAND_VOLUME) * ClientConfig.get().modSoundVolume;
	}

	/** Stops the hum now (client reset). */
	void end() {
		stop();
	}

	private void follow() {
		this.x = entity.getX();
		this.y = entity.getY() + entity.getBbHeight() * 0.55;
		this.z = entity.getZ();
	}
}
