package com.oldrepublic.client.force;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.oldrepublic.saber.LightsaberItem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Client-side animation state of the Force powers other than Push, one per entity: which pose it
 * holds, since when, until when, and the looped sound that goes with it. Fed by effect events for
 * everyone and by the {@link ForceAbilityHooks} prediction for the own player. Drives the third
 * person {@link PowerPose}, the first-person hand offset and the crouch while a jump gathers.
 *
 * <p>Every state ends by itself: one-shot poses after their length, channels at a watchdog limit
 * past their longest server duration, predicted states when the server does not confirm them in
 * time, and all of them when the entity dies, is removed or the level changes.
 */
public final class PowerAnimations {
	/** The power poses. */
	public enum Kind {
		/** Pull: the free arm reaches out clawed, then snaps back to the chest. */
		PULL,
		/** Jump gather: crouched, both arms drawn back. */
		JUMP_GATHER,
		/** Jump launch: both arms swing up. */
		JUMP_LAUNCH,
		/** Soaring Leap glide: arms spread. */
		GLIDE,
		/** Heal: both hands drawn together at the chest, head slightly bowed. */
		HEAL,
		/** Lightning: the free arm (both without a saber) thrust forward, fingers spread. */
		LIGHTNING,
		/** Sense: two fingers of the free hand to the temple, the head tilted toward them. */
		SENSE,
		/** Lift: the free arm raised toward the target, palm up, with a slight tremble. */
		LIFT
	}

	/** Pull pose: reach for 3 ticks, snap back for 6. */
	static final int PULL_REACH_TICKS = 3;
	static final int PULL_TICKS = 9;
	static final int LAUNCH_TICKS = 8;
	/** Ticks a pose takes to blend in and out. */
	private static final float BLEND_TICKS = 3f;

	private static final class State {
		final Kind kind;
		final long start;
		/** Last tick of the state, or -1 while it lasts until stopped (bounded by {@link #watchdog}). */
		long until;
		/** A watchdog end for open states, so a lost stop event never leaves a pose or loop behind. */
		final long watchdog;
		/** Own-player prediction: dropped unless confirmed by this tick; -1 once confirmed. */
		long confirmBy = -1;
		@Nullable PowerLoopSound sound;

		State(Kind kind, long start, long until, long watchdog) {
			this.kind = kind;
			this.start = start;
			this.until = until;
			this.watchdog = watchdog;
		}
	}

	private static final Map<Integer, State> STATES = new HashMap<>();
	private static long ticks;

	/** The client animation clock (advances while the game is not paused). */
	static long ticks() {
		return ticks;
	}

	/**
	 * Starts a pose for {@code entityId}, replacing any other power pose of that entity.
	 *
	 * @param length   ticks for a one-shot pose, or -1 for one that lasts until {@link #stop}
	 * @param watchdog for an open pose, the most ticks it may last without a stop event
	 */
	static void start(int entityId, Kind kind, int length, int watchdog) {
		State previous = STATES.get(entityId);

		if (previous != null && previous.kind == kind && previous.until < 0 && length < 0) {
			// Already running (a prediction the server's event now repeats).
			previous.confirmBy = -1;
			return;
		}

		endSound(previous);
		STATES.put(entityId, new State(kind, ticks, length < 0 ? -1 : ticks + length, ticks + Math.max(length, watchdog)));
	}

	/** Own-player prediction: like {@link #start}, but dropped unless {@link #confirm}ed in time. */
	static void startPredicted(int entityId, Kind kind, int length, int watchdog) {
		start(entityId, kind, length, watchdog);
		STATES.get(entityId).confirmBy = ticks + PushAnimations.CONFIRM_TICKS;
	}

	/** The server's event for a predicted pose arrived. */
	static void confirm(int entityId, Kind kind) {
		State state = STATES.get(entityId);

		if (state != null && state.kind == kind) {
			state.confirmBy = -1;
		}
	}

	/** Ends the pose if it is {@code kind}. */
	static void stop(int entityId, Kind kind) {
		State state = STATES.get(entityId);

		if (state != null && state.kind == kind) {
			endSound(state);
			STATES.remove(entityId);
		}
	}

	/** Drops an unconfirmed prediction (the server refused the power). */
	static void stopPredicted(int entityId) {
		State state = STATES.get(entityId);

		if (state != null && state.confirmBy >= 0) {
			endSound(state);
			STATES.remove(entityId);
		}
	}

	static boolean isActive(int entityId, Kind kind) {
		State state = STATES.get(entityId);
		return state != null && state.kind == kind;
	}

	/** Ids of every entity holding {@code kind} right now. */
	static List<Integer> active(Kind kind) {
		List<Integer> ids = new ArrayList<>();

		for (Map.Entry<Integer, State> entry : STATES.entrySet()) {
			if (entry.getValue().kind == kind) {
				ids.add(entry.getKey());
			}
		}

		return ids;
	}

	/** Ticks since the pose started, or -1 if {@code kind} is not active. */
	static float age(int entityId, Kind kind, float partialTick) {
		State state = STATES.get(entityId);
		return state != null && state.kind == kind ? ticks - state.start + partialTick : -1f;
	}

	/** Attaches a loop to the entity's current pose; it stops when the pose ends. */
	static void loop(LivingEntity entity, Kind kind, SoundEvent sound, float volume, float pitch) {
		State state = STATES.get(entity.getId());

		if (state == null || state.kind != kind || state.sound != null && !state.sound.isStopped()) {
			return;
		}

		int id = entity.getId();
		state.sound = new PowerLoopSound(sound, entity, volume, pitch, () -> isActive(id, kind));
		Minecraft.getInstance().getSoundManager().play(state.sound);
	}

