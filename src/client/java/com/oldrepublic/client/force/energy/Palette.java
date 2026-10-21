package com.oldrepublic.client.force.energy;

import com.oldrepublic.client.fx.GlowRibbon;

import net.minecraft.util.Mth;

/**
 * The colours of one kind of light, as cross-sections for {@link GlowRibbon}: {@code glow} is the soft light around a line
 * (added to the frame), {@code core} its hot centre (alpha blended, so it reads against a bright sky), {@code flare} a point
 * of light seen face on. Radii are the full widths of a main line in blocks; thinner lines scale them down.
 */
public record Palette(GlowRibbon.Profile glow, float glowRadius, GlowRibbon.Profile core, float coreRadius, GlowRibbon.Profile flare) {
	/** Force Lightning: a white-hot core with a pale violet edge inside a blue-violet glow. */
	public static final Palette LIGHTNING = new Palette(
			profile(new float[] {0f, 0.08f, 0.26f, 0.58f, 1f},
					argb(0.66f, 0xD4DCFF), argb(0.5f, 0xA9B4FF), argb(0.22f, 0x7F7CFF), argb(0.075f, 0x6450F2), argb(0f, 0x5040D8)),
			0.2f,
			profile(new float[] {0f, 0.42f, 0.72f, 1f},
					argb(1f, 0xFFFFFF), argb(0.96f, 0xF2F1FF), argb(0.6f, 0xB4AEFF), argb(0f, 0x8C80FF)),
			0.026f,
			profile(new float[] {0f, 0.12f, 0.36f, 1f},
					argb(0.9f, 0xF2F2FF), argb(0.55f, 0xBEC4FF), argb(0.17f, 0x8A7EFF), argb(0f, 0x6050F0)));
	/** A spark of lightning still hot: near white with a pale blue glow. */
	public static final Palette SPARK_HOT = new Palette(
			profile(new float[] {0f, 0.3f, 1f}, argb(0.7f, 0xE4EAFF), argb(0.28f, 0x9AA6FF), argb(0f, 0x6C60F0)),
			0.07f,
			profile(new float[] {0f, 0.5f, 1f}, argb(1f, 0xFFFFFF), argb(0.9f, 0xEAF0FF), argb(0f, 0xA8B0FF)),
			0.011f,
			profile(new float[] {0f, 0.3f, 1f}, argb(0.7f, 0xE4EAFF), argb(0.28f, 0x9AA6FF), argb(0f, 0x6C60F0)));
	/** A spark of lightning cooling: violet. */
	public static final Palette SPARK_COOL = new Palette(
			profile(new float[] {0f, 0.3f, 1f}, argb(0.55f, 0xA49CFF), argb(0.2f, 0x7464F0), argb(0f, 0x5038C8)),
			0.06f,
			profile(new float[] {0f, 0.5f, 1f}, argb(1f, 0xD8D4FF), argb(0.8f, 0xA89CFF), argb(0f, 0x7060E0)),
			0.009f,
			profile(new float[] {0f, 0.3f, 1f}, argb(0.55f, 0xA49CFF), argb(0.2f, 0x7464F0), argb(0f, 0x5038C8)));

	/** A cross-section from colours given centre first. */
	static GlowRibbon.Profile profile(float[] at, int... argb) {
		return new GlowRibbon.Profile(at, argb);
	}

	/** {@code rgb} (0xRRGGBB) at {@code alpha} (0 to 1). */
	static int argb(float alpha, int rgb) {
		return Mth.clamp(Math.round(alpha * 255f), 0, 255) << 24 | rgb & 0xFFFFFF;
	}
}
