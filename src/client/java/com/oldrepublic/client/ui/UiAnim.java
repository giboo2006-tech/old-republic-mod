package com.oldrepublic.client.ui;

import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.client.quest.QuestClient;

import net.minecraft.util.Mth;

/**
 * Small animation helpers for the HUD and screens, all driven by client ticks plus the partial
 * tick so motion is smooth at any frame rate. Anything that pulses or flickers respects the
 * player's flashing setting: with flashing effects off it holds a steady value instead.
 */
public final class UiAnim {
	/** Client ticks plus the partial tick: the clock every UI animation runs on. */
	public static float time(float partialTick) {
		return QuestClient.ticks() + partialTick;
	}

	public static float easeOutCubic(float t) {
		float u = 1f - Mth.clamp(t, 0f, 1f);
		return 1f - u * u * u;
	}

	/**
	 * Frame-rate independent exponential approach of {@code current} toward {@code target}.
	 *
	 * @param rate fraction of the remaining distance covered per tick (0 to 1)
	 * @param dt   elapsed ticks since the last call
	 */
	public static float approach(float current, float target, float rate, float dt) {
		float keep = (float) Math.pow(1f - Mth.clamp(rate, 0f, 0.999f), Math.max(0f, dt));
		return target + (current - target) * keep;
	}

	/** 0 to 1 sine pulse with the given period; a steady 1 when flashing effects are off. */
	public static float pulse(float time, float periodTicks) {
		if (!Vfx.flashing()) {
			return 1f;
		}

		return 0.5f + 0.5f * Mth.sin(time / periodTicks * (float) Math.PI * 2f);
	}

	/**
	 * A faint holo-projector flicker, 0.9 to 1 (never a strobe); exactly 1 when flashing effects
	 * are off.
	 */
	public static float flicker(float time) {
		if (!Vfx.flashing()) {
			return 1f;
		}

		float slow = Mth.sin(time * 0.37f) * Mth.sin(time * 0.113f + 1.3f);
		return 0.95f + 0.05f * slow;
	}

	/**
	 * Fade envelope of a one-shot moment: 0 before {@code start}, rises over {@code in} ticks, holds,
	 * falls over {@code out} ticks and is 0 after {@code start + length}.
	 */
	public static float envelope(float now, int start, int length, int in, int out) {
		if (start == Integer.MIN_VALUE) {
			return 0f;
		}

		float t = now - start;

		if (t < 0 || t > length) {
			return 0f;
		}

		return Math.min(Mth.clamp(t / in, 0f, 1f), Mth.clamp((length - t) / out, 0f, 1f));
	}

	private UiAnim() {
	}
}
