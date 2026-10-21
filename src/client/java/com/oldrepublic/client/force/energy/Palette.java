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
			profile(new float[] {0f, 0.3f, 1f}, argb(0.75f, 0xE4EAFF), argb(0.3f, 0x9AA6FF), argb(0f, 0x6C60F0)),
			0.09f,
			profile(new float[] {0f, 0.5f, 1f}, argb(1f, 0xFFFFFF), argb(0.9f, 0xEAF0FF), argb(0f, 0xA8B0FF)),
			0.014f,
			profile(new float[] {0f, 0.3f, 1f}, argb(0.75f, 0xE4EAFF), argb(0.3f, 0x9AA6FF), argb(0f, 0x6C60F0)));
	/** A spark of lightning cooling: violet. */
	public static final Palette SPARK_COOL = new Palette(
			profile(new float[] {0f, 0.3f, 1f}, argb(0.6f, 0xA49CFF), argb(0.22f, 0x7464F0), argb(0f, 0x5038C8)),
			0.075f,
			profile(new float[] {0f, 0.5f, 1f}, argb(1f, 0xD8D4FF), argb(0.8f, 0xA89CFF), argb(0f, 0x7060E0)),
			0.011f,
			profile(new float[] {0f, 0.3f, 1f}, argb(0.6f, 0xA49CFF), argb(0.22f, 0x7464F0), argb(0f, 0x5038C8)));
	/** Life torn out by Force Drain: a hot crimson core in a deep red glow. */
	public static final Palette DRAIN = new Palette(
			profile(new float[] {0f, 0.14f, 0.4f, 1f}, argb(0.55f, 0xFF5050), argb(0.34f, 0xE01830), argb(0.1f, 0x90061C), argb(0f, 0x500010)),
			0.17f,
			profile(new float[] {0f, 0.45f, 1f}, argb(0.95f, 0xFF7A80), argb(0.88f, 0xC8102C), argb(0f, 0x600010)),
			0.022f,
			profile(new float[] {0f, 0.15f, 0.42f, 1f}, argb(0.8f, 0xFF8C8C), argb(0.5f, 0xF02838), argb(0.14f, 0x9A0A1E), argb(0f, 0x500010)));
	/** Warm light (Heal, Shared Light, Serene Ward, Valor): a near-white core in a gold glow. */
	public static final Palette WARM = new Palette(
			profile(new float[] {0f, 0.2f, 0.5f, 1f}, argb(0.5f, 0xFFEAB4), argb(0.3f, 0xFFC860), argb(0.09f, 0xE8902A), argb(0f, 0xB06010)),
			0.16f,
			profile(new float[] {0f, 0.5f, 1f}, argb(1f, 0xFFFFF4), argb(0.85f, 0xFFEFC0), argb(0f, 0xFFC860)),
			0.018f,
			profile(new float[] {0f, 0.15f, 0.45f, 1f}, argb(0.78f, 0xFFF6DC), argb(0.46f, 0xFFD890), argb(0.12f, 0xF0A040), argb(0f, 0xC07020)));
	/** An ember still hot (Rage): a yellow-white core in an orange glow. */
	public static final Palette EMBER_HOT = new Palette(
			profile(new float[] {0f, 0.3f, 1f}, argb(0.75f, 0xFFB050), argb(0.3f, 0xFF5A10), argb(0f, 0xA01800)),
			0.08f,
			profile(new float[] {0f, 0.5f, 1f}, argb(1f, 0xFFF0C0), argb(0.9f, 0xFFA040), argb(0f, 0xFF5010)),
			0.013f,
			profile(new float[] {0f, 0.15f, 0.45f, 1f}, argb(0.72f, 0xFFC890), argb(0.46f, 0xFF6A20), argb(0.14f, 0xC02008), argb(0f, 0x700800)));
	/** An ember cooling: a dull red. */
	public static final Palette EMBER_COOL = new Palette(
			profile(new float[] {0f, 0.3f, 1f}, argb(0.5f, 0xE04010), argb(0.2f, 0xA01808), argb(0f, 0x600800)),
			0.06f,
			profile(new float[] {0f, 0.5f, 1f}, argb(1f, 0xFF8A40), argb(0.8f, 0xD03010), argb(0f, 0x801000)),
			0.01f,
			profile(new float[] {0f, 0.3f, 1f}, argb(0.5f, 0xE04010), argb(0.2f, 0xA01808), argb(0f, 0x600800)));
	/** Fear's shadow: dark smoke, alpha blended, in a dim violet light that shows it at night. */
	public static final Palette SHADOW = new Palette(
			profile(new float[] {0f, 0.3f, 1f}, argb(0.26f, 0x7A3CB0), argb(0.1f, 0x45186E), argb(0f, 0x200838)),
			0.2f,
			profile(new float[] {0f, 0.45f, 1f}, argb(0.82f, 0x0A0510), argb(0.55f, 0x160A20), argb(0f, 0x1E1028)),
			0.05f,
			profile(new float[] {0f, 0.3f, 1f}, argb(0.3f, 0x8A4CC0), argb(0.12f, 0x45186E), argb(0f, 0x200838)));
	/** Mind Trick: a faint, pale, cool light. */
	public static final Palette PALE = new Palette(
			profile(new float[] {0f, 0.25f, 1f}, argb(0.42f, 0xEEF2FF), argb(0.18f, 0xB4C2FF), argb(0f, 0x8090E0)),
			0.12f,
			profile(new float[] {0f, 0.5f, 1f}, argb(0.85f, 0xFFFFFF), argb(0.55f, 0xE6ECFF), argb(0f, 0xC0CCFF)),
			0.01f,
			profile(new float[] {0f, 0.2f, 1f}, argb(0.62f, 0xF4F6FF), argb(0.26f, 0xC0CCFF), argb(0f, 0x8090E0)));

	/** A soft light of one colour ({@code rgb}, 0xRRGGBB): its glow of that colour, its core and its flare paler. */
	public static Palette light(int rgb) {
		int pale = mix(rgb, 0xFFFFFF, 0.5f);
		int white = mix(rgb, 0xFFFFFF, 0.85f);
		return new Palette(
				profile(new float[] {0f, 0.25f, 1f}, argb(0.5f, pale), argb(0.22f, rgb), argb(0f, rgb)),
				0.13f,
				profile(new float[] {0f, 0.5f, 1f}, argb(1f, white), argb(0.8f, pale), argb(0f, rgb)),
				0.013f,
				profile(new float[] {0f, 0.18f, 0.5f, 1f}, argb(0.75f, white), argb(0.42f, pale), argb(0.12f, rgb), argb(0f, rgb)));
	}

	/** A cross-section from colours given centre first. */
	static GlowRibbon.Profile profile(float[] at, int... argb) {
		return new GlowRibbon.Profile(at, argb);
	}

	/** {@code rgb} (0xRRGGBB) at {@code alpha} (0 to 1). */
	static int argb(float alpha, int rgb) {
		return Mth.clamp(Math.round(alpha * 255f), 0, 255) << 24 | rgb & 0xFFFFFF;
	}

	/** {@code a} blended {@code t} of the way to {@code b} (both 0xRRGGBB). */
	static int mix(int a, int b, float t) {
		int r = Math.round(Mth.lerp(t, (float) (a >> 16 & 255), (float) (b >> 16 & 255)));
		int g = Math.round(Mth.lerp(t, (float) (a >> 8 & 255), (float) (b >> 8 & 255)));
		int bl = Math.round(Mth.lerp(t, (float) (a & 255), (float) (b & 255)));
		return r << 16 | g << 8 | bl;
	}
}
