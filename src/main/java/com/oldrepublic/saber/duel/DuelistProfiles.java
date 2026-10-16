package com.oldrepublic.saber.duel;

import java.util.Map;

/**
 * The named duelist profiles (spec section 9.6).
 *
 * <p>Phase 0 stub: fixed default values; the saber feature moves them into its balance section.
 */
public final class DuelistProfiles {
	private static final DuelistProfile ACOLYTE = new DuelistProfile("acolyte", 6f, 60f, 0.35f, 2, 3, 10, 7, 30, true, 0f);
	private static final Map<String, DuelistProfile> PROFILES = Map.of(
			"acolyte", ACOLYTE,
			"rival", new DuelistProfile("rival", 6f, 70f, 0.35f, 3, 3, 10, 7, 30, true, 0.15f),
			"jedi_master", new DuelistProfile("jedi_master", 5f, 120f, 0.5f, 2, 2, 12, 7, 30, false, 0.35f),
			"training", new DuelistProfile("training", 1f, Float.POSITIVE_INFINITY, 0f, 1, 1, 14, 50, 30, false, 0f));

	/** The profile with this id; unknown ids fall back to {@code acolyte}. */
	public static DuelistProfile get(String id) {
		return PROFILES.getOrDefault(id, ACOLYTE);
	}

	private DuelistProfiles() {
	}
}
