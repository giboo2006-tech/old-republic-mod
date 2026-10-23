package com.oldrepublic.client.fx;

import net.minecraft.util.Mth;

/**
 * Small, decaying camera shake, scaled by the player's screen-shake setting. Applied to the
 * camera rotation by {@code CameraMixin}. The offset follows smooth layered sine noise over
 * time (interpolated with the partial tick), so it reads as a jolt rather than per-frame jitter.
 */
public final class CameraShake {
	private static float trauma;
	private static float prevTrauma;
	private static int ticks;

	/** Adds shake. {@code intensity} 0 to 1; values stack and are capped at 1. */
	public static void add(float intensity) {
		trauma = Math.min(1f, trauma + intensity * Vfx.shakeScale());
	}

	public static void tick() {
		prevTrauma = trauma;
		trauma = Math.max(0f, trauma - 0.08f);
		ticks++;
	}

	/** Current yaw offset in degrees. */
	public static float yaw(float partialTick) {
		return offset(4f, partialTick, 0f);
	}

	/** Current pitch offset in degrees. */
	public static float pitch(float partialTick) {
		return offset(3f, partialTick, 17.3f);
	}

	private static float offset(float maxDegrees, float partialTick, float phase) {
		float level = Mth.lerp(partialTick, prevTrauma, trauma);

		if (level <= 0f) {
			return 0f;
		}

		float time = ticks + partialTick + phase;
		float noise = 0.6f * Mth.sin(time * 1.9f) + 0.4f * Mth.sin(time * 3.7f + 1.3f);
		return maxDegrees * level * level * noise;
	}

	private CameraShake() {
	}
}
