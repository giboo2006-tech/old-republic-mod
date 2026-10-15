package com.oldrepublic.client.fx;

import com.oldrepublic.client.config.ClientConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * Budget and culling rules every client effect goes through, so quality presets and the particle
 * cap apply consistently.
 *
 * <pre>{@code
 * if (!Vfx.inRange(pos)) return;
 * for (int i = 0; i < Vfx.count(24); i++) { ... }
 * }</pre>
 */
public final class Vfx {
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
		long now = System.currentTimeMillis();

		if (now - secondStart > 1000) {
			secondStart = now;
			spawnedThisSecond = 0;
		}

		int scaled = Math.round(base * preset.particleMultiplier);
		int remaining = preset.maxParticles - spawnedThisSecond;

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
