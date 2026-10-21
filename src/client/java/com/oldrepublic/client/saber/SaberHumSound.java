package com.oldrepublic.client.saber;

import com.oldrepublic.client.config.ClientConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * The hum of one lit blade in one hand of an entity (a player or a saber NPC), in its emitter's
 * profile (MILESTONE_5.md 10.5). Follows the entity and stops itself as soon as that hand no longer
 * holds a fully lit saber with this hum, or the entity dies, is removed or is not in the client's
 * level: this covers retract, throw, drop, item switch, death, disconnect and dimension change. The
 * off hand's hum is a little quieter, so two blades do not double the volume.
 *
 * <p>Like a film saber's, the hum rises with the blade's speed (docs/AUDIO_BRIEF.md, "Lightsaber"):
 * how fast the entity turns its head and body each tick, plus a swing of this hand's arm (a bell
 * over the swing), sets a motion level from 0 (still) to 1 that eases in quickly and out slowly,
 * never by more than {@link #MAX_STEP} a tick, and lifts the pitch by up to {@link #PITCH_SPAN} and
 * the volume by up to {@link #VOLUME_SPAN}. Turns under {@link #TURN_DEAD} degrees a tick count as
 * still, so the hum is steady while the blade is held still or the view drifts.
 */
final class SaberHumSound extends AbstractTickableSoundInstance {
	private static final float VOLUME = 0.5f;
	private static final float OFF_HAND_VOLUME = 0.375f;
	/** A head or body turn (degrees a tick) under which the blade counts as still, and at which it is at full speed. */
	private static final float TURN_DEAD = 2.5f;
	private static final float TURN_FULL = 35f;
	/** The share of a swing of the other arm that reaches this hand's hum (the body turns both blades a little). */
	private static final float OTHER_ARM_SWING = 0.3f;
	/** How far the motion level eases toward its target each tick, rising and falling, and its largest step. */
	private static final float RISE = 0.5f;
	private static final float FALL = 0.2f;
	private static final float MAX_STEP = 0.2f;
	/** The pitch and volume lift at full motion, as a fraction of the resting pitch and volume. */
	static final float PITCH_SPAN = 0.08f;
	private static final float VOLUME_SPAN = 0.3f;
	private final LivingEntity entity;
	private final InteractionHand hand;
	private final SoundEvent hum;
	/** The head yaw, head pitch and body yaw seen last tick, in degrees. */
	private float lastHeadYaw;
	private float lastXRot;
	private float lastBodyYaw;
	/** The eased motion level, 0 (still) to 1 (full speed). */
	private float motion;

	SaberHumSound(LivingEntity entity, InteractionHand hand, SoundEvent hum) {
		super(hum, entity instanceof Player ? SoundSource.PLAYERS : SoundSource.HOSTILE, SoundInstance.createUnseededRandom());
		this.entity = entity;
		this.hand = hand;
		this.hum = hum;
		this.looping = true;
		this.delay = 0;
		this.attenuation = SoundInstance.Attenuation.LINEAR;
		this.lastHeadYaw = entity.getYHeadRot();
		this.lastXRot = entity.getXRot();
		this.lastBodyYaw = entity.yBodyRot;
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

		move();
		this.pitch = 1f + PITCH_SPAN * motion;
		this.volume = volume();
		follow();
	}

	/** The eased motion level, 0 (still) to 1 (full speed); the swing sound follows it too. */
	float motion() {
		return motion;
	}

	/** Eases the motion level toward this tick's blade speed: the faster turn of head and body, plus the swing. */
	private void move() {
		float headYaw = entity.getYHeadRot();
		float xRot = entity.getXRot();
		float bodyYaw = entity.yBodyRot;
		float head = (float) Math.hypot(Mth.wrapDegrees(headYaw - lastHeadYaw), xRot - lastXRot);
		float body = Math.abs(Mth.wrapDegrees(bodyYaw - lastBodyYaw));
		lastHeadYaw = headYaw;
		lastXRot = xRot;
		lastBodyYaw = bodyYaw;

		float turn = Mth.clamp((Math.max(head, body) - TURN_DEAD) / (TURN_FULL - TURN_DEAD), 0f, 1f);
		float target = Math.min(1f, turn + swing());
		float step = (target - motion) * (target > motion ? RISE : FALL);
		motion = Mth.clamp(motion + Mth.clamp(step, -MAX_STEP, MAX_STEP), 0f, 1f);
	}

	/** This hand's share of the entity's swing: a bell over the swing, highest halfway through. */
	private float swing() {
		if (!entity.swinging) {
			return 0f;
		}

		InteractionHand arm = entity.swingingArm == null ? InteractionHand.MAIN_HAND : entity.swingingArm;
		return Mth.sin(entity.getAttackAnim(1f) * Mth.PI) * (arm == hand ? 1f : OTHER_ARM_SWING);
	}

	private float volume() {
		float base = hand == InteractionHand.MAIN_HAND ? VOLUME : OFF_HAND_VOLUME;
		return base * (1f + VOLUME_SPAN * motion) * ClientConfig.get().modSoundVolume;
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
