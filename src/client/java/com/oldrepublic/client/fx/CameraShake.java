package com.oldrepublic.client.fx;

import net.minecraft.util.RandomSource;

/**
 * Small, decaying camera shake, scaled by the player's screen-shake setting. Applied to the
 * camera rotation by {@code CameraMixin}.
 */
public final class CameraShake {
	private static final RandomSource RANDOM = RandomSource.create();
	private static float trauma;

	/** Adds shake. {@code intensity} 0 to 1; values stack and are capped at 1. */
	public static void add(float intensity) {
		trauma = Math.min(1f, trauma + intensity * Vfx.shakeScale());
	}

	public static void tick() {
		trauma = Math.max(0f, trauma - 0.08f);
	}

	/** Current yaw offset in degrees. */
	public static float yaw(float partialTick) {
		return offset(4f);
	}

	/** Current pitch offset in degrees. */
	public static float pitch(float partialTick) {
		return offset(3f);
	}

	private static float offset(float maxDegrees) {
		if (trauma <= 0f) {
			return 0f;
		}

		float t = trauma * trauma;
		return maxDegrees * t * (RANDOM.nextFloat() * 2f - 1f);
	}

	private CameraShake() {
	}
}
