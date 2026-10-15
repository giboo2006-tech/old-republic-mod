package com.oldrepublic.client.force;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;

/**
 * Client-side push animation state for every entity: charging since when, released when. Drives
 * the third-person arm pose, the first-person hand offset, the charge particles and the charge loop.
 * Fed by local input (prediction for the own player) and by effect events (everyone).
 */
public final class PushAnimations {
	/** Visual full charge (1 s); the server decides the real charge. */
	static final int FULL_CHARGE_TICKS = 20;
	static final int THRUST_TICKS = 8;

	private static final class State {
		long chargeStart = -1;
		long releaseAt = -1;
		long awaitUntil = -1;
		@Nullable ChargeSoundInstance sound;
	}

	private static final Map<Integer, State> STATES = new HashMap<>();
	private static long ticks;

	static long ticks() {
		return ticks;
	}

	static void startCharge(int entityId) {
		State state = STATES.computeIfAbsent(entityId, id -> new State());

		if (state.chargeStart < 0) {
			state.chargeStart = ticks;
		}

		state.releaseAt = -1;
		state.awaitUntil = -1;
	}

	static void stopCharge(int entityId) {
		State state = STATES.get(entityId);

		if (state != null) {
			state.chargeStart = -1;
			state.awaitUntil = -1;
		}
	}

	/** Keeps the drawn-back pose for up to {@code timeout} ticks while the server answers a release. */
	static void awaitRelease(int entityId, int timeout) {
		State state = STATES.get(entityId);

		if (state != null && state.chargeStart >= 0) {
			state.awaitUntil = ticks + timeout;
		}
	}

	static void release(int entityId) {
		State state = STATES.computeIfAbsent(entityId, id -> new State());
		state.chargeStart = -1;
		state.awaitUntil = -1;
		state.releaseAt = ticks;
	}

	public static boolean isCharging(int entityId) {
		State state = STATES.get(entityId);
		return state != null && state.chargeStart >= 0;
	}

	/** Visual charge 0 to 1. */
	public static float charge(int entityId, float partialTick) {
		State state = STATES.get(entityId);

		if (state == null || state.chargeStart < 0) {
			return 0f;
		}

		return Mth.clamp((ticks - state.chargeStart + partialTick) / FULL_CHARGE_TICKS, 0f, 1f);
	}

	/** Thrust 0 to 1: snaps forward in the first fifth of {@link #THRUST_TICKS}, then eases back. */
	public static float thrust(int entityId, float partialTick) {
		State state = STATES.get(entityId);

		if (state == null || state.releaseAt < 0) {
			return 0f;
		}

		float t = (ticks - state.releaseAt + partialTick) / THRUST_TICKS;

		if (t < 0f || t >= 1f) {
			return 0f;
		}

		return t < 0.2f ? t / 0.2f : 1f - (t - 0.2f) / 0.8f;
	}

	/** The pose to render for {@code entity}, or null when it is not pushing. */
	public static @Nullable PushPose pose(LivingEntity entity, float partialTick) {
		if (STATES.isEmpty() || !STATES.containsKey(entity.getId())) {
			return null;
		}

		float charge = charge(entity.getId(), partialTick);
		float draw = isCharging(entity.getId()) ? Math.max(0.35f, 1f - (1f - charge) * (1f - charge)) : 0f;
		float thrust = thrust(entity.getId(), partialTick);

		if (draw <= 0f && thrust <= 0f) {
			return null;
		}

		return new PushPose(draw, thrust, pushArmIsLeft(entity));
	}

	/** The free hand pushes: the main arm when the main hand is empty, otherwise the off arm. */
	public static boolean pushArmIsLeft(LivingEntity entity) {
		HumanoidArm main = entity.getMainArm();
		HumanoidArm arm = entity.getMainHandItem().isEmpty() ? main : main.getOpposite();
		return arm == HumanoidArm.LEFT;
	}

	/** First-person: hand pulled back while charging, jabbed forward on release. */
	public static void applyFirstPerson(PoseStack poseStack, LivingEntity player, float partialTick) {
		if (!STATES.containsKey(player.getId())) {
			return;
		}

		float charge = charge(player.getId(), partialTick);
		float draw = isCharging(player.getId()) ? Math.max(0.3f, charge) : 0f;
		float thrust = thrust(player.getId(), partialTick);
		poseStack.translate(0f, -0.07f * draw + 0.02f * thrust, 0.12f * draw - 0.24f * thrust);
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

			if (!(entity instanceof LivingEntity living) || entity.isRemoved() || !entity.isAlive()) {
				endSound(state);
				it.remove();
				continue;
			}

			if (state.awaitUntil >= 0 && ticks > state.awaitUntil) {
				state.chargeStart = -1;
				state.awaitUntil = -1;
			}

			if (state.chargeStart >= 0) {
				PushFx.chargeTick(level, living, charge(entry.getKey(), 0f), ticks - state.chargeStart);

				if (state.sound == null || state.sound.isStopped()) {
					state.sound = new ChargeSoundInstance(living);
					mc.getSoundManager().play(state.sound);
				}
			} else if (state.releaseAt < 0 || ticks - state.releaseAt > THRUST_TICKS) {
				endSound(state);
				it.remove();
			}
		}
	}

	private static void endSound(State state) {
		if (state.sound != null) {
			state.sound.end();
			state.sound = null;
		}
	}

	/** Forgets every entity (level change, disconnect); stops all charge loops. */
	static void clear() {
		for (State state : STATES.values()) {
			endSound(state);
		}

		STATES.clear();
	}

	private PushAnimations() {
	}
}
