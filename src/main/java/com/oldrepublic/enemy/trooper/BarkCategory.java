package com.oldrepublic.enemy.trooper;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

/**
 * What a trooper's radio bark is about (spec 7.1, 11.2). A bark travels as the {@code trooper_bark}
 * effect with variant {@code ordinal() * 4 + line} (line 0 to 2), and its text is the lang key
 * {@code trooper.oldrepublic.bark.<armyKey>.<id()>.<line>}. Wire-encoded by ordinal: append only.
 */
public enum BarkCategory {
	/** A target spotted. */
	SPOT,
	/** Lines 0 and 1: the Wary warning ("That's close enough."). Line 2: the Neutral "Move along." */
	WARN,
	/** Line 0 is the leader's line when two patrols halt under the cold peace. */
	STANDOFF,
	SALUTE,
	HURT,
	/** The rifle vents. */
	RELOAD,
	/** Repositioning after its bolts were deflected. */
	FLANK,
	RALLY,
	GRENADE,
	REINFORCE,
	RETREAT,
	YIELD;

	/** Lines per category and army. */
	public static final int LINES = 3;

	/** Lower-case id used in the lang keys. */
	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** The {@code trooper_bark} variant for {@code line} of this category. */
	public int variant(int line) {
		return ordinal() * 4 + line;
	}

	/** The category of a {@code trooper_bark} variant, or null for an unknown one. */
	public static @Nullable BarkCategory byVariant(int variant) {
		BarkCategory[] values = values();
		int ordinal = variant / 4;
		return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
	}
}
