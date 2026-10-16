package com.oldrepublic.identity;

/**
 * Named ranges of the -100 (dark) to +100 (light) alignment value: Dark up to -50, Leaning to the
 * Dark up to -15, Balanced from -14 to +14, Leaning to the Light from +15 and Light from +50.
 */
public enum AlignmentBand {
	DARK,
	LEANING_DARK,
	BALANCED,
	LEANING_LIGHT,
	LIGHT;

	private static final int STRONG = 50;
	private static final int LEANING = 15;

	public static AlignmentBand of(int alignment) {
		if (alignment >= STRONG) {
			return LIGHT;
		}

		if (alignment >= LEANING) {
			return LEANING_LIGHT;
		}

		if (alignment <= -STRONG) {
			return DARK;
		}

		if (alignment <= -LEANING) {
			return LEANING_DARK;
		}

		return BALANCED;
	}
}
