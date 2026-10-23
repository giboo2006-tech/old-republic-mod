package com.oldrepublic.client.force.kinetic;

/**
 * Smooth value noise in three dimensions (0 to 1), for the wavering patches of bent air drawn by the kinetic powers' shapes:
 * a hashed lattice, smoothly interpolated, so neighbouring points vary gently and the pattern drifts when one coordinate is
 * time.
 */
public final class KineticNoise {
	/** Noise at a point, 0 to 1. */
	public static float at(float x, float y, float z) {
		int x0 = floor(x);
		int y0 = floor(y);
		int z0 = floor(z);
		float fx = smooth(x - x0);
		float fy = smooth(y - y0);
		float fz = smooth(z - z0);
		float c000 = hash(x0, y0, z0);
		float c100 = hash(x0 + 1, y0, z0);
		float c010 = hash(x0, y0 + 1, z0);
		float c110 = hash(x0 + 1, y0 + 1, z0);
		float c001 = hash(x0, y0, z0 + 1);
		float c101 = hash(x0 + 1, y0, z0 + 1);
		float c011 = hash(x0, y0 + 1, z0 + 1);
		float c111 = hash(x0 + 1, y0 + 1, z0 + 1);
		float near = lerp(fy, lerp(fx, c000, c100), lerp(fx, c010, c110));
		float far = lerp(fy, lerp(fx, c001, c101), lerp(fx, c011, c111));
		return lerp(fz, near, far);
	}

	/** Two octaves of {@link #at}, the second at twice the frequency and half the weight; 0 to 1. */
	public static float layered(float x, float y, float z) {
		return (at(x, y, z) * 2f + at(x * 2.03f + 17.1f, y * 2.03f - 5.3f, z * 2.03f + 9.7f)) / 3f;
	}

	private static int floor(float v) {
		int i = (int) v;
		return v < i ? i - 1 : i;
	}

	private static float smooth(float t) {
		return t * t * (3f - 2f * t);
	}

	private static float lerp(float t, float a, float b) {
		return a + (b - a) * t;
	}

	/** A lattice point's value, 0 to 1. */
	private static float hash(int x, int y, int z) {
		int h = x * 374761393 + y * 668265263 + z * 1274126177;
		h = (h ^ h >>> 13) * 1103515245;
		h ^= h >>> 16;
		return (h & 0xFFFF) / 65535f;
	}

	private KineticNoise() {
	}
}
