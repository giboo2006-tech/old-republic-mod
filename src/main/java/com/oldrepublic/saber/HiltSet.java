package com.oldrepublic.saber;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.saber.HiltParts.Emitter;
import com.oldrepublic.saber.HiltParts.Grip;
import com.oldrepublic.saber.HiltParts.Pommel;
import com.oldrepublic.saber.SaberComponents.SaberConfig;

/**
 * The bosses' hilt sets (MILESTONE_7.md 7.1): which parts belong to which set and how many of them a
 * saber carries. A saber with two parts of a set has its two-piece bonus, with three both bonuses (7.2,
 * applied by {@code HiltSetEffects}). A saberstaff counts its emitter and its grip only.
 */
public enum HiltSet {
	/** Orren Vasch, the Collector: the Reliquary emitter and grip, the Archivist pommel. */
	COLLECTOR(Emitter.RELIQUARY, Grip.RELIQUARY, Pommel.ARCHIVIST),
	/** Torvin Kaast's trophies: the Watchman emitter, grip and pommel. */
	WATCHMAN(Emitter.WATCHMAN, Grip.WATCHMAN, Pommel.WATCHMAN),
	/** Darth Ulvaine's parts: the Penitent emitter, grip and pommel. */
	PENITENT(Emitter.PENITENT, Grip.PENITENT, Pommel.PENITENT),
	/** The Long Hymn's choir: the Choir emitter, grip and pommel. */
	CHOIR(Emitter.CHOIR, Grip.CHOIR, Pommel.CHOIR);

	private final Emitter emitter;
	private final Grip grip;
	private final Pommel pommel;

	HiltSet(Emitter emitter, Grip grip, Pommel pommel) {
		this.emitter = emitter;
		this.grip = grip;
		this.pommel = pommel;
	}

	/** {@code collector}, {@code watchman}, ... */
	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public Emitter emitter() {
		return emitter;
	}

	public Grip grip() {
		return grip;
	}

	public Pommel pommel() {
		return pommel;
	}

	/** The set {@code part} belongs to, or null for a craftable or legendary part. */
	public static @Nullable HiltSet of(HiltParts.Part part) {
		for (HiltSet set : values()) {
			if (part == set.emitter || part == set.grip || part == set.pommel) {
				return set;
			}
		}

		return null;
	}

	/** How many of {@code set}'s parts {@code config} carries; a staff ({@code staff}) counts its emitter and grip. */
	public static int count(SaberConfig config, HiltSet set, boolean staff) {
		int count = 0;

		if (config.emitter() == set.emitter) {
			count++;
		}

		if (config.grip() == set.grip) {
			count++;
		}

		if (!staff && config.pommel() == set.pommel) {
			count++;
		}

		return count;
	}

	/** The set with two or more parts on this hilt, or null. */
	public static @Nullable HiltSet active(SaberConfig config, boolean staff) {
		for (HiltSet set : values()) {
			if (count(config, set, staff) >= 2) {
				return set;
			}
		}

		return null;
	}
}