	/** True while a jump gathers: the renderer shows the entity crouched. */
	public static boolean isCrouching(int entityId) {
		return isActive(entityId, Kind.JUMP_GATHER);
	}

	/** The pose to render for {@code entity}, or null. */
	public static @Nullable PowerPose pose(LivingEntity entity, float partialTick) {
		if (STATES.isEmpty()) {
			return null;
		}

		State state = STATES.get(entity.getId());

		if (state == null) {
			return null;
		}

		float age = ticks - state.start + partialTick;
		float blendIn = Mth.clamp(age / BLEND_TICKS, 0f, 1f);
		float blendOut = state.until < 0 ? 1f : Mth.clamp((state.until - ticks - partialTick) / BLEND_TICKS, 0f, 1f);
		boolean left = ForceFxUtil.freeArmIsLeft(entity);

		return switch (state.kind) {
			case PULL -> {
				float phase = Mth.clamp((age - PULL_REACH_TICKS) / (PULL_TICKS - PULL_REACH_TICKS), 0f, 1f);
				yield new PowerPose(Kind.PULL, Math.min(blendIn * 1.5f, 1f) * blendOut, phase, left, false);
			}
			case LIGHTNING -> new PowerPose(Kind.LIGHTNING, blendIn, 0f, left, !holdsSaber(entity));
			case SENSE -> new PowerPose(Kind.SENSE, blendIn * blendOut, 0f, left, false);
			// Phase carries the pose age, which drives the tremble.
			case LIFT -> new PowerPose(Kind.LIFT, blendIn * blendOut, age, left, false);
			case JUMP_LAUNCH -> new PowerPose(Kind.JUMP_LAUNCH, Math.min(blendIn * 2f, 1f) * blendOut, 0f, left, true);
			default -> new PowerPose(state.kind, blendIn * blendOut, 0f, left, true);
		};
	}

	/** Whether a lightsaber is in either hand. */
	static boolean holdsSaber(LivingEntity entity) {
		return entity.getMainHandItem().getItem() instanceof LightsaberItem || entity.getOffhandItem().getItem() instanceof LightsaberItem;
	}

	/**
	 * How far a lifted creature flails its arms and legs (0 to 1), for the humanoid model; 0 when it is
	 * not lifted. Read for every living entity's render state.
	 */
	public static float flail(LivingEntity entity, float partialTick) {
		return LiftFx.flail(entity.getId(), partialTick);
	}

	/** The Lift tremble: a small, uneven shake of the raised hand, from the pose age. */
	public static float tremble(float age) {
		return Mth.sin(age * 2.3f) * 0.6f + Mth.sin(age * 3.7f + 1.1f) * 0.4f;
	}

	/**
	 * First person: the hand reaches and pulls in, rises for a launch, draws in to the chest, thrusts
	 * forward, rises to the temple (Sense) or lifts, trembling (Lift).
	 */
	public static void applyFirstPerson(PoseStack poseStack, LivingEntity player, float partialTick) {
		PowerPose pose = pose(player, partialTick);

		if (pose == null) {
			return;
		}

		float a = pose.amount();

		switch (pose.kind()) {
			case PULL -> poseStack.translate(0f, 0.02f * a, (-0.22f + 0.36f * pose.phase()) * a);
			case JUMP_GATHER -> poseStack.translate(0f, -0.10f * a, 0.10f * a);
			case JUMP_LAUNCH -> poseStack.translate(0f, 0.12f * a, -0.04f * a);
			case GLIDE -> poseStack.translate(0f, -0.06f * a, 0.04f * a);
			case HEAL -> poseStack.translate(0f, -0.12f * a, 0.14f * a);
			case LIGHTNING -> poseStack.translate(0f, 0.03f * a, -0.18f * a);
			case SENSE -> poseStack.translate(0f, 0.16f * a, 0.12f * a);
			case LIFT -> poseStack.translate(0.004f * tremble(pose.phase() + 0.7f) * a, (0.12f + 0.006f * tremble(pose.phase())) * a, -0.10f * a);
		}
	}

	static void tick(Minecraft mc) {
		if (mc.isPaused()) {
			return;
		}

		ticks++;
		ClientLevel level = mc.level;

		if (level == null) {
			clear();
			return;
		}

		Iterator<Map.Entry<Integer, State>> it = STATES.entrySet().iterator();

		while (it.hasNext()) {
			Map.Entry<Integer, State> entry = it.next();
			State state = entry.getValue();
			Entity entity = level.getEntity(entry.getKey());
			boolean gone = !(entity instanceof LivingEntity) || entity.isRemoved() || !entity.isAlive();
			boolean expired = state.until >= 0 ? ticks > state.until : ticks > state.watchdog;
			boolean unconfirmed = state.confirmBy >= 0 && ticks > state.confirmBy;

			if (gone || expired || unconfirmed) {
				endSound(state);
				it.remove();
			}
		}
	}

	private static void endSound(@Nullable State state) {
		if (state != null && state.sound != null) {
			state.sound.end();
			state.sound = null;
		}
	}

	/** Forgets every entity (level change, disconnect); stops all loops. */
	static void clear() {
		for (State state : STATES.values()) {
			endSound(state);
		}

		STATES.clear();
	}

	private PowerAnimations() {
	}
}
