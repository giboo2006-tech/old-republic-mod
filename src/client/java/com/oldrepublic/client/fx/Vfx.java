package com.oldrepublic.client.fx;

import com.oldrepublic.client.config.ClientConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * Budget and culling rules every client effect goes through, so quality presets and the particle
 * cap apply consistently. Each call to {@link #count} or {@link #tellCount} spends from the
 * per-second budget, so an effect asks once and keeps the answer, never in a loop's condition:
 *
 * <pre>{@code
 * if (!Vfx.inRange(pos)) return;
 * for (int i = 0, n = Vfx.count(24); i < n; i++) { ... }
 * }</pre>
 */
public final class Vfx {
	/** Particles per second no effect may exceed, whatever the preset: the Cinematic budget. */
	private static final int ABSOLUTE_MAX_PARTICLES = ClientConfig.Preset.CINEMATIC.maxParticles;

	private static int spawnedThisSecond;
	private static long secondStart;

	/** Whether an effect at {@code pos} is close enough to the camera to be drawn. */
	public static boolean inRange(Vec3 pos) {
		Minecraft mc = Minecraft.getInstance();

		if (mc.gameRenderer == null || mc.gameRenderer.mainCamera() == null) {
			return false;
		}

		int distance = ClientConfig.get().preset.effectDistance;
		return mc.gameRenderer.mainCamera().position().distanceToSqr(pos) <= (double) distance * distance;
	}

	/**
	 * Scales a Standard-preset particle count by the current preset and the remaining budget.
	 * Always returns at least {@code min(1, base)} so tells and impacts stay visible.
	 */
	public static int count(int base) {
		ClientConfig.Preset preset = ClientConfig.get().preset;
		return budget(base, Math.round(base * preset.particleMultiplier), preset.maxParticles);
	}

	/**
	 * Particle count for an enemy attack tell (every event {@code DangerTellTable.isTell} lists):
	 * {@code base} as at Standard, never scaled by the preset, so no preset thins out a tell. It still
	 * counts toward the per-second budget, and only the absolute cap (the largest budget of any
	 * preset) limits it. Always at least {@code min(1, base)}.
	 */
	public static int tellCount(int base) {
		return budget(base, base, ABSOLUTE_MAX_PARTICLES);
	}

	/** Spends up to {@code wanted} particles from the per-second budget capped at {@code cap}. */
	private static int budget(int base, int wanted, int cap) {
		long now = System.currentTimeMillis();

		if (now - secondStart > 1000) {
			secondStart = now;
			spawnedThisSecond = 0;
		}

		int scaled = wanted;
		int remaining = cap - spawnedThisSecond;

		if (remaining < scaled) {
			scaled = Math.max(Math.min(1, base), remaining);
		}

		spawnedThisSecond += Math.max(0, scaled);
		return Math.max(Math.min(1, base), scaled);
	}

	public static boolean flashing() {
		return ClientConfig.get().flashingEffects;
	}

	public static float shakeScale() {
		return ClientConfig.get().screenShake;
	}

	private Vfx() {
	}
}
