package com.oldrepublic.crafting.bench;

import java.util.Locale;

/**
 * The outcome of the last bench action, synced to the screen's status line through a menu data
 * slot (by ordinal; append only). {@link #good()} picks the line's colour.
 */
public enum BenchResult {
	NONE(true),
	STARTED(true),
	CRAFTED(true),
	MASTERWORK(true),
	CANCELLED_RANGE(false),
	CANCELLED(false),
	MISSING_INPUTS(false),
	LOCKED(false),
	BUSY(false),
	FITTED(true),
	REMOVED(true),
	NO_SOCKET(false),
	SALVAGED(true),
	NOT_SALVAGEABLE(false),
	CHOSEN(true),
	SECOND_LOCKED(false),
	FULL(false),
	NO_CREDITS(false),
	COMMISSIONED(true),
	EXCHANGED(true),
	NOTHING_TO_EXCHANGE(false),
	/** A legendary schematic's craft (MILESTONE_5.md 11.1). */
	LEGENDARY(true),
	/** Salvage refused: a Legendary item is too rare to break down (11.1). */
	TOO_RARE(false);

	private final boolean good;

	BenchResult(boolean good) {
		this.good = good;
	}

	public boolean good() {
		return good;
	}

	public String translationKey() {
		return "screen.oldrepublic.bench.result." + name().toLowerCase(Locale.ROOT);
	}

	public static BenchResult byOrdinal(int ordinal) {
		BenchResult[] values = values();
		return ordinal >= 0 && ordinal < values.length ? values[ordinal] : NONE;
	}
}
